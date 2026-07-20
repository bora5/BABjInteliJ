package rs.co.bora5.plugins.babj.lifecycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;

import rs.co.bora5.plugins.babj.lifecycle.LifecycleDiagram.Implementation;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.MethodRef;
import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.Owner;
import rs.co.bora5.plugins.babj.navigation.BABjArtifactResolver;
import rs.co.bora5.plugins.babj.navigation.BABjArtifactRole;

/** Resolves known BAB lifecycles and hook implementations for one concrete project class. */
public final class BABjLifecycleResolver {

    private static final String BAB_FRAMEWORK_PACKAGE = "rs.co.bora5.programs.bab.";
    static final String GENERIC_REPORT_WINDOW =
            "rs.co.bora5.programs.bab.front.windowses.GenericReportWindow";
    static final String CHECK_VALIDATE_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckValidateHomeInterface";
    static final String MULTI_VALIDATE_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.MultiValidateHomeInterface";
    static final String CHECK_STORNO_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckStornoHomeInterface";
    static final String CHECK_ARRIVED_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckArrivedHomeInterface";
    static final String CHECK_INVALIDATE_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckInvalidateHomeInterface";
    static final String CHECK_PAYOUT_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckPayoutHomeInterface";
    static final String CHECK_UNPAYOUT_HOME =
            "rs.co.bora5.programs.bab.session.interfaceCheck.CheckUnpayoutHomeInterface";

    private BABjLifecycleResolver() {
    }

    public static @NotNull ScanResult resolve(@Nullable PsiClass selectedClass) {
        if (selectedClass == null) {
            return ScanResult.none();
        }
        Context context = contextFor(selectedClass);
        List<LifecycleEvent> events = availableEvents(context);
        if (events.isEmpty()) {
            return new ScanResult(selectedClass.getName(), List.of());
        }

        List<LifecycleDiagram> diagrams = events.stream()
                .map(event -> resolveDiagram(context, LifecycleCatalog.template(event)))
                .toList();
        return new ScanResult(displayName(selectedClass), diagrams);
    }

    private static Context contextFor(PsiClass selectedClass) {
        Map<BABjArtifactRole, PsiClass> artifacts = new EnumMap<>(BABjArtifactRole.class);
        BABjArtifactRole selectedRole = BABjArtifactResolver.roleOf(selectedClass);
        if (selectedRole != null) {
            artifacts.put(selectedRole, selectedClass);
            for (BABjArtifactResolver.Artifact artifact
                    : BABjArtifactResolver.relatedArtifacts(selectedClass)) {
                artifacts.putIfAbsent(artifact.role(), artifact.psiClass());
            }
        }

        PsiClass view = artifacts.get(BABjArtifactRole.VIEW);
        PsiClass home = artifacts.get(BABjArtifactRole.HOME);
        PsiClass window = artifacts.get(BABjArtifactRole.WINDOW);

        if (view != null && home == null) {
            home = BABjArtifactResolver.typeArgument(view, BABjArtifactResolver.GENERIC_VIEW, 1);
        }
        if (window != null && home == null) {
            home = BABjArtifactResolver.typeArgument(window, BABjArtifactResolver.GENERIC_WINDOW, 1);
        }

        PsiClass report = inherits(selectedClass, GENERIC_REPORT_WINDOW) ? selectedClass : null;
        return new Context(selectedClass, view, home, window, report);
    }

    private static List<LifecycleEvent> availableEvents(Context context) {
        List<LifecycleEvent> result = new ArrayList<>();
        if (context.view() != null) {
            if (inherits(context.home(), CHECK_VALIDATE_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.VALIDATE)) {
                result.add(LifecycleEvent.VALIDATE);
            }
            if (inherits(context.home(), MULTI_VALIDATE_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.MULTI_VALIDATE)) {
                result.add(LifecycleEvent.MULTI_VALIDATE);
            }
            if (inherits(context.home(), CHECK_STORNO_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.STORNO)) {
                result.add(LifecycleEvent.STORNO);
            }
            if (inherits(context.home(), CHECK_ARRIVED_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.ARRIVED)) {
                result.add(LifecycleEvent.ARRIVED);
            }
            if (inherits(context.home(), CHECK_INVALIDATE_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.INVALIDATE)) {
                result.add(LifecycleEvent.INVALIDATE);
            }
            if (inherits(context.home(), CHECK_PAYOUT_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.PAYOUT)) {
                result.add(LifecycleEvent.PAYOUT);
            }
            if (inherits(context.home(), CHECK_UNPAYOUT_HOME)
                    && hasNonEmptyHook(context, LifecycleEvent.UNPAYOUT)) {
                result.add(LifecycleEvent.UNPAYOUT);
            }
            if (hasNonEmptyHook(context, LifecycleEvent.DELETE)) {
                result.add(LifecycleEvent.DELETE);
            }
        }
        if (context.window() != null) {
            if (hasNonEmptyHook(context, LifecycleEvent.CREATE)) {
                result.add(LifecycleEvent.CREATE);
            }
            if (hasNonEmptyHook(context, LifecycleEvent.EDIT)) {
                result.add(LifecycleEvent.EDIT);
            }
        }
        if (context.report() != null
                && hasNonEmptyHook(context, LifecycleEvent.REPORT_EXECUTE)) {
            result.add(LifecycleEvent.REPORT_EXECUTE);
        }
        return List.copyOf(result);
    }

    private static boolean hasNonEmptyHook(Context context, LifecycleEvent event) {
        LifecycleTemplate template = LifecycleCatalog.template(event);
        return template.steps().stream()
                .filter(step -> step.kind() == LifecycleTemplate.StepKind.HOOK)
                .map(LifecycleTemplate.Step::method)
                .filter(java.util.Objects::nonNull)
                .anyMatch(reference -> hasProjectImplementationWithBody(
                        owner(context, reference.owner()), reference));
    }

