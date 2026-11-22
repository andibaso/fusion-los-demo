package sg.andi.fusionlos.projection;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.CheckResult;
import sg.andi.fusionlos.domain.LoanApplication;
import sg.andi.fusionlos.domain.Status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** The underwriter work queue, read straight from MongoDB. */
@Component
public class QueueProjection {

    public record QueueRow(
            String id,
            String applicant,
            double amount,
            Integer score,
            Status status,
            String assignedTo,
            Instant waitingSince,
            Instant reminderAt,
            Instant escalateAt,
            boolean escalated,
            boolean fourEyes,
            int approvals,
            List<String> reviewReasons) {}

    private static final List<Status> UNDERWRITING_STATUSES =
            Arrays.stream(Status.values()).filter(Status::isUnderwriting).toList();

    private final MongoTemplate mongo;

    public QueueProjection(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<QueueRow> forUser(String user) {
        Query query = Query.query(Criteria.where("status").in(UNDERWRITING_STATUSES))
                .with(Sort.by(Sort.Direction.ASC, "underwriting.assignedAt"));
        if (user != null && !user.isBlank()) {
            query.addCriteria(Criteria.where("underwriting.assignedTo").is(user));
        }
        return mongo.find(query, LoanApplication.class).stream().map(QueueProjection::toRow).toList();
    }

    private static QueueRow toRow(LoanApplication app) {
        CheckResult bureau = app.checks() == null ? null : app.checks().get("bureau");
        LoanApplication.Underwriting uw = app.underwriting();
        return new QueueRow(
                app.id(),
                app.applicant().name(),
                app.amount(),
                bureau == null ? null : bureau.score(),
                app.status(),
                uw.assignedTo(),
                uw.assignedAt(),
                uw.reminderAt(),
                uw.escalateAt(),
                uw.escalated(),
                uw.fourEyesRequired(),
                uw.approvals() == null ? 0 : uw.approvals().size(),
                uw.reviewReasons() == null ? List.of() : uw.reviewReasons());
    }
}
