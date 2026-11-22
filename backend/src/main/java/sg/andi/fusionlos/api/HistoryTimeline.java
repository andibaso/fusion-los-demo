package sg.andi.fusionlos.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.Payloads;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflow.v1.PendingActivityInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sg.andi.fusionlos.domain.Stage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Temporal event history into a simple timeline for the Case Detail screen:
 * activities with attempts, timers with fire times, signals with the user who sent them.
 * Activities and timers come from the stage child workflows; signals come from the parent.
 */
@Component
@Profile("api")
public class HistoryTimeline {

    public record Row(
            String stage,
            String kind,        // ACTIVITY, TIMER, SIGNAL
            String name,
            Instant at,
            Instant closedAt,
            String outcome,     // SCHEDULED, RUNNING, RETRYING, COMPLETED, FAILED, TIMED_OUT, CANCELED, WAITING, FIRED
            Integer attempt,
            String lastFailure,
            Instant nextAt) {}  // next retry, or when the timer fires

    private final WorkflowClient temporal;
    private final ObjectMapper json;

    public HistoryTimeline(WorkflowClient temporal, ObjectMapper json) {
        this.temporal = temporal;
        this.json = json;
    }

    public List<Row> forApplication(String applicationId) {
        List<Row> rows = new ArrayList<>(signalsToParent(applicationId));
        for (Stage stage : Stage.values()) {
            rows.addAll(stageRows(stage, stage.childWorkflowId(applicationId)));
        }
        rows.sort(Comparator.comparing(Row::at));
        return rows;
    }

    /** Activities and timers of one stage child workflow, merged with live retry info for running activities. */
    private List<Row> stageRows(Stage stage, String workflowId) {
        List<HistoryEvent> events = events(workflowId);
        if (events.isEmpty()) return List.of();

        Map<Long, Row> byEventId = new LinkedHashMap<>();
        Map<String, Long> activityIdToEvent = new LinkedHashMap<>();
        for (HistoryEvent e : events) {
            Instant at = instant(e.getEventTime());
            switch (e.getEventType()) {
                case EVENT_TYPE_ACTIVITY_TASK_SCHEDULED -> {
                    var a = e.getActivityTaskScheduledEventAttributes();
                    byEventId.put(e.getEventId(), new Row(stage.name(), "ACTIVITY", a.getActivityType().getName(),
                            at, null, "SCHEDULED", 1, null, null));
                    activityIdToEvent.put(a.getActivityId(), e.getEventId());
                }
                case EVENT_TYPE_ACTIVITY_TASK_STARTED -> {
                    var a = e.getActivityTaskStartedEventAttributes();
                    String lastFailure = a.hasLastFailure() ? a.getLastFailure().getMessage() : null;
                    update(byEventId, a.getScheduledEventId(), r -> withAttempt(r, "RUNNING", a.getAttempt(), lastFailure));
                }
                case EVENT_TYPE_ACTIVITY_TASK_COMPLETED ->
                        close(byEventId, e.getActivityTaskCompletedEventAttributes().getScheduledEventId(), "COMPLETED", at, null);
                case EVENT_TYPE_ACTIVITY_TASK_FAILED -> {
                    var a = e.getActivityTaskFailedEventAttributes();
                    close(byEventId, a.getScheduledEventId(), "FAILED", at, a.getFailure().getMessage());
                }
                case EVENT_TYPE_ACTIVITY_TASK_TIMED_OUT -> {
                    var a = e.getActivityTaskTimedOutEventAttributes();
                    close(byEventId, a.getScheduledEventId(), "TIMED_OUT", at, a.getFailure().getMessage());
                }
                case EVENT_TYPE_ACTIVITY_TASK_CANCEL_REQUESTED ->
                        close(byEventId, e.getActivityTaskCancelRequestedEventAttributes().getScheduledEventId(), "CANCELED", at, null);
                case EVENT_TYPE_ACTIVITY_TASK_CANCELED ->
                        close(byEventId, e.getActivityTaskCanceledEventAttributes().getScheduledEventId(), "CANCELED", at, null);
                case EVENT_TYPE_TIMER_STARTED -> {
                    Duration d = duration(e.getTimerStartedEventAttributes().getStartToFireTimeout());
                    byEventId.put(e.getEventId(), new Row(stage.name(), "TIMER", "Timer " + pretty(d),
                            at, null, "WAITING", null, null, at.plus(d)));
                }
                case EVENT_TYPE_TIMER_FIRED ->
                        close(byEventId, e.getTimerFiredEventAttributes().getStartedEventId(), "FIRED", at, null);
                case EVENT_TYPE_TIMER_CANCELED ->
                        close(byEventId, e.getTimerCanceledEventAttributes().getStartedEventId(), "CANCELED", at, null);
                default -> { }
            }
        }

        // History only records the final attempt. Pending activity info shows the retries in progress.
        for (PendingActivityInfo pending : pendingActivities(workflowId)) {
            Long eventId = activityIdToEvent.get(pending.getActivityId());
            String lastFailure = pending.hasLastFailure() ? pending.getLastFailure().getMessage() : null;
            Instant nextAt = pending.hasNextAttemptScheduleTime() ? instant(pending.getNextAttemptScheduleTime()) : null;
            String outcome = pending.getAttempt() > 1 ? "RETRYING" : "RUNNING";
            update(byEventId, eventId, r -> new Row(r.stage(), r.kind(), r.name(), r.at(), null,
                    outcome, pending.getAttempt(), lastFailure, nextAt));
        }
        return new ArrayList<>(byEventId.values());
    }

