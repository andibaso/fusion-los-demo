package sg.andi.fusionlos.workflow.stage;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import sg.andi.fusionlos.workflow.OfferResponse;
import sg.andi.fusionlos.workflow.StageOutcome;

@WorkflowInterface
public interface OfferWorkflow {
    @WorkflowMethod
    StageOutcome run(String applicationId);

    @SignalMethod
    void response(OfferResponse response);
}
