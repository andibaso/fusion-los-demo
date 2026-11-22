import { matchPath } from 'react-router-dom'

/**
 * How it works, one entry per screen. Business first: what the bank gets, then why Temporal
 * makes it cheap, then the flow and the few lines of workflow code behind it.
 */
export type Screen = {
  title: string
  objective: string
  outcomes: { label: string; value: string }[]
  /** Old way versus this way, in plain words. */
  whyTemporal: { before: string; after: string }[]
  flow: { title: string; detail: string }[]
  code?: { file: string; text: string }
  live?: string
}

const OVERVIEW: Screen = {
  title: 'Overview',
  objective:
    'Personal loan origination from application to payout. Each application runs as one Temporal workflow that owns its status, retries, waits and audit history.',
  outcomes: [
    { label: 'Workflows per application', value: '1' },
    { label: 'Cron jobs', value: '0' },
    { label: 'Retry code in the app', value: '0 lines' },
    { label: 'Audit trail', value: 'event history' },
  ],
  whyTemporal: [
    {
      before: 'Status column plus cron jobs that sweep for stuck rows.',
      after: 'One workflow per application owns the status from start to end.',
    },
    {
      before: 'Retry tables and reconciliation scripts around every vendor call.',
      after: 'A retry policy on the call. Temporal retries until it works or the deadline passes.',
    },
    {
      before: 'A scheduler for reminders, escalations and offer expiry.',
      after: 'Timers inside the workflow, durable across restarts and deploys.',
    },
    {
      before: 'Audit reconstructed from logs after the fact.',
      after: 'The event history is the audit trail, recorded as it happens.',
    },
  ],
  flow: [
    { title: 'Intake', detail: 'Validate and store the application. The application ID is the workflow ID.' },
    { title: 'Verification', detail: 'KYC, credit bureau and sanctions in parallel. Valuation above 100,000.' },
    { title: 'Underwriting', detail: 'Wait for a human with reminder and escalation timers. 4 eyes above 50,000.' },
    { title: 'Offer', detail: 'Send the offer and wait for the applicant, with an expiry.' },
    { title: 'Disbursement', detail: 'Reserve funds, open the loan account, pay out.' },
  ],
  code: {
    file: 'LoanApplicationWorkflowImpl.java',
    text: `StageOutcome outcome = runStage(INTAKE, IntakeWorkflow.class, i -> i.run(input));
if (outcome.proceed()) outcome = runStage(VERIFICATION, ...);
if (outcome.proceed()) outcome = runStage(UNDERWRITING, ...);
if (outcome.proceed()) outcome = runStage(OFFER, ...);
if (outcome.proceed()) outcome = runStage(DISBURSEMENT, ...);`,
  },
  live: 'Temporal owns the process and its failures. MongoDB owns the loan file and the audit projection. The UI sends signals and reads MongoDB.',
}

const APPLY: Screen = {
  title: 'Apply',
  objective:
    'Submit starts one workflow whose ID is the application ID. A second submit with the same ID is rejected, and a crash after submit does not drop the application.',
  outcomes: [
    { label: 'Duplicate applications', value: 'rejected by ID' },
    { label: 'Workflow ID', value: 'application ID' },
    { label: 'State held in Temporal', value: 'IDs only' },
    { label: 'Loan file', value: 'MongoDB' },
  ],
  whyTemporal: [
    {
      before: 'Insert a row and publish a message as two steps that can fail separately.',
      after: 'Start one workflow. Temporal persists its state, so it continues after a worker crash.',
    },
    {
      before: 'Dedup tables to stop double submits.',
      after: 'Workflow ID equals application ID, and a second start with the same ID is rejected.',
    },
  ],
  flow: [
    { title: 'Submit', detail: 'The API generates APP-YYYY-NNNNNN and starts the workflow with that ID.' },
    { title: 'Intake', detail: 'Activities validate and write the document to MongoDB. Status is written by the workflow only.' },
    { title: 'Verification starts', detail: 'The next stage starts as a child workflow {id}-verification.' },
  ],
  code: {
    file: 'ApplicationController.java',
    text: `WorkflowOptions.newBuilder()
    .setWorkflowId(applicationId)
    .setTaskQueue(TaskQueues.MAIN)
    .setWorkflowIdReusePolicy(REJECT_DUPLICATE)
    .build();`,
  },
  live: 'Recent applications refresh every 5 seconds from MongoDB. List screens never query Temporal.',
}

