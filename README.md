# FusionLOS

FusionLOS is a loan origination system: the software a bank uses to take a personal loan from application to payout. It is built on Temporal and MongoDB. Each application is one Temporal workflow that moves through five stages: intake, verification, underwriting, offer and disbursement. MongoDB holds the loan file; Temporal runs the process.

Stack: Java 21, Spring Boot 3.3, Temporal Java SDK 1.41, MongoDB 8, React 18, Vite, TypeScript, Tailwind CSS.

## The scenario

A bank offers personal loans in SGD. Four people use the system:

- **Ben** applies for a loan. He fills in a form, watches his application move through the stages, uploads a document if the bank asks for one, and accepts or declines the offer he gets.
- **Alice** is an underwriter. New cases land in her queue. She approves, rejects, or asks the applicant for a document.
- **Carol** is a senior underwriter. She sees the whole team's queue. If Alice leaves a case waiting too long, it is escalated to Carol. Large loans need a second approval, from someone other than the first approver.
- **Dave** works in operations. He watches the counts, the waiting times and the worker, and he can switch the outside services into failure modes to see how the system copes.

An application goes through these steps:

1. **Intake.** The application is checked for completeness and saved.
2. **Verification.** Identity (KYC), credit bureau and sanctions checks run at the same time. Loans above 100,000 also get a property valuation. A sanctions hit rejects the application at once.
3. **Underwriting.** The case is assigned to an underwriter. If nobody decides, a reminder goes out, then the case is escalated to the senior underwriter. Loans above 50,000 need two approvals from two different people.
4. **Offer.** The rate depends on the credit score and the term. The offer expires if the applicant does not answer in time.
5. **Disbursement.** Funds are reserved, a loan account is opened, and the money is paid out. If the payout fails, the account is closed and the reservation released, in that order.

The outside services (KYC, bureau, sanctions, valuation, core banking, notifications) are simulated in the backend, so everything runs on one machine. Their failures can be switched on from the chaos screen.

## What it shows

| | In business terms | How Temporal does it |
| --- | --- | --- |
| Nothing gets lost | Applications never end up in an unknown state after a crash | Kill the worker mid stage. On restart the workflow continues from the last completed step |
| Failures handle themselves | A vendor outage delays applications; they continue when it recovers | A retry policy on each activity. Attempts and errors are visible, no retry code in the app |
| Waiting is free and managed | Reminders, escalation and offer expiry run on schedule | Durable timers and signals. A wait of days holds no thread and survives restarts |
| Compensation is built in | If payout fails, the earlier steps are reversed | Saga steps with compensations that run in reverse, each one on record |
| Change without breaking loans in progress | Process changes ship while applications are open | `Workflow.getVersion`: old runs finish on old logic, new runs take the new path |

The event history of a completed application in the Temporal UI is a full audit trail of what happened, when, and who did it.

## Getting started

You need Docker, JDK 21, Maven and Node 20.

```bash
cp .env.example .env   # optional, see Configuration
make up                # Temporal, Temporal UI and MongoDB in Docker
```

Then start the three processes, each in its own terminal:

```bash
make api       # REST API on 127.0.0.1:8080
make worker    # Temporal worker: runs the workflows and activities
make web       # frontend on port 3003
```

Open http://localhost:3003 and choose a user on the sign in page. The Temporal UI is at http://localhost:3003/temporal.

To fill the system with sample data, sign in as Dave and press **Seed 30 applications** on the Operations screen, or run `make seed`.

Port 3003 is the only entry point. Vite serves the frontend there and forwards `/api` to Spring Boot and `/temporal` to the Temporal UI. Spring Boot, Temporal (7233), the Temporal UI (8233) and MongoDB listen on 127.0.0.1 only, and the browser always talks to one address, so there is no CORS setup.

## Sample data

Seeding starts 30 real workflows, not database rows. Each application goes through the same code as one submitted from the form, and a background task plays the underwriter and applicant until it reaches its target stage. Within about a minute you have:

| Count | Where it ends up | Why it stays there |
| --- | --- | --- |
| 6 | Verification | Their credit bureau check keeps failing, so they sit in retries. Shows what an outage looks like |
| 10 | Underwriting, waiting for Alice | Normal timers: the reminder is two days away |
| 4 | Underwriting, escalated to Carol | Short timers: they escalate after 45 seconds |
| 4 | Offer sent, waiting for the applicant | Approved by underwriting; one of them needed two approvals |
| 3 | Completed, money paid out | Approved and accepted |
| 2 | Rejected by the underwriter | Debt to income too high |
| 1 | Rejected by sanctions | The name "Ivan Blocked" is on the simulated sanctions list |

