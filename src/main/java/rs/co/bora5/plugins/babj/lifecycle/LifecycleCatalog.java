package rs.co.bora5.plugins.babj.lifecycle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.Edge;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.MethodRef;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.Owner;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.Step;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.StepKind;

/** Known BAB template-method chains. Kept separate from PSI so the graph is deterministic. */
public final class LifecycleCatalog {

    private static final Map<LifecycleEvent, LifecycleTemplate> TEMPLATES = buildTemplates();

    private LifecycleCatalog() {
    }

    public static LifecycleTemplate template(LifecycleEvent event) {
        return TEMPLATES.get(event);
    }

    private static Map<LifecycleEvent, LifecycleTemplate> buildTemplates() {
        Map<LifecycleEvent, LifecycleTemplate> result = new EnumMap<>(LifecycleEvent.class);
        result.put(LifecycleEvent.VALIDATE, validate());
        result.put(LifecycleEvent.MULTI_VALIDATE, multiValidate());
        result.put(LifecycleEvent.STORNO, serviceAction(LifecycleEvent.STORNO,
                "beforeStorno", 1, "storno", 3, "beforeStorno", 2,
                "Set cancellation data", null, 0, "afterStorno", 1, true));
        result.put(LifecycleEvent.ARRIVED, serviceAction(LifecycleEvent.ARRIVED,
                "beforeRecived", 1, "arrived", 2, "beforeArrived", 2,
                "Set arrived data", "afterArrived", 2, "afterRecived", 1, true));
        result.put(LifecycleEvent.INVALIDATE, serviceAction(LifecycleEvent.INVALIDATE,
                "beforeInvalidate", 1, "invalidate", 1, "beforeInvalidate", 1,
                "Clear validation data", null, 0, null, 0, false));
        result.put(LifecycleEvent.PAYOUT, serviceAction(LifecycleEvent.PAYOUT,
                "beforePayout", 1, "payout", 2, "beforePayout", 2,
                "Set payout data", null, 0, "afterPayout", 1, true));
        // GenericView currently deliberately exposes payout hooks around unpayout as well.
        result.put(LifecycleEvent.UNPAYOUT, serviceAction(LifecycleEvent.UNPAYOUT,
                "beforePayout", 1, "unpayout", 1, "beforeUnpayout", 1,
                "Clear payout data", null, 0, "afterPayout", 1, true));
        result.put(LifecycleEvent.DELETE, delete());
        result.put(LifecycleEvent.CREATE, save(true));
        result.put(LifecycleEvent.EDIT, save(false));
        result.put(LifecycleEvent.REPORT_EXECUTE, report());
        return Map.copyOf(result);
    }

    private static LifecycleTemplate validate() {
        Builder b = new Builder(LifecycleEvent.VALIDATE);
        b.step("start", "Validate action", "User starts validation", StepKind.START, 0, 0);
        b.method("prepare", "Flush and reload entity", "GenericView action handler",
                StepKind.ACTION, 1, 0, Owner.VIEW, "handleValidateAction", 2);
        b.step("approved", "Approved?", "Validation dialog result", StepKind.DECISION, 2, 0);
        b.method("viewBefore", "View.beforeValidate", "View-level guard", StepKind.HOOK,
                3, 0, Owner.VIEW, "beforeValidate", 1);
        b.step("continue", "Continue?", "false aborts validation", StepKind.DECISION, 4, 0);
        b.method("homeValidate", "Home.validate", "Service transaction", StepKind.ACTION,
                5, 0, Owner.HOME, "validate", 2);
        b.method("find", "Home.find", "Reload managed entity", StepKind.ACTION,
                6, 0, Owner.HOME, "find", 1);
        b.method("homeBefore", "Home.beforeValidate", "Service-level entity hook",
                StepKind.HOOK, 7, 0, Owner.HOME, "beforeValidate", 2);
        b.step("metadata", "Set validation data", "validated flag, operator and time",
                StepKind.ACTION, 8, 0);
        b.method("save", "Home.save", "Persist entity", StepKind.ACTION,
                9, 0, Owner.HOME, "save", 1);
        b.method("dto", "Home.findDTO", "Return refreshed projection", StepKind.ACTION,
                10, 0, Owner.HOME, "findDTO", 1);
        commonViewTail(b, 11, "viewAfter", "afterValidate");
        b.step("rejected", "Save rejection comment", "Home.save and View.refresh",
                StepKind.SIDE_EFFECT, 3, 1);
        b.step("aborted", "Validation aborted", "beforeValidate returned false",
                StepKind.STOP, 5, -1);
        b.step("rejectedEnd", "Rejection recorded", "Entity remains unvalidated",
                StepKind.END, 4, 1);

        b.edge("start", "prepare", "");
        b.edge("prepare", "approved", "");
        b.edge("approved", "viewBefore", "yes");
        b.edge("approved", "rejected", "no");
        b.edge("rejected", "rejectedEnd", "");
        b.edge("viewBefore", "continue", "");
        b.edge("continue", "homeValidate", "true");
        b.edge("continue", "aborted", "false");
        chain(b, "homeValidate", "find", "homeBefore", "metadata", "save", "dto",
                "broadcast", "refresh", "notification", "viewAfter", "end");
        return b.build();
    }

