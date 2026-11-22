package sg.andi.fusionlos.api;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Year;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Issues IDs like APP-2026-000123 from a counter document. */
@Component
public class ApplicationIdGenerator {

    record Counter(String id, long seq) {}

    private final MongoTemplate mongo;

    public ApplicationIdGenerator(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public String next() {
        Counter counter = mongo.findAndModify(
                Query.query(where("_id").is("application")),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Counter.class, "counters");
        return "APP-%d-%06d".formatted(Year.now().getValue(), counter.seq());
    }
}