The applicants are fictional (Aisha Rahman, Brandon Lim, and so on) with `example.com` email addresses. Amounts range from 10,000 to 250,000, so some cases need two approvals and some need a valuation. Monthly income is derived from the amount, the term is 36 months, or 60 above 100,000.

Seeded applications have no owner, so Ben does not see them. Applications Ben submits himself are his.

Some names trigger rules in the simulated services:

- A name containing **Blocked** is a sanctions hit.
- A name containing **Senator** is a politically exposed person (PEP). This only matters once the PEP check is switched on, see below.
- Amounts above **400,000** fail the payout with insufficient funds, which is not retried.

The credit score is derived from the applicant, so a check that runs again gives the same score.

To start over, press the reset button on the Operations screen or run `make reset`. It ends every open workflow and deletes all applications.

## Things to try

Keep the app and the Temporal UI side by side.

1. **A loan from start to finish.** Sign in as Ben and apply for 30,000. In the Temporal UI, open the verification workflow and see the three checks run in parallel. Sign in as Alice, open the case from the queue and approve it. Sign in as Ben again and accept the offer. The status ends at COMPLETED. The MongoDB document: `docker compose exec mongodb mongosh fusionlos --eval 'db.applications.findOne({_id:"APP-..."})'`.
2. **A crash.** Submit an application. While it is in verification, run `make kill-worker`. The worker badge turns red within 15 seconds and the pending work is visible in the Temporal UI. Start the worker again with `make worker`. The application carries on where it stopped.
3. **A failing service.** As Dave, open the chaos screen (`/chaos`) and set the bureau to ERROR_500. Submit an application. The case detail screen shows the attempts and the last error. Reset the bureau and the next retry succeeds. The retry policy is a few lines in `ActivityStubs.java`.
4. **People and timers.**
   - Open an escalated case (a red row in the queue) and look at its reminder and escalation timers in the Temporal UI.
   - Kill and restart the worker while a case waits: the timers are still there.
   - Request a payslip from the applicant. As Ben, upload it. Only the credit check runs again.
   - On a loan above 50,000: approve as Alice, approve again as Alice (ignored, but recorded), then approve as Carol.
5. **A failed payout.** On the chaos screen, set the payout to DOWN, then accept an offer. Within about 15 seconds the status is DISBURSEMENT_COMPENSATED. The ledger on the case and the applicant's timeline show the loan account closed and then the reservation released. With payout TIMEOUT instead, the money is paid exactly once: one payout, the rest are repeat requests recognised by their key.
6. **Changing the process while loans are open.**
   - Have one case waiting in underwriting (A). Optionally put another one in verification with the bureau on ERROR_500 (B).
   - Stop the worker and start it as version 2, which adds the PEP check: `LOS_FEATURE_PEP=true make worker`. The worker badge shows v2.
   - Reset chaos and submit an application as "Senator Ben Ong" (C). C shows five checks including PEP, a PEP review flag in the queue, and "workflow v2" on the case.
   - Approve and accept A and B. Both finish on version 1 without errors. The Operations screen shows open applications by version.
   - In code: `VerificationWorkflowImpl.choosePath()`, then `make test-replay`. Replace the `getVersion` call with `return true` and run `make test-replay` again: the version 1 histories now fail with a NonDeterministicException.
7. **The audit trail.** Open the event history of the completed application from step 1.

To go back to version 1, run `make reset`, restart the worker with plain `make worker`, then seed again.

## Make targets

| Target | What it does |
| --- | --- |
| `make up` / `make down` | Start or stop Temporal, Temporal UI and MongoDB |
| `make api`, `make worker`, `make web` | Run one process in the foreground |
| `make kill-worker` | Kill the worker process at once (`kill -9`) |
| `make seed` / `make reset` | Add the 30 sample applications / end all workflows and delete all data |
| `make test` | All workflow tests, including replay |
| `make test-replay` | Only the replay test against `histories/` |
| `make export-history ID=APP-2026-000123 LABEL=v1` | Save an application's histories for the replay test |

## Configuration

`make` and `docker compose` both read `.env` in the repository root. Every setting is optional.

| Variable | Effect |
| --- | --- |
| `LOS_PUBLIC_ORIGIN` | Public URL when the app sits behind a reverse proxy, for example `https://loans.example.com`. Vite accepts that host name and the Temporal UI allows it as an origin. Unset: localhost only |
| `LOS_SESSION_SECRET` | Key that signs the session cookie. Unset: random per start, so an API restart signs everyone out |
| `LOS_LOGIN_PASSWORD` | Shared password for the sign in page. `make seed` and `make reset` pass it on. Unset: one click sign in |
| `LOS_FEATURE_PEP` | `true` runs the worker as version 2, with the PEP check |
| `JAVA_HOME`, `MAVEN_HOME` | Local JDK 21 and Maven installs, if they are not on `PATH` |
| `DURATIONS` | `prod-durations` for real waiting times. The default uses short ones |
| `TEMPORAL_ADDRESS`, `TEMPORAL_NAMESPACE`, `TEMPORAL_TLS_CERT`, `TEMPORAL_TLS_KEY` | Point api and worker at another Temporal service, such as Temporal Cloud with mTLS |