    private static LifecycleTemplate multiValidate() {
        Builder b = new Builder(LifecycleEvent.MULTI_VALIDATE);
        b.step("start", "Multi-validate action", "User submits a validation decision",
                StepKind.START, 0, 0);
        b.step("condition", "Load validation condition", "Condition for the current operator",
                StepKind.ACTION, 1, 0);
        b.method("viewBefore", "View.beforeValidate", "View-level guard", StepKind.HOOK,
                2, 0, Owner.VIEW, "beforeValidate", 1);
        b.step("continue", "Continue?", "false aborts the decision", StepKind.DECISION, 3, 0);
        b.method("homeValidate", "Home.validate", "Pass approve/deny and comment",
                StepKind.ACTION, 4, 0, Owner.HOME, "validate", 4);
        b.method("homeBefore", "Home.beforeValidate", "Service-level entity hook",
                StepKind.HOOK, 5, 0, Owner.HOME, "beforeValidate", 2);
        b.step("record", "Validate condition", "Record this operator's decision",
                StepKind.ACTION, 6, 0);
        b.method("try", "Home.tryValidation", "Check all required conditions",
                StepKind.ACTION, 7, 0, Owner.HOME, "tryValidation", 2);
        b.step("validatable", "All conditions met?", "entity.isValidatable()",
                StepKind.DECISION, 8, 0);
        b.step("finalize", "Set validated and save", "Finalize parent entity",
                StepKind.ACTION, 9, 0);
        b.method("success", "Home.onSucessfulValidate", "Successful-validation hook",
                StepKind.HOOK, 10, 0, Owner.HOME, "onSucessfulValidate", 2);
        b.step("pending", "Keep pending state", "Other validation conditions remain",
                StepKind.ACTION, 9, 1);
        b.method("dto", "Home.findDTO", "Return refreshed projection", StepKind.ACTION,
                11, 0, Owner.HOME, "findDTO", 1);
        commonViewTail(b, 12, "viewAfter", "afterValidate");
        b.step("aborted", "Decision aborted", "beforeValidate returned false",
                StepKind.STOP, 4, -1);

        chain(b, "start", "condition", "viewBefore", "continue");
        b.edge("continue", "homeValidate", "true");
        b.edge("continue", "aborted", "false");
        chain(b, "homeValidate", "homeBefore", "record", "try", "validatable");
        b.edge("validatable", "finalize", "yes");
        b.edge("finalize", "success", "");
        b.edge("success", "dto", "");
        b.edge("validatable", "pending", "no");
        b.edge("pending", "dto", "");
        chain(b, "dto", "broadcast", "refresh", "notification", "viewAfter", "end");
        return b.build();
    }

