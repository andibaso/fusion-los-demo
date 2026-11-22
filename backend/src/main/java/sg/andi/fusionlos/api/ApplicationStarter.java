package sg.andi.fusionlos.api;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.SearchAttributes;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import sg.andi.fusionlos.api.ApiModels.ApplicantRequest;
import sg.andi.fusionlos.api.ApiModels.CreateApplicationRequest;
import sg.andi.fusionlos.api.ApiModels.CreateApplicationResponse;
import sg.andi.fusionlos.domain.ApplicationRules;
import sg.andi.fusionlos.worker.TaskQueues;
import sg.andi.fusionlos.workflow.ApplicationInput;
import sg.andi.fusionlos.workflow.LoanApplicationWorkflow;
import sg.andi.fusionlos.workflow.LosSearchAttributes;

import java.util.List;

/** Starts one workflow per application. The application ID is the workflow ID, so a second start is rejected. */
@Component
@Profile("api")
public class ApplicationStarter {

    private final WorkflowClient temporal;
    private final ApplicationIdGenerator ids;

    public ApplicationStarter(WorkflowClient temporal, ApplicationIdGenerator ids) {
        this.temporal = temporal;
        this.ids = ids;
    }

    public CreateApplicationResponse start(CreateApplicationRequest req, String durationsProfile, String submittedBy) {
        ApplicantRequest applicant = req.applicant();
        List<String> problems = ApplicationRules.validate(
                applicant.name(), applicant.email(), applicant.monthlyIncome(), req.amount(), req.tenorMonths());
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
        }

        String applicationId = req.applicationId() != null ? req.applicationId() : ids.next();
        ApplicationInput input = new ApplicationInput(applicationId, customerId(applicant.email()),
                applicant.name(), applicant.email(), applicant.monthlyIncome(),
                req.product() != null ? req.product() : "PERSONAL_LOAN", req.amount(), req.tenorMonths(),
                durationsProfile, submittedBy);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(applicationId)
                .setTaskQueue(TaskQueues.MAIN)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setTypedSearchAttributes(SearchAttributes.newBuilder()
                        .set(LosSearchAttributes.AMOUNT, req.amount())
                        .build())
                .build();
        LoanApplicationWorkflow workflow = temporal.newWorkflowStub(LoanApplicationWorkflow.class, options);

        try {
            WorkflowClient.start(workflow::run, input);
            return new CreateApplicationResponse(applicationId, true);
        } catch (WorkflowExecutionAlreadyStarted duplicate) {
            return new CreateApplicationResponse(applicationId, false);
        }
    }

    private static String customerId(String email) {
        return "CUST-%06d".formatted(Math.floorMod(email.trim().toLowerCase().hashCode(), 1_000_000));
    }
}
