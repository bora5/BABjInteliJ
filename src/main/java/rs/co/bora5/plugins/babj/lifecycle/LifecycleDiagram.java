package rs.co.bora5.plugins.babj.lifecycle;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;

/** A lifecycle template resolved against concrete project classes. */
public record LifecycleDiagram(@NotNull LifecycleEvent event,
                               @NotNull String contextName,
                               @NotNull List<Node> nodes,
                               @NotNull List<LifecycleTemplate.Edge> edges) {

    public enum Implementation {
        OVERRIDDEN,
        INHERITED,
        FRAMEWORK,
        NONE
    }

    public record Node(@NotNull String id,
                       @NotNull String label,
                       @NotNull String detail,
                       @NotNull LifecycleTemplate.StepKind kind,
                       int row,
                       int column,
                       @NotNull Implementation implementation,
                       @Nullable SmartPsiElementPointer<PsiMethod> target) {
    }

    @Override
    public String toString() {
        return event.getDisplayName();
    }
}
