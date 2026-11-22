package sg.andi.fusionlos.workflow.stage;

import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.workflow.ActivityStubs;
import sg.andi.fusionlos.workflow.ApplicationInput;
import sg.andi.fusionlos.workflow.StageOutcome;
import sg.andi.fusionlos.workflow.StageStatus;

import java.util.List;

import static sg.andi.fusionlos.domain.Stage.INTAKE;
import static sg.andi.fusionlos.domain.Status.INTAKE_RECEIVED;
import static sg.andi.fusionlos.domain.Status.REJECTED;

/** Trivial by design. It exists so every stage follows the same pattern. */
public class IntakeWorkflowImpl implements IntakeWorkflow {

    private final LoanDataActivities loanData = ActivityStubs.internal(LoanDataActivities.class);

    @Override
    public StageOutcome run(ApplicationInput input) {
        String applicationId = input.applicationId();
        List<String> problems = loanData.validate(input);
        loanData.createApplication(input);

        if (!problems.isEmpty()) {
            StageStatus.set(loanData, applicationId, INTAKE, REJECTED);
            return StageOutcome.done(REJECTED);
        }
        StageStatus.set(loanData, applicationId, INTAKE, INTAKE_RECEIVED);
        return StageOutcome.next();
    }
}
