package sg.andi.fusionlos.workflow;

import io.temporal.workflow.Workflow;
import sg.andi.fusionlos.activity.LoanDataActivities;
import sg.andi.fusionlos.domain.Stage;
import sg.andi.fusionlos.domain.Status;

import static sg.andi.fusionlos.workflow.LosSearchAttributes.STATUS;

/**
 * Status changes go to MongoDB through an activity, and to the Status search attribute
 * so the Temporal UI can filter on it, for example `Status`="UNDERWRITING_ESCALATED" (backticks: Status is reserved).
 */
public final class StageStatus {

    private StageStatus() {}

    public static void set(LoanDataActivities loanData, String applicationId, Stage stage, Status status) {
        loanData.setStatus(applicationId, stage, status);
        Workflow.upsertTypedSearchAttributes(STATUS.valueSet(status.name()));
    }
}
