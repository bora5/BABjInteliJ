package rs.co.bora5.plugins.babj.inspection;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes the common CompletionStage + nested UI.access + re-enable chain. */
final class UiTaskPattern {

    record Match(@NotNull PsiMethodCallExpression whenComplete,
                 @NotNull PsiExpressionStatement chainStatement,
                 @Nullable PsiExpressionStatement disableStatement,
                 @NotNull String stageExpression,
                 @NotNull String triggerExpression,
                 @NotNull String successLambda,
                 @NotNull String failureLambda) {

        String replacement() {
            return "handleUiTask(" + stageExpression + ", " + triggerExpression + ", "
                    + successLambda + ", " + failureLambda + ");";
        }
    }

    private UiTaskPattern() {
    }

    static void addProblems(PsiMethod method, InspectionManager manager, boolean isOnTheFly,
                            List<ProblemDescriptor> problems) {
        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(method,
                PsiMethodCallExpression.class)) {
            if (!"whenComplete".equals(call.getMethodExpression().getReferenceName())) {
                continue;
            }
            Match match = detect(call, method);
            if (match == null) {
                continue;
            }
            PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
            if (anchor != null) {
                problems.add(manager.createProblemDescriptor(anchor,
                        "Asynchronous UI callbacks can use handleUiTask().",
                        new ReplaceUiTaskQuickFix(), ProblemHighlightType.WEAK_WARNING,
                        isOnTheFly));
            }
        }
    }

    static @Nullable Match detect(PsiMethodCallExpression whenComplete, PsiMethod ownerMethod) {
        if (ownerMethod.getContainingClass() == null
                || ownerMethod.getContainingClass().findMethodsByName("handleUiTask", true).length == 0
                || whenComplete.getArgumentList().getExpressions().length != 1
                || !(whenComplete.getArgumentList().getExpressions()[0]
                        instanceof PsiLambdaExpression completionLambda)
                || !(whenComplete.getParent() instanceof PsiExpressionStatement chainStatement)) {
            return null;
        }
        PsiMethodCallExpression exceptionally = qualifierCall(whenComplete, "exceptionally");
        PsiMethodCallExpression thenAccept = exceptionally == null
                ? null : qualifierCall(exceptionally, "thenAccept");
        if (exceptionally == null || thenAccept == null
                || exceptionally.getArgumentList().getExpressions().length != 1
                || thenAccept.getArgumentList().getExpressions().length != 1
                || !(exceptionally.getArgumentList().getExpressions()[0]
                        instanceof PsiLambdaExpression failureLambda)
                || !(thenAccept.getArgumentList().getExpressions()[0]
                        instanceof PsiLambdaExpression successLambda)) {
            return null;
        }
        PsiExpression stage = thenAccept.getMethodExpression().getQualifierExpression();
        String successBody = accessBody(successLambda, false);
        String failureBody = accessBody(failureLambda, true);
        String trigger = enabledTrigger(completionLambda);
        if (stage == null || successBody == null || failureBody == null || trigger == null) {
            return null;
        }

        PsiExpressionStatement disable = previousDisable(chainStatement, trigger);
        return new Match(whenComplete, chainStatement, disable, stage.getText(), trigger,
                lambdaText(successLambda, successBody), lambdaText(failureLambda, failureBody));
    }

    private static @Nullable PsiMethodCallExpression qualifierCall(PsiMethodCallExpression call,
                                                                    String expectedName) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiMethodCallExpression result)
                || !expectedName.equals(result.getMethodExpression().getReferenceName())) {
            return null;
        }
        return result;
    }

    private static @Nullable String accessBody(PsiLambdaExpression outer, boolean failure) {
        if (failure) {
            if (!(outer.getBody() instanceof PsiCodeBlock block)
                    || block.getStatements().length != 2
                    || !(block.getStatements()[1] instanceof PsiReturnStatement returned)
                    || !(returned.getReturnValue() instanceof PsiLiteralExpression literal)
                    || literal.getValue() != null) {
                return null;
            }
        } else if (outer.getBody() instanceof PsiCodeBlock block
                && block.getStatements().length != 1) {
            return null;
        }

        List<PsiMethodCallExpression> accessCalls = PsiTreeUtil
                .findChildrenOfType(outer, PsiMethodCallExpression.class).stream()
                .filter(call -> "access".equals(call.getMethodExpression().getReferenceName()))
                .filter(call -> call.getArgumentList().getExpressions().length == 1
                        && call.getArgumentList().getExpressions()[0] instanceof PsiLambdaExpression)
                .toList();
        if (accessCalls.size() != 1) {
            return null;
        }
        PsiLambdaExpression inner = (PsiLambdaExpression) accessCalls.get(0)
                .getArgumentList().getExpressions()[0];
        return inner.getBody() == null ? null : inner.getBody().getText();
    }

    private static @Nullable String enabledTrigger(PsiLambdaExpression completion) {
        String body = accessBody(completion, false);
        if (body == null) {
            return null;
        }
        PsiMethodCallExpression enabled = PsiTreeUtil
                .findChildrenOfType(completion, PsiMethodCallExpression.class).stream()
                .filter(call -> "setEnabled".equals(call.getMethodExpression().getReferenceName()))
                .findFirst().orElse(null);
        if (enabled == null || enabled.getArgumentList().getExpressions().length != 1
                || !(enabled.getArgumentList().getExpressions()[0] instanceof PsiLiteralExpression literal)
                || !Boolean.TRUE.equals(literal.getValue())
                || enabled.getMethodExpression().getQualifierExpression() == null) {
            return null;
        }
        String trigger = enabled.getMethodExpression().getQualifierExpression().getText();
        String compactBody = body.replaceAll("[\\s{};]", "");
        String compactExpected = enabled.getText().replaceAll("[\\s{};]", "");
        return compactBody.equals(compactExpected) ? trigger : null;
    }

    private static @Nullable PsiExpressionStatement previousDisable(PsiExpressionStatement chain,
                                                                     String trigger) {
        if (!(chain.getParent() instanceof PsiCodeBlock block)) {
            return null;
        }
        PsiStatement[] statements = block.getStatements();
        for (int i = 1; i < statements.length; i++) {
            if (statements[i] != chain || !(statements[i - 1] instanceof PsiExpressionStatement previous)
                    || !(previous.getExpression() instanceof PsiMethodCallExpression call)
                    || !"setEnabled".equals(call.getMethodExpression().getReferenceName())
                    || call.getMethodExpression().getQualifierExpression() == null
                    || !trigger.equals(call.getMethodExpression().getQualifierExpression().getText())
                    || call.getArgumentList().getExpressions().length != 1
                    || !(call.getArgumentList().getExpressions()[0] instanceof PsiLiteralExpression literal)
                    || !Boolean.FALSE.equals(literal.getValue())) {
                continue;
            }
            return previous;
        }
        return null;
    }

    private static String lambdaText(PsiLambdaExpression original, String body) {
        return original.getParameterList().getText() + " -> " + body;
    }

    private static final class ReplaceUiTaskQuickFix implements LocalQuickFix {

        @Override
        public @NotNull String getName() {
            return "Replace with handleUiTask()";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use BAB asynchronous UI helper";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiMethodCallExpression.class, false);
            PsiMethod method = call == null ? null : PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            Match match = call == null || method == null ? null : detect(call, method);
            if (match == null) {
                return;
            }
            if (match.disableStatement() != null) {
                match.disableStatement().delete();
            }
            PsiReplacement.replaceStatement(project, match.chainStatement(), match.replacement());
        }
    }
}
