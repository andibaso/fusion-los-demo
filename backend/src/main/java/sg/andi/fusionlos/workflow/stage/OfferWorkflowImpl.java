package sg.andi.fusionlos.workflow.stage;

import io.temporal.workflow.Workflow;
import sg.andi.fusionlos.activity.ConfigActivities;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.activity.NotificationActivities;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.workflow.ActivityStubs;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.StageStatus;

import java.time.Instant;

import static sg.andi.fusionlos.domain.OfferChoice.ACCEPT;
import static sg.andi.fusionlos.domain.Stage.OFFER;
import static sg.andi.fusionlos.domain.Status.OFFER_DECLINED;
import static sg.andi.fusionlos.domain.Status.OFFER_EXPIRED;
import static sg.andi.fusionlos.domain.Status.OFFER_SENT;

/** Sends the offer and waits for the applicant, up to offer.expiresAfter (7 days in prod). */
public class OfferWorkflowImpl implements OfferWorkflow {

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);
    private final ConfigActivities config = ActivityStubs.internal(ConfigActivities.class);

    private OfferResponse response;

    @Override
    public StageOutcome run(String applicationId) {
        DemoClock clock = config.durations(applicationId);
        NotificationActivities notifications = ActivityStubs.external(NotificationActivities.class, clock);

        Instant expiresAt = Instant.ofEpochMilli(Workflow.currentTimeMillis()).plus(clock.offerExpiresAfter());
        loanData.prepareOffer(applicationId, expiresAt);
        StageStatus.set(loanData, applicationId, OFFER, OFFER_SENT);
        notifications.notifyApplicant(applicationId, "OFFER_SENT");

        boolean answered = Workflow.await(clock.offerExpiresAfter(), () -> response != null);
        if (!answered) {
            StageStatus.set(loanData, applicationId, OFFER, OFFER_EXPIRED);
            notifications.notifyApplicant(applicationId, "OFFER_EXPIRED");
            return StageOutcome.done(OFFER_EXPIRED);
        }

        loanData.recordOfferResponse(applicationId, response);
        if (response.choice() == ACCEPT) {
            return StageOutcome.next();
        }
        StageStatus.set(loanData, applicationId, OFFER, OFFER_DECLINED);
        return StageOutcome.done(OFFER_DECLINED);
    }

    @Override
    public void response(OfferResponse incoming) {
        if (response == null) {
            response = incoming;
        }
    }
}