    private static LifecycleTemplate serviceAction(LifecycleEvent event,
                                                    String viewBefore, int viewBeforeParams,
                                                    String operation, int operationParams,
                                                    String homeBefore, int homeBeforeParams,
                                                    String mutation,
                                                    String homeAfter, int homeAfterParams,
                                                    String viewAfter, int viewAfterParams,
                                                    boolean reloadForAfter) {
        Builder b = new Builder(event);
        b.step("start", event.getDisplayName() + " action", "User confirms the action",
                StepKind.START, 0, 0);
        b.method("prepare", "Flush and prepare entity", "GenericView action handler",
                StepKind.ACTION, 1, 0, Owner.VIEW, handlerName(event), 2);
        b.method("viewBefore", "View." + viewBefore, "View-level guard", StepKind.HOOK,
                2, 0, Owner.VIEW, viewBefore, viewBeforeParams);
        b.step("continue", "Continue?", "false aborts the action", StepKind.DECISION, 3, 0);
        b.method("operation", "Home." + operation, "Service transaction", StepKind.ACTION,
                4, 0, Owner.HOME, operation, operationParams);
        b.method("find", "Home.find", "Reload managed entity", StepKind.ACTION,
                5, 0, Owner.HOME, "find", 1);
        b.method("homeBefore", "Home." + homeBefore, "Service-level entity hook",
                StepKind.HOOK, 6, 0, Owner.HOME, homeBefore, homeBeforeParams);
        b.step("mutation", mutation, "Update framework state fields", StepKind.ACTION, 7, 0);
        b.method("save", "Home.save", "Persist entity", StepKind.ACTION,
                8, 0, Owner.HOME, "save", 1);
        int dtoRow = 9;
        if (homeAfter != null) {
            b.method("homeAfter", "Home." + homeAfter, "Service-level post-update hook",
                    StepKind.HOOK, 9, 0, Owner.HOME, homeAfter, homeAfterParams);
            b.method("saveAfter", "Home.save", "Persist changes made by " + homeAfter,
                    StepKind.ACTION, 10, 0, Owner.HOME, "save", 1);
            dtoRow = 11;
        }
        b.method("dto", "Home.findDTO", "Return refreshed projection", StepKind.ACTION,
                dtoRow, 0, Owner.HOME, "findDTO", 1);
        b.step("broadcast", "Broadcast TableUpdatedEvent", "Notify other views",
                StepKind.SIDE_EFFECT, dtoRow + 1, 0);
        b.step("refresh", "Refresh view", "Reload the selected row",
                StepKind.SIDE_EFFECT, dtoRow + 2, 0);
        b.step("notification", "Show success notification", "Visible UI feedback",
                StepKind.SIDE_EFFECT, dtoRow + 3, 0);
        int endRow = dtoRow + 4;
        if (viewAfter != null) {
            b.method("viewAfter", "View." + viewAfter,
                    reloadForAfter ? "Reload entity and run view hook" : "Run view hook",
                    StepKind.HOOK, endRow, 0, Owner.VIEW, viewAfter, viewAfterParams);
            endRow++;
        }
        b.step("end", event.getDisplayName() + " complete", "", StepKind.END, endRow, 0);
        b.step("aborted", "Action aborted", viewBefore + " returned false",
                StepKind.STOP, 4, -1);

        chain(b, "start", "prepare", "viewBefore", "continue");
        b.edge("continue", "operation", "true");
        b.edge("continue", "aborted", "false");
        chain(b, "operation", "find", "homeBefore", "mutation", "save");
        if (homeAfter == null) {
            b.edge("save", "dto", "");
        } else {
            chain(b, "save", "homeAfter", "saveAfter", "dto");
        }
        chain(b, "dto", "broadcast", "refresh", "notification");
        if (viewAfter == null) {
            b.edge("notification", "end", "");
        } else {
            chain(b, "notification", "viewAfter", "end");
        }
        return b.build();
    }

    private static String handlerName(LifecycleEvent event) {
        return switch (event) {
            case STORNO -> "handleStornoAction";
            case ARRIVED -> "handleArrivedAction";
            case INVALIDATE -> "handleInvalidateAction";
            case PAYOUT -> "handlePayoutAction";
            case UNPAYOUT -> "handleUnpayoutAction";
            default -> throw new IllegalArgumentException("No handler for " + event);
        };
    }

