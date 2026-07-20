package rs.co.bora5.plugins.babj.lifecycle;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PSI-independent description of a BAB lifecycle graph. */
public record LifecycleTemplate(@NotNull LifecycleEvent event,
                                @NotNull List<Step> steps,
                                @NotNull List<Edge> edges) {

    public enum StepKind {
        START,
        ACTION,
        HOOK,
        DECISION,
        SIDE_EFFECT,
        STOP,
        END
    }

    public enum Owner {
        VIEW,
        HOME,
        WINDOW,
        REPORT,
        NONE
    }

    public record MethodRef(@NotNull Owner owner, @NotNull String name, int parameterCount) {
    }

    public record Step(@NotNull String id,
                       @NotNull String label,
                       @NotNull String detail,
                       @NotNull StepKind kind,
                       int row,
                       int column,
                       @Nullable MethodRef method) {
    }

    public record Edge(@NotNull String from, @NotNull String to,
                       @NotNull String label) {
    }
}