    private static boolean hasProjectImplementationWithBody(@Nullable PsiClass owner,
                                                             MethodRef reference) {
        if (owner == null) {
            return false;
        }
        ProjectFileIndex projectFiles = ProjectFileIndex.getInstance(owner.getProject());
        return java.util.Arrays.stream(owner.findMethodsByName(reference.name(), true))
                .filter(method -> method.getParameterList().getParametersCount()
                        == reference.parameterCount())
                .filter(method -> method.getContainingClass() != null
                        && (method.getContainingClass().getQualifiedName() == null
                        || !method.getContainingClass().getQualifiedName()
                        .startsWith(BAB_FRAMEWORK_PACKAGE)))
                .filter(method -> method.getContainingFile() != null
                        && method.getContainingFile().getVirtualFile() != null
                        && projectFiles.isInContent(method.getContainingFile().getVirtualFile()))
                .map(PsiMethod::getBody)
                .filter(java.util.Objects::nonNull)
                .anyMatch(body -> body.getStatements().length > 0);
    }

    private static LifecycleDiagram resolveDiagram(Context context, LifecycleTemplate template) {
        List<LifecycleDiagram.Node> nodes = template.steps().stream()
                .map(step -> resolveNode(context, step))
                .toList();
        return new LifecycleDiagram(template.event(), displayName(context.selected()),
                nodes, template.edges());
    }

    private static LifecycleDiagram.Node resolveNode(Context context,
                                                      LifecycleTemplate.Step step) {
        MethodRef reference = step.method();
        if (reference == null) {
            return new LifecycleDiagram.Node(step.id(), step.label(), step.detail(), step.kind(),
                    step.row(), step.column(), Implementation.NONE, null);
        }

        PsiClass concreteOwner = owner(context, reference.owner());
        PsiMethod method = findMethod(concreteOwner, reference.name(), reference.parameterCount());
        if (method == null) {
            PsiClass frameworkOwner = frameworkOwner(context, reference.owner());
            method = findMethod(frameworkOwner, reference.name(), reference.parameterCount());
        }
        if (method == null) {
            return new LifecycleDiagram.Node(step.id(), step.label(),
                    step.detail() + " — implementation not found", step.kind(), step.row(),
                    step.column(), Implementation.NONE, null);
        }

        PsiClass declaringClass = method.getContainingClass();
        boolean overridden = concreteOwner != null && declaringClass != null
                && concreteOwner.isEquivalentTo(declaringClass);
        Implementation implementation = step.kind() == LifecycleTemplate.StepKind.HOOK
                ? overridden ? Implementation.OVERRIDDEN : Implementation.INHERITED
                : Implementation.FRAMEWORK;
        String ownerName = declaringClass == null ? null : declaringClass.getName();
        String suffix = ownerName == null ? "" : implementation == Implementation.OVERRIDDEN
                ? " — overridden in " + ownerName : " — inherited from " + ownerName;
        SmartPsiElementPointer<PsiMethod> pointer = SmartPointerManager
                .getInstance(method.getProject()).createSmartPsiElementPointer(method);
        return new LifecycleDiagram.Node(step.id(), step.label(), step.detail() + suffix,
                step.kind(), step.row(), step.column(), implementation, pointer);
    }

    private static @Nullable PsiClass owner(Context context, Owner owner) {
        return switch (owner) {
            case VIEW -> context.view();
            case HOME -> context.home();
            case WINDOW -> context.window();
            case REPORT -> context.report();
            case NONE -> null;
        };
    }

    private static @Nullable PsiClass frameworkOwner(Context context, Owner owner) {
        String fqn = switch (owner) {
            case VIEW -> BABjArtifactResolver.GENERIC_VIEW;
            case WINDOW -> BABjArtifactResolver.GENERIC_WINDOW;
            case REPORT -> GENERIC_REPORT_WINDOW;
            case HOME, NONE -> null;
        };
        if (fqn == null) {
            return null;
        }
        return JavaPsiFacade.getInstance(context.selected().getProject())
                .findClass(fqn, GlobalSearchScope.allScope(context.selected().getProject()));
    }

    private static @Nullable PsiMethod findMethod(@Nullable PsiClass owner, String name,
                                                  int parameterCount) {
        if (owner == null) {
            return null;
        }
        return java.util.Arrays.stream(owner.findMethodsByName(name, true))
                .filter(method -> method.getParameterList().getParametersCount() == parameterCount)
                .sorted(Comparator.comparing(method -> !owner.isEquivalentTo(
                        method.getContainingClass())))
                .findFirst()
                .orElse(null);
    }

    private static boolean inherits(@Nullable PsiClass type, String baseFqn) {
        return type != null && (baseFqn.equals(type.getQualifiedName())
                || InheritanceUtil.isInheritor(type, baseFqn));
    }

    private static String displayName(PsiClass psiClass) {
        return psiClass.getName() == null ? "anonymous BAB class" : psiClass.getName();
    }

    private record Context(@NotNull PsiClass selected,
                           @Nullable PsiClass view,
                           @Nullable PsiClass home,
                           @Nullable PsiClass window,
                           @Nullable PsiClass report) {
    }

    public record ScanResult(@Nullable String contextName,
                             @NotNull List<LifecycleDiagram> diagrams) {
        public static ScanResult none() {
            return new ScanResult(null, List.of());
        }

        public boolean found() {
            return contextName != null && !diagrams.isEmpty();
        }
    }
}
