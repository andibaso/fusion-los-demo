package sg.andi.fusionlos.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One workflow per loan application. Workflow ID = application ID.
 * The UI talks only to this workflow; it forwards signals to the stage that is running.
 */
@WorkflowInterface
public interface LoanApplicationWorkflow {

    @WorkflowMethod
    ApplicationOutcome run(ApplicationInput input);

    @SignalMethod
    void underwriterDecision(UnderwriterDecision decision);

    @SignalMethod
    void documentUploaded(DocumentUploaded document);

    @SignalMethod
    void offerResponse(OfferResponse response);

    /** Applicant withdraws. Ignored once disbursement has started: money may already be moving. */
    @SignalMethod
    void cancel(String reason);

    @QueryMethod
    ApplicationState state();
}
