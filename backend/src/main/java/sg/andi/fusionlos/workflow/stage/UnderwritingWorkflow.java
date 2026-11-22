package sg.andi.fusionlos.workflow.stage;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import sg.andi.fusionlos.workflow.DocumentUploaded;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.UnderwriterDecision;

@WorkflowInterface
public interface UnderwritingWorkflow {
    @WorkflowMethod
    StageOutcome run(String applicationId);

    @SignalMethod
    void decision(UnderwriterDecision decision);

    @SignalMethod
    void documentUploaded(DocumentUploaded document);
}
