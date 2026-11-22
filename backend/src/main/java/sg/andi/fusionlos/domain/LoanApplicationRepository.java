package sg.andi.fusionlos.domain;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Read access to application documents. Writes live in LoanDataActivitiesImpl. */
@Repository
public class LoanApplicationRepository {

    private final MongoTemplate mongo;

    public LoanApplicationRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Optional<LoanApplication> find(String id) {
        return Optional.ofNullable(mongo.findById(id, LoanApplication.class));
    }

    public LoanApplication get(String id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Application not found: " + id));
    }

    public List<LoanApplication> list(Stage stage, Status status, String assignedTo) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(200);
        if (stage != null) query.addCriteria(Criteria.where("stage").is(stage));
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        if (assignedTo != null) query.addCriteria(Criteria.where("underwriting.assignedTo").is(assignedTo));
        return mongo.find(query, LoanApplication.class);
    }
}