const MY_APPLICATION: Screen = {
  title: 'My application',
  objective:
    'Shows the applicant the current stage, any document or answer needed from them, and when the offer expires.',
  outcomes: [
    { label: 'Status source', value: 'workflow' },
    { label: 'Offer window', value: '60 seconds short, 7 days production' },
    { label: 'Upload or accept', value: 'a signal' },
    { label: 'Worker down', value: 'page still works' },
  ],
  whyTemporal: [
    {
      before: 'An expiry job that scans offers every night.',
      after: 'The workflow waits on the answer with a timeout. No job, no table.',
    },
    {
      before: 'Upload endpoint updates a status column and publishes to a queue.',
      after: 'Upload sends a signal. The waiting workflow wakes and carries on.',
    },
    {
      before: 'Status written by several services, so screens can disagree.',
      after: 'Status is written only by workflow activities, so all screens read the same value.',
    },
  ],
  flow: [
    { title: 'Read', detail: 'The page reads the MongoDB projection and a live query of the workflow.' },
    { title: 'Act', detail: 'Upload a document or accept the offer. Each is a signal to the application ID.' },
    { title: 'Wait', detail: 'If nobody answers, the offer expires on time and the applicant is notified.' },
  ],
  code: {
    file: 'OfferWorkflowImpl.java',
    text: `boolean answered = Workflow.await(
    clock.offerExpiresAfter(), () -> response != null);
if (!answered) {
    loanData.setStatus(applicationId, OFFER, OFFER_EXPIRED);
    notifications.notifyApplicant(applicationId, "OFFER_EXPIRED");
}`,
  },
  live: 'Status, offer countdown and timeline refresh every 2 seconds. Stop the worker and the page says the application is safe.',
}

const QUEUE: Screen = {
  title: 'Underwriting queue',
  objective:
    'Each case has a reminder and an escalation deadline. When the reminder timer fires the assignee is notified. When the escalation timer fires the case moves to the senior underwriter.',
  outcomes: [
    { label: 'Reminder', value: '20 seconds short, 2 days production' },
    { label: 'Escalation', value: '45 seconds short, 5 days production' },
    { label: 'Escalation jobs', value: '0' },
    { label: 'Threads held while waiting', value: '0' },
  ],
  whyTemporal: [
    {
      before: 'A cron job every 15 minutes looking for overdue cases.',
      after: 'Two timers per case, started with the case and cancelled by the decision.',
    },
    {
      before: 'Timers lost when the scheduler restarts.',
      after: 'Timers live on the Temporal server and survive worker restarts and deploys.',
    },
  ],
  flow: [
    { title: 'Case opens', detail: 'Assigned to Alice. Reminder and escalation timers start.' },
    { title: 'Reminder fires', detail: 'Alice is notified. The case stays hers.' },
    { title: 'Escalation fires', detail: 'Case moves to Carol, the senior underwriter.' },
    { title: 'Decision', detail: 'Approve, reject or ask for info. Pending timers are cancelled.' },
  ],
  code: {
    file: 'UnderwritingWorkflowImpl.java',
    text: `CancellationScope slaTimers = Workflow.newCancellationScope(() -> {
    Async.procedure(this::remindAfter, clock.reminderAfter());
    Async.procedure(this::escalateAfter, clock.escalateAfter());
});
slaTimers.run();

StageOutcome outcome = awaitDecision(fourEyes);
slaTimers.cancel();`,
  },
  live: 'Countdowns tick in the browser. Rows come from MongoDB, which the workflow updates when a timer fires.',
}

const CASE: Screen = {
  title: 'Case detail',
  objective:
    'Approve, reject or request a document. Loans above 50,000 need two different approvers. A requested document re-runs only the check it affects.',
  outcomes: [
    { label: '4 eyes', value: 'above 50,000' },
    { label: 'Info requests', value: 'max 3' },
    { label: 'Checks re-run', value: 'only the affected one' },
    { label: 'Audit', value: 'event history' },
  ],
  whyTemporal: [
    {
      before: 'Approve endpoint updates several tables directly.',
      after: 'Approve sends a signal. The workflow decides the next step.',
    },
    {
      before: 'Second approver rules spread across UI and stored procedures.',
      after: 'One short method in the workflow, unit tested.',
    },
    {
      before: 'Retry attempts visible only in application logs.',
      after: 'Attempt count and last failure come from the workflow history and show in the timeline.',
    },
  ],
  flow: [
    { title: 'Approve', detail: 'Below 50,000 one approval moves to the offer. Above it a second, different underwriter must approve.' },
    { title: 'Request info', detail: 'The workflow waits for the upload, then re-runs only bureau (payslip) or KYC (ID).' },
    { title: 'Reject', detail: 'Recorded, the applicant is notified, the workflow ends.' },
  ],
  code: {
    file: 'UnderwritingWorkflowImpl.java',
    text: `if (firstApprover == null) {
    firstApprover = approval.actor();
    setStatus(UNDERWRITING_AWAITING_SECOND_APPROVAL);
    return false;
}
if (firstApprover.equals(approval.actor())) {
    // same person twice: ignored, and on record
    return false;
}
return true;`,
  },
  live: 'The workflow timeline is built from the Temporal event history of the parent and its five stages, with retries from the pending activity.',
}