Waiting times: by default the reminder fires after 20 seconds, escalation after 45 seconds and offer expiry after 60 seconds, so you can watch them happen. `DURATIONS=prod-durations make worker` uses the real ones: 2 days, 5 days and 7 days. Workflows read them through an activity, so the same code and the same history work with both.

To put the app on the internet, run a reverse proxy with HTTPS in front of port 3003, and set `LOS_PUBLIC_ORIGIN` and `LOS_LOGIN_PASSWORD`. The Temporal UI at `/temporal` is not behind the sign in page.

## Sign in and roles

The sign in page shows one card per user. Sign out at the bottom of the sidebar to switch.

| User | Role | Sees and does |
| --- | --- | --- |
| Ben (`applicant.ben`) | Applicant | Apply, follow, upload documents, accept the offer, withdraw. Only his own applications |
| Alice (`uw.alice`) | Underwriter | Her own queue. Cannot decide escalated cases |
| Carol (`uw.carol`) | Senior underwriter | The whole team queue, escalated cases, second approvals |
| Dave (`ops.dave`) | Operations | Operations screen, chaos screen, seed and reset. Case detail read only |

Signing in sets a signed (HMAC-SHA256), HttpOnly cookie that lasts 12 hours. The API takes the user from that cookie, never from the browser, and every endpoint declares its roles with `@RequiresRole`. No session gives 401, the wrong role 403, and an applicant opening someone else's application gets 404.

One browser holds one session. To keep the chaos screen open as Dave while working as someone else, use a private window or a second browser.

## Screens

| Path | For |
| --- | --- |
| `/` | Overview: what the system does and why it is built on Temporal |
| `/apply`, `/my/:id` | Applicant: submit, follow progress, upload documents, accept the offer, withdraw |
| `/underwriting` | Underwriter queue with reminder and escalation countdowns and two approval badges |
| `/cases/:id` | Case detail: decision panel, checks, ledger, and a workflow timeline built from the Temporal history |
| `/ops` | Operations: counts, SLA breaches, stuck list, worker status, waiting times, workflow versions |
| `/chaos` | Chaos screen: put any simulated service into a failure mode |

Press `?` on any screen for a short explanation of what is happening underneath.

## How it is built

### Who owns what

- **Temporal owns the process:** stages, waits, retries, timers, and the status of each application.
- **MongoDB owns the data:** the application document, check results, decisions, and an audit log for the list screens.
- **Existing services do the work:** KYC, bureau, sanctions, valuation and core banking are called as activities. Here they are simulated in process.

### Rules the code follows

- Workflow code is deterministic: no system clock, random, I/O or MongoDB in workflow classes. Time comes from `Workflow.currentTimeMillis()`, settings from an activity.
- `status` and `stage` on the application document are written only by activities called from workflows. Controllers never write them.
- Workflow ID equals the application ID, so there is exactly one running workflow per application. Child workflow IDs are `{applicationId}-{stage}`, for example `APP-2026-000123-verification`.
- Workflows and activities pass IDs and small records, never the loan document. Activities load it from MongoDB by ID.

### Parent workflow

`LoanApplicationWorkflow` runs the five stage workflows in sequence on task queue `los-main`. It receives every signal (`underwriterDecision`, `documentUploaded`, `offerResponse`, `cancel`) and passes it to the running stage, so the UI has one address per application. A `state()` query returns the live stage, status and timers. Each stage runs in its own cancellation scope, so withdrawing cancels the current stage; disbursement cannot be cancelled once it starts.

### Stages

- **Intake:** validate, save, record. Simple on purpose, so every stage follows the same pattern.
- **Verification:** KYC, bureau and sanctions run in parallel (`Async.function`, `Promise.allOf`), plus valuation above 100,000. A sanctions hit cancels the other checks and rejects at once. If the bureau is down, the other checks complete and the stage waits only on bureau retries.
- **Underwriting:** assigns the case, then waits for a decision signal with a reminder timer and an escalation timer alongside. Request info waits for the document, runs only the affected check again, and loops, at most 3 times. Above 50,000 a second approval from a different person is required; the same person twice is ignored and recorded.
- **Offer:** computes the offer from a rate table and waits for accept or decline, with an expiry timer.
- **Disbursement (saga):** reserve funds, open the loan account, pay out with an idempotency key, notify. If a step fails after its retries, the compensations run in reverse: close the loan account, then release the reservation.

