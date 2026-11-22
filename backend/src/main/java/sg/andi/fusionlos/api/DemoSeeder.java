package sg.andi.fusionlos.api;

import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.api.ApiModels.ApplicantRequest;
import sg.andi.fusionlos.api.ApiModels.CreateApplicationRequest;
import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.LoanApplicationRepository;
import sg.andi.fusionlos.domain.OfferChoice;
import sg.andi.fusionlos.domain.Status;
import sg.andi.fusionlos.mock.BureauService;
import sg.andi.fusionlos.workflow.LoanApplicationWorkflow;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Starts real workflows and drives them with real signals until they sit in the stage they are meant
 * to show. Nothing is written to MongoDB directly. Runs in the background; the queue fills within a minute.
 */
@Component
@Profile("api")
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private static final String PROD = "prod-durations";  // long timers: stays where it lands
    private static final String DEMO = "demo-durations";  // short timers: escalates in 45 seconds

    enum Target { VERIFICATION, UNDERWRITING, ESCALATED, OFFER, COMPLETED, REJECTED_BY_UNDERWRITER, REJECTED_BY_SANCTIONS }

    record Seed(String name, double amount, Target target) {}

    /** 30 applications spread across the stages. Amounts above 50,000 need 4 eyes, above 100,000 a valuation. */
    private static final List<Seed> SEEDS = List.of(
            new Seed("Aisha Rahman", 18_000, Target.VERIFICATION),
            new Seed("Brandon Lim", 62_000, Target.VERIFICATION),
            new Seed("Chloe Tan", 25_000, Target.VERIFICATION),
            new Seed("Darius Ng", 140_000, Target.VERIFICATION),
            new Seed("Elena Goh", 33_000, Target.VERIFICATION),
            new Seed("Farid Ismail", 12_000, Target.VERIFICATION),
            new Seed("Grace Chua", 45_000, Target.UNDERWRITING),
            new Seed("Hafiz Osman", 75_000, Target.UNDERWRITING),
            new Seed("Irene Koh", 22_000, Target.UNDERWRITING),
            new Seed("Jason Teo", 180_000, Target.UNDERWRITING),
            new Seed("Kavitha Nair", 38_000, Target.UNDERWRITING),
            new Seed("Leon Wong", 55_000, Target.UNDERWRITING),
            new Seed("Mei Ling Ho", 15_000, Target.UNDERWRITING),
            new Seed("Nikhil Rao", 95_000, Target.UNDERWRITING),
            new Seed("Olivia Seah", 28_000, Target.UNDERWRITING),
            new Seed("Pravin Kumar", 250_000, Target.UNDERWRITING),
            new Seed("Qi Hui Lee", 41_000, Target.ESCALATED),
            new Seed("Rizwan Ali", 68_000, Target.ESCALATED),
            new Seed("Sarah Yeo", 30_000, Target.ESCALATED),
            new Seed("Timothy Png", 120_000, Target.ESCALATED),
            new Seed("Umairah Zain", 20_000, Target.OFFER),
            new Seed("Victor Chan", 47_000, Target.OFFER),
            new Seed("Wei Jie Soh", 85_000, Target.OFFER),
            new Seed("Xin Yi Lau", 35_000, Target.OFFER),
            new Seed("Yusuf Hamid", 10_000, Target.COMPLETED),
            new Seed("Zoe Ong", 26_000, Target.COMPLETED),
            new Seed("Adrian Sim", 49_000, Target.COMPLETED),
            new Seed("Benjamin Toh", 52_000, Target.REJECTED_BY_UNDERWRITER),
            new Seed("Cheryl Quek", 16_000, Target.REJECTED_BY_UNDERWRITER),
            new Seed("Ivan Blocked", 40_000, Target.REJECTED_BY_SANCTIONS));

    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(3);

    private final ApplicationStarter starter;
    private final WorkflowClient temporal;
    private final LoanApplicationRepository applications;
    private final ExecutorService drivers = Executors.newVirtualThreadPerTaskExecutor();

    public DemoSeeder(ApplicationStarter starter, WorkflowClient temporal, LoanApplicationRepository applications) {
        this.starter = starter;
        this.temporal = temporal;
        this.applications = applications;
    }

    /** Starts the first {@code count} seeds and returns their IDs. Driving them on happens in the background. */
    public List<String> seed(int count) {
        List<String> ids = new ArrayList<>();
        for (Seed seed : SEEDS.subList(0, Math.min(count, SEEDS.size()))) {
            String id = starter.start(request(seed), durationsFor(seed.target()), null).applicationId();
            ids.add(id);
            drivers.submit(() -> drive(id, seed.target()));
        }
        return ids;
    }

    private void drive(String id, Target target) {
        try {
            switch (target) {
                case VERIFICATION, UNDERWRITING, ESCALATED, REJECTED_BY_SANCTIONS -> {
                    // The workflow gets there on its own: bureau failing, waiting for a person, or a timer.
                }
                case OFFER -> approve(id);
                case COMPLETED -> {
                    approve(id);
                    await(id, s -> s == Status.OFFER_SENT);
                    workflow(id).offerResponse(new OfferResponse(OfferChoice.ACCEPT, "applicant.ben"));
                }
                case REJECTED_BY_UNDERWRITER -> {
                    await(id, s -> s == Status.UNDERWRITING_PENDING);
                    decide(id, Decision.REJECT, "uw.alice", "Debt to income too high");
                }
            }
        } catch (RuntimeException e) {
            log.warn("Seeding {} towards {} stopped: {}", id, target, e.getMessage());
        }
    }

    /** One approval, or two from different underwriters when 4 eyes applies. */
    private void approve(String id) {
        await(id, s -> s == Status.UNDERWRITING_PENDING);
        decide(id, Decision.APPROVE, "uw.alice", "Within policy");
        Status next = await(id, s -> s == Status.UNDERWRITING_AWAITING_SECOND_APPROVAL || s == Status.OFFER_SENT);
        if (next == Status.UNDERWRITING_AWAITING_SECOND_APPROVAL) {
            decide(id, Decision.APPROVE, "uw.carol", "Agreed");
        }
    }

    private void decide(String id, Decision decision, String actor, String note) {
        workflow(id).underwriterDecision(new UnderwriterDecision(decision, actor, note, null));
    }

    /** Polls the MongoDB projection, the same way the UI does. */
    private Status await(String id, Predicate<Status> reached) {
        Instant deadline = Instant.now().plus(STEP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Status status = applications.find(id).map(app -> app.status()).orElse(null);
            if (status != null && reached.test(status)) {
                return status;
            }
            sleep();
        }
        throw new IllegalStateException("timed out waiting for " + id);
    }

    private LoanApplicationWorkflow workflow(String id) {
        return temporal.newWorkflowStub(LoanApplicationWorkflow.class, id);
    }

    private static String durationsFor(Target target) {
        return target == Target.ESCALATED || target == Target.COMPLETED ? DEMO : PROD;
    }

    private static CreateApplicationRequest request(Seed seed) {
        String handle = seed.name().toLowerCase().replace(' ', '.');
        String email = seed.target() == Target.VERIFICATION
                ? handle + BureauService.ALWAYS_FAILING_DOMAIN
                : handle + "@example.com";
        double income = Math.max(4_000, Math.round(seed.amount() / 12 / 100) * 100);
        int tenor = seed.amount() > 100_000 ? 60 : 36;
        return new CreateApplicationRequest(null, new ApplicantRequest(seed.name(), email, income),
                "PERSONAL_LOAN", seed.amount(), tenor);
    }

    private static void sleep() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }
}