const CHAOS: Screen = {
  title: 'Chaos panel',
  objective:
    'Simulates vendor failures. While a service fails, the activity retries with backoff. When it recovers, the application continues without manual action.',
  outcomes: [
    { label: 'Retry policy', value: '5 lines' },
    { label: 'Backoff', value: '1s, doubling' },
    { label: 'Gives up after', value: '10 minutes short, 1 hour production' },
    { label: 'Manual replays', value: '0' },
  ],
  whyTemporal: [
    {
      before: 'Retry wrappers, dead letter queues and a reconciliation job per vendor.',
      after: 'A retry policy on the activity. Each check retries on its own.',
    },
    {
      before: 'One slow vendor holds the whole request.',
      after: 'Checks run in parallel. A sanctions hit cancels the remaining checks.',
    },
    {
      before: 'A failed payout leaves a reservation and an empty loan account behind.',
      after: 'The saga undoes completed steps in reverse order. A retried payout uses the same key, so it pays once.',
    },
  ],
  flow: [
    { title: 'Break', detail: 'Set bureau to ERROR_500. New applications retry the bureau with backoff.' },
    { title: 'Watch', detail: 'Case detail and the Temporal UI show the attempt count and the last error.' },
    { title: 'Fix', detail: 'Reset all. The next retry succeeds and the application moves on.' },
  ],
  code: {
    file: 'ActivityStubs.java',
    text: `.setRetryOptions(RetryOptions.newBuilder()
    .setInitialInterval(Duration.ofSeconds(1))
    .setBackoffCoefficient(2.0)
    .setMaximumInterval(clock.retryMaxInterval())
    .setMaximumAttempts(0) // unlimited
    .build())`,
  },
  live: 'Settings are stored in MongoDB and read by the mock services on every call, so a change applies to the next attempt.',
}

const OPS: Screen = {
  title: 'Operations',
  objective:
    'Shows operations staff which applications have missed an underwriting SLA, which have sat too long in a system step, and whether the worker is running.',
  outcomes: [
    { label: 'Data source', value: 'MongoDB' },
    { label: 'Worker heartbeat', value: 'every 5s' },
    { label: 'Worker down for', value: '15s turns red' },
    { label: 'Reconciliation jobs', value: '0' },
  ],
  whyTemporal: [
    {
      before: 'Nightly reconciliation to find applications that stopped halfway.',
      after: 'A workflow does not stop halfway. Stuck means waiting on a retry, and the history says why.',
    },
    {
      before: 'Worker restart loses in-flight work.',
      after: 'Kill the worker: the badge turns red, applications wait on the Temporal server and continue on restart.',
    },
    {
      before: 'Different timeouts in test and production mean different code paths.',
      after: 'One workflow, two duration sets. The short set runs in seconds what production runs in days.',
    },
    {
      before: 'Process changes wait until in-flight loans drain, or are migrated by hand.',
      after: 'getVersion records which path each run took. Old runs finish on v1, new runs take v2.',
    },
  ],
  flow: [
    { title: 'Seed', detail: 'Starts 30 real workflows and drives them with signals into each stage.' },
    { title: 'Watch', detail: 'Counts, SLA breaches and stuck applications refresh every 5 seconds.' },
    { title: 'Deploy v2', detail: 'make deploy-v2 restarts the worker with the PEP check. Open applications stay on v1.' },
    { title: 'Reset', detail: 'Terminates running workflows and clears the collections.' },
  ],
  code: {
    file: 'VerificationWorkflowImpl.java',
    text: `// v2 adds the PEP check. Runs started on v1 have no
// marker in their history and replay as v1.
int version = Workflow.getVersion(
    "add-pep-check", Workflow.DEFAULT_VERSION, 1);

if (version == 1) {
    running.put("pep",
        Async.function(pep::checkPoliticalExposure, applicationId));
}`,
  },
  live: 'The worker writes a heartbeat to MongoDB every 5 seconds. Stop it with make kill-worker and the badge turns red within 15 seconds.',
}

const ROUTES: { pattern: string; screen: Screen }[] = [
  { pattern: '/', screen: OVERVIEW },
  { pattern: '/apply', screen: APPLY },
  { pattern: '/my/:id', screen: MY_APPLICATION },
  { pattern: '/underwriting', screen: QUEUE },
  { pattern: '/cases/:id', screen: CASE },
  { pattern: '/ops', screen: OPS },
  { pattern: '/chaos', screen: CHAOS },
]

export function screenFor(pathname: string): Screen {
  return ROUTES.find((r) => matchPath(r.pattern, pathname))?.screen ?? OVERVIEW
}

export { OVERVIEW }