### Versioning

- `VerificationWorkflowImpl.choosePath()` calls `Workflow.getVersion("add-pep-check", DEFAULT_VERSION, 1)`. The first run writes a marker into the history; runs without the marker replay as version 1.
- Version 2 adds the PEP check to verification and a PEP review reason to the underwriting case.
- `LOS_FEATURE_PEP=true` stands in for deploying a new build. It only decides whether the worker has the `getVersion` call at all. The marker is what protects applications already in progress.
- `histories/` holds three exported applications: one on version 1, one on version 2, and one that was in verification when version 2 went live. `ReplayTest` replays all of them against the current code.

### Application statuses

```
INTAKE_RECEIVED
VERIFICATION_RUNNING, VERIFICATION_FAILED
UNDERWRITING_PENDING, UNDERWRITING_INFO_REQUESTED, UNDERWRITING_ESCALATED, UNDERWRITING_AWAITING_SECOND_APPROVAL
OFFER_SENT, OFFER_EXPIRED, OFFER_DECLINED
DISBURSEMENT_RUNNING, DISBURSEMENT_COMPENSATED
COMPLETED, REJECTED, CANCELLED
```

### Simulated services and chaos

Each simulated service is a Spring bean shaped like a client, so it could be swapped for the real thing. Each one has a chaos state set from the chaos screen: `OK`, `SLOW`, `ERROR_500`, `TIMEOUT` or `DOWN`, plus extra latency and "fail the next N calls". Core banking keeps payouts by idempotency key, so a retried payout never pays twice.

### Worker status

The worker writes a heartbeat to MongoDB every 5 seconds, with its version. The API reports the worker as up if the last heartbeat is less than 15 seconds old; the badge turns red when the worker is gone.

## REST API

All under `/api`. Every endpoint except `/api/auth/*` needs a session.

| Endpoint | Roles |
| --- | --- |
| `GET /auth/options`, `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` | Anyone |
| `POST /applications` | Applicant |
| `GET /applications`, `GET /applications/{id}`, `.../audit`, `.../history` | Signed in; applicants see only their own |
| `POST /applications/{id}/documents`, `.../offer`, `.../cancel` | Applicant, owner only |
| `GET /underwriting/queue` | Underwriter (own cases), senior underwriter (all) |
| `POST /applications/{id}/decision` | Underwriter, senior underwriter. Escalated cases: senior only |
| `GET /applications/{id}/ledger` | Underwriters, operations |
| `GET /ops/worker` | Signed in |
| `GET /ops/summary`, `GET /config/durations`, `POST /admin/seed`, `POST /admin/reset` | Operations |
| `GET /chaos`, `POST /chaos/{service}`, `POST /chaos/reset`, `GET` and `POST /config/verification` | Operations |

`GET /applications/{id}/history` reads the Temporal event history and simplifies it to a timeline of activities with attempts, timers and signals. List screens read MongoDB only and never query Temporal.

## Temporal UI filters

The workflows set the search attributes `Stage`, `Status`, `Amount` and `AssignedTo`. `Status` is a reserved word in the query language, so quote it with backticks:

```
`Status`="UNDERWRITING_ESCALATED"
Stage="UNDERWRITING" and AssignedTo="uw.carol"
Amount > 100000
```

The parent workflow carries the final status; each stage workflow carries the last status it set.

## Tests

`make test` runs:

- `LoanApplicationWorkflowTest`: full approval, escalation, offer expiry, request info loop, same person approving twice, withdrawal during underwriting, withdrawal ignored during disbursement.
- `DisbursementSagaTest`: compensation in reverse order, no retry on insufficient funds, a lost payout response found by its key.
- `VerificationFailFastTest`: a sanctions hit rejects without waiting for a slow bureau.
- `VersioningTest`: version 1 runs three checks, version 2 adds PEP.
- `ReplayTest`: every history in `histories/`.

The workflow tests use Temporal's test environment with time skipping, so a 7 day offer expiry runs in milliseconds.

## Layout

```
backend/    Spring Boot app with two profiles: api (REST) and worker (Temporal worker)
  workflow/         parent workflow and the five stage workflows
  activity/         activities; the only code that writes status and stage
  mock/             simulated KYC, bureau, sanctions, PEP, valuation, core banking, with chaos switches
  auth/             sign in, session cookie, roles
  api/, ops/, projection/   REST, sample data, operations summary, queue and audit reads
frontend/   React app
scripts/    helper scripts, including history export
histories/  exported workflow histories for ReplayTest
```

## Out of scope

Real credit decisioning, document OCR, multiple products, real authentication, multi tenancy, mobile layout, and real core banking integration. Each of those would be an activity calling an existing service.