    private static LifecycleTemplate delete() {
        Builder b = new Builder(LifecycleEvent.DELETE);
        b.step("start", "Delete action", "User confirms deletion", StepKind.START, 0, 0);
        b.step("allowed", "Deletion allowed?", "Cascade enabled or entity is empty",
                StepKind.DECISION, 1, 0);
        b.method("before", "View.beforeDelete", "View-level guard", StepKind.HOOK,
                2, 0, Owner.VIEW, "beforeDelete", 0);
        b.step("continue", "Continue?", "false shows an error", StepKind.DECISION, 3, 0);
        b.method("find", "Home.find", "Load selected entity", StepKind.ACTION,
                4, 0, Owner.HOME, "find", 1);
        b.method("remove", "Home.remove", "Remove entity", StepKind.ACTION,
                5, 0, Owner.HOME, "remove", 1);
        b.step("files", "Delete associated files", "Only for file-system entities",
                StepKind.DECISION, 6, 0);
        b.step("refresh", "Broadcast and refresh", "Update all table views",
                StepKind.SIDE_EFFECT, 7, 0);
        b.step("end", "Delete complete", "", StepKind.END, 8, 0);
        b.step("blocked", "Deletion blocked", "Entity has dependent data",
                StepKind.STOP, 2, 1);
        b.step("aborted", "Delete aborted", "beforeDelete returned false",
                StepKind.STOP, 4, -1);
        chain(b, "start", "allowed");
        b.edge("allowed", "before", "yes");
        b.edge("allowed", "blocked", "no");
        chain(b, "before", "continue");
        b.edge("continue", "find", "true");
        b.edge("continue", "aborted", "false");
        chain(b, "find", "remove", "files", "refresh", "end");
        return b.build();
    }

    private static LifecycleTemplate save(boolean create) {
        LifecycleEvent event = create ? LifecycleEvent.CREATE : LifecycleEvent.EDIT;
        String before = create ? "doBeforePersist" : "doBeforeEdit";
        String after = create ? "doAfterPersist" : "doAfterEdit";
        String callback = create ? "afterPersist" : "afterEdit";
        Builder b = new Builder(event);
        b.step("start", event.getDisplayName(), "User confirms the edit window",
                StepKind.START, 0, 0);
        b.step("valid", "Binder valid?", "writeBeanIfValid", StepKind.DECISION, 1, 0);
        b.method("before", "Window." + before, "Pre-save guard", StepKind.HOOK,
                2, 0, Owner.WINDOW, before, 0);
        b.step("continueBefore", "Continue?", "false keeps the window open",
                StepKind.DECISION, 3, 0);
        b.method("save", "Home.save", "Persist entity", StepKind.ACTION,
                4, 0, Owner.HOME, "save", 1);
        if (!create) {
            b.step("stale", "Stale state?", "Optimistic-lock exception branch",
                    StepKind.DECISION, 5, 0);
        }
        int callbackRow = create ? 5 : 6;
        b.step("callback", "PopulateEvent." + callback, "Notify parent view",
                StepKind.SIDE_EFFECT, callbackRow, 0);
        b.step("sound", "Broadcast success sound", "When sounds are configured",
                StepKind.SIDE_EFFECT, callbackRow + 1, 0);
        b.method("after", "Window." + after, "Post-save guard", StepKind.HOOK,
                callbackRow + 2, 0, Owner.WINDOW, after, 0);
        b.step("continueAfter", "Continue?", "false stops final window handling",
                StepKind.DECISION, callbackRow + 3, 0);
        b.step("finish", create ? "Continual entry?" : "Close window",
                create ? "Open a fresh form or close" : "Finish editing",
                create ? StepKind.DECISION : StepKind.END, callbackRow + 4, 0);
        if (create) {
            b.step("newForm", "Prepare next entry", "Instantiate, clone defaults and focus",
                    StepKind.ACTION, callbackRow + 5, 1);
            b.step("close", "Close window", "", StepKind.END, callbackRow + 5, -1);
        }
        b.step("invalid", "Show validation errors", "Broadcast error sound",
                StepKind.STOP, 2, 1);
        b.step("beforeStop", "Save aborted", before + " returned false",
                StepKind.STOP, 4, -1);
        b.step("afterStop", "Final handling stopped", after + " returned false",
                StepKind.STOP, callbackRow + 4, -1);
        if (!create) {
            b.step("staleStop", "Handle stale save", "Refresh after optimistic-lock conflict",
                    StepKind.STOP, 6, 1);
        }

        b.edge("start", "valid", "");
        b.edge("valid", "before", "yes");
        b.edge("valid", "invalid", "no");
        chain(b, "before", "continueBefore");
        b.edge("continueBefore", "save", "true");
        b.edge("continueBefore", "beforeStop", "false");
        if (create) {
            chain(b, "save", "callback", "sound", "after", "continueAfter");
        } else {
            b.edge("save", "stale", "");
            b.edge("stale", "callback", "no");
            b.edge("stale", "staleStop", "yes");
            chain(b, "callback", "sound", "after", "continueAfter");
        }
        b.edge("continueAfter", "finish", "true");
        b.edge("continueAfter", "afterStop", "false");
        if (create) {
            b.edge("finish", "newForm", "yes");
            b.edge("finish", "close", "no");
        }
        return b.build();
    }

