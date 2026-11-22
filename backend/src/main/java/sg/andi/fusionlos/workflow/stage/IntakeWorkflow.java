package sg.andi.fusionlos.workflow.stage;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import sg.andi.fusionlos.workflow.ApplicationInput;
import sg.andi.fusionlos.workflow.StageOutcome;

@WorkflowInterface
public interface IntakeWorkflow {
    @WorkflowMethod
    StageOutcome run(ApplicationInput input);
}