    /** Signals the UI sent to the parent workflow, placed in the stage that was running at the time. */
    private List<Row> signalsToParent(String applicationId) {
        List<Row> rows = new ArrayList<>();
        String stage = Stage.INTAKE.name();
        for (HistoryEvent e : events(applicationId)) {
            switch (e.getEventType()) {
                case EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_STARTED -> {
                    String childId = e.getChildWorkflowExecutionStartedEventAttributes().getWorkflowExecution().getWorkflowId();
                    stage = childId.substring(childId.lastIndexOf('-') + 1).toUpperCase();
                }
                case EVENT_TYPE_WORKFLOW_EXECUTION_SIGNALED -> {
                    var a = e.getWorkflowExecutionSignaledEventAttributes();
                    Instant at = instant(e.getEventTime());
                    rows.add(new Row(stage, "SIGNAL", a.getSignalName() + describeSignal(a.getInput()),
                            at, at, "RECEIVED", null, null, null));
                }
                default -> { }
            }
        }
        return rows;
    }

    private String describeSignal(Payloads input) {
        if (input.getPayloadsCount() == 0) return "";
        try {
            JsonNode body = json.readTree(input.getPayloads(0).getData().toStringUtf8());
            String what = body.path("decision").asText(body.path("choice").asText(body.path("type").asText("")));
            String actor = body.path("actor").asText("");
            return ": " + what + (actor.isEmpty() ? "" : " by " + actor);
        } catch (Exception unreadable) {
            return "";
        }
    }

    private List<HistoryEvent> events(String workflowId) {
        try {
            return temporal.fetchHistory(workflowId).getEvents();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Code.NOT_FOUND) return List.of();
            throw e;
        }
    }

    private List<PendingActivityInfo> pendingActivities(String workflowId) {
        DescribeWorkflowExecutionResponse described = temporal.getWorkflowServiceStubs().blockingStub()
                .describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                        .setNamespace(temporal.getOptions().getNamespace())
                        .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                        .build());
        if (described.getWorkflowExecutionInfo().getStatus() != WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING) {
            return List.of();
        }
        return described.getPendingActivitiesList();
    }

    private static Row withAttempt(Row r, String outcome, int attempt, String lastFailure) {
        return new Row(r.stage(), r.kind(), r.name(), r.at(), r.closedAt(), outcome, attempt, lastFailure, r.nextAt());
    }

    private static void close(Map<Long, Row> rows, long eventId, String outcome, Instant at, String failure) {
        update(rows, eventId, r -> new Row(r.stage(), r.kind(), r.name(), r.at(), at, outcome, r.attempt(),
                failure != null ? failure : r.lastFailure(), "TIMER".equals(r.kind()) ? r.nextAt() : null));
    }

    private static void update(Map<Long, Row> rows, Long eventId, java.util.function.UnaryOperator<Row> change) {
        if (eventId != null) rows.computeIfPresent(eventId, (id, row) -> change.apply(row));
    }

    private static Instant instant(com.google.protobuf.Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }

    private static Duration duration(com.google.protobuf.Duration d) {
        return Duration.ofSeconds(d.getSeconds(), d.getNanos());
    }

    private static String pretty(Duration d) {
        if (d.toDays() > 0) return d.toDays() + "d";
        if (d.toHours() > 0) return d.toHours() + "h";
        if (d.toMinutes() > 0) return d.toMinutes() + "m";
        return d.toSeconds() + "s";
    }
}
