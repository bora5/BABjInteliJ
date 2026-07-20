package rs.co.bora5.plugins.babj.inspection;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes standard themed notifications and immediately opened local confirmation dialogs. */
final class FeedbackPattern {

    private static final Map<String, String> NOTIFICATION_METHODS = Map.of(
            "LUMO_SUCCESS", "showSuccess",
            "LUMO_ERROR", "showError",
            "LUMO_CONTRAST", "showWarning",
            "LUMO_PRIMARY", "showInfo");

    record NotificationMatch(@NotNull PsiMethodCallExpression themedCall,
                             @NotNull String helperMethod,
                             @NotNull String messageExpression) {
    }

    record ConfirmMatch(@NotNull PsiNewExpression constructor,
                        @NotNull PsiDeclarationStatement declaration,
                        @NotNull PsiExpressionStatement openStatement,
                        @NotNull String arguments) {
    }

    private FeedbackPattern() {
    }

    static void addProblems(PsiMethod method, InspectionManager manager, boolean isOnTheFly,
                            List<ProblemDescriptor> problems) {
        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(method,
                PsiMethodCallExpression.class)) {
            if (PsiTreeUtil.getParentOfType(call, PsiMethod.class) != method) {
                continue;
            }
            NotificationMatch match = detectNotification(call);
            if (match == null) {
                continue;
            }
            PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
            if (anchor != null) {
                problems.add(manager.createProblemDescriptor(anchor,
                        "Standard notification can use NotificationFactory."
                                + match.helperMethod() + "().",
                        new ReplaceNotificationQuickFix(match.helperMethod()),
                        ProblemHighlightType.WEAK_WARNING, isOnTheFly));
            }
        }

        for (PsiNewExpression expression : PsiTreeUtil.findChildrenOfType(method,
                PsiNewExpression.class)) {
            ConfirmMatch match = detectConfirm(expression, method);
            if (match == null || expression.getClassReference() == null) {
                continue;
            }
            problems.add(manager.createProblemDescriptor(expression.getClassReference(),
                    "Local ConfirmDialog can be created and opened with ConfirmDialogs.open().",
                    new ReplaceConfirmQuickFix(), ProblemHighlightType.WEAK_WARNING, isOnTheFly));
        }
    }

    static @Nullable NotificationMatch detectNotification(PsiMethodCallExpression call) {
        if (!"addThemeVariants".equals(call.getMethodExpression().getReferenceName())) {
            return null;
        }
        PsiExpression[] variants = call.getArgumentList().getExpressions();
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (variants.length != 1 || !(qualifier instanceof PsiMethodCallExpression show)
                || !"show".equals(show.getMethodExpression().getReferenceName())
                || show.getArgumentList().getExpressions().length != 1) {
            return null;
        }
        PsiMethod resolvedShow = show.resolveMethod();
        if (resolvedShow == null || resolvedShow.getContainingClass() == null
                || !"com.vaadin.flow.component.notification.Notification"
                        .equals(resolvedShow.getContainingClass().getQualifiedName())) {
            return null;
        }
        String variant = variants[0] instanceof PsiReferenceExpression reference
                ? reference.getReferenceName() : null;
        String helper = NOTIFICATION_METHODS.get(variant);
        return helper == null ? null : new NotificationMatch(call, helper,
                show.getArgumentList().getExpressions()[0].getText());
    }

    static @Nullable ConfirmMatch detectConfirm(PsiNewExpression expression, PsiMethod method) {
        if (expression.getClassReference() == null
                || !(expression.getClassReference().resolve() instanceof PsiClass dialogClass)
                || !"com.vaadin.flow.component.confirmdialog.ConfirmDialog".equals(
                        dialogClass.getQualifiedName())
                || expression.getArgumentList() == null) {
            return null;
        }
        int count = expression.getArgumentList().getExpressions().length;
        if (count != 4 && count != 6 && count != 8) {
            return null;
        }
        if (!(expression.getParent() instanceof PsiLocalVariable local)
                || !(local.getParent() instanceof PsiDeclarationStatement declaration)) {
            return null;
        }
        List<PsiReferenceExpression> references = PsiTreeUtil
                .findChildrenOfType(method, PsiReferenceExpression.class).stream()
                .filter(reference -> local.equals(reference.resolve()))
                .toList();
        if (references.size() != 1
                || !(references.get(0).getParent() instanceof PsiReferenceExpression methodExpression)
                || !(methodExpression.getParent() instanceof PsiMethodCallExpression openCall)
                || !"open".equals(openCall.getMethodExpression().getReferenceName())
                || openCall.getArgumentList().getExpressions().length != 0
                || !(openCall.getParent() instanceof PsiExpressionStatement openStatement)) {
            return null;
        }
        String arguments = Arrays.stream(expression.getArgumentList().getExpressions())
                .map(PsiElement::getText).collect(Collectors.joining(", "));
        return new ConfirmMatch(expression, declaration, openStatement, arguments);
    }

    private static final class ReplaceNotificationQuickFix implements LocalQuickFix {
        private final String expectedMethod;

        private ReplaceNotificationQuickFix(String expectedMethod) {
            this.expectedMethod = expectedMethod;
        }

        @Override
        public @NotNull String getName() {
            return "Replace with NotificationFactory." + expectedMethod + "()";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use BAB feedback helper";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiMethodCallExpression.class, false);
            NotificationMatch match = call == null ? null : detectNotification(call);
            if (match == null || !expectedMethod.equals(match.helperMethod())) {
                return;
            }
            PsiReplacement.replaceExpression(project, call,
                    "rs.co.bora5.programs.bab.utils.NotificationFactory."
                            + expectedMethod + "(" + match.messageExpression() + ")");
        }
    }

    private static final class ReplaceConfirmQuickFix implements LocalQuickFix {

        @Override
        public @NotNull String getName() {
            return "Replace with ConfirmDialogs.open()";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use BAB feedback helper";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiNewExpression expression = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiNewExpression.class, false);
            PsiMethod method = expression == null ? null
                    : PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            ConfirmMatch match = expression == null || method == null
                    ? null : detectConfirm(expression, method);
            if (match == null) {
                return;
            }
            match.openStatement().delete();
            PsiReplacement.replaceStatement(project, match.declaration(),
                    "rs.co.bora5.programs.bab.utils.ConfirmDialogs.open("
                            + match.arguments() + ");");
        }
    }
}