    private static LifecycleTemplate report() {
        Builder b = new Builder(LifecycleEvent.REPORT_EXECUTE);
        b.step("start", "Execute report", "User confirms the report window",
                StepKind.START, 0, 0);
        b.step("valid", "Binder valid?", "writeBeanIfValid", StepKind.DECISION, 1, 0);
        b.step("sound", "Broadcast success sound", "When sounds are configured",
                StepKind.SIDE_EFFECT, 2, 0);
        b.method("before", "Report.doBeforeExec", "Before-execution hook", StepKind.HOOK,
                3, 0, Owner.REPORT, "doBeforeExec", 0);
        b.method("main", "Report.doMain", "Main report/action logic", StepKind.HOOK,
                4, 0, Owner.REPORT, "doMain", 0);
        b.method("after", "Report.doAfterExec", "After-execution hook", StepKind.HOOK,
                5, 0, Owner.REPORT, "doAfterExec", 0);
        b.step("close", "Close window", "", StepKind.END, 6, 0);
        b.step("invalid", "Show validation errors", "Broadcast error sound",
                StepKind.STOP, 2, 1);
        chain(b, "start", "valid");
        b.edge("valid", "sound", "yes");
        b.edge("valid", "invalid", "no");
        chain(b, "sound", "before", "main", "after", "close");
        return b.build();
    }

    private static void commonViewTail(Builder b, int startRow,
                                       String afterId, String afterMethod) {
        b.step("broadcast", "Broadcast TableUpdatedEvent", "Notify other views",
                StepKind.SIDE_EFFECT, startRow, 0);
        b.step("refresh", "Refresh view", "Reload the selected row",
                StepKind.SIDE_EFFECT, startRow + 1, 0);
        b.step("notification", "Show success notification", "Visible UI feedback",
                StepKind.SIDE_EFFECT, startRow + 2, 0);
        b.method(afterId, "View." + afterMethod, "Post-action view hook", StepKind.HOOK,
                startRow + 3, 0, Owner.VIEW, afterMethod, 1);
        b.step("end", "Validation complete", "", StepKind.END, startRow + 4, 0);
    }

    private static void chain(Builder builder, String... ids) {
        for (int i = 1; i < ids.length; i++) {
            builder.edge(ids[i - 1], ids[i], "");
        }
    }

    private static final class Builder {
        private final LifecycleEvent event;
        private final List<Step> steps = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();

        private Builder(LifecycleEvent event) {
            this.event = event;
        }

        private void step(String id, String label, String detail, StepKind kind,
                          int row, int column) {
            steps.add(new Step(id, label, detail, kind, row, column, null));
        }

        private void method(String id, String label, String detail, StepKind kind,
                            int row, int column, Owner owner, String name, int parameterCount) {
            steps.add(new Step(id, label, detail, kind, row, column,
                    new MethodRef(owner, name, parameterCount)));
        }

        private void edge(String from, String to, String label) {
            edges.add(new Edge(from, to, label));
        }

        private LifecycleTemplate build() {
            return new LifecycleTemplate(event, List.copyOf(steps), List.copyOf(edges));
        }
    }
}
