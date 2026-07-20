package rs.co.bora5.plugins.babj.inspection;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

/** Recognizes a pure getSingleResult try/catch that only supplies a missing-row fallback. */
final class QueryResultPattern {

    record Match(@NotNull PsiTryStatement tryStatement,
                 @NotNull PsiMethodCallExpression getSingleResult,
                 @NotNull String queryExpression,
                 @NotNull String fallbackExpression) {

        boolean nullFallback() {
            return "null".equals(fallbackExpression);
        }

        String helperName() {
            return nullFallback() ? "resultOrNull" : "resultOrDefault";
        }

        String replacement() {
            return "return " + helperName() + "(" + queryExpression
                    + (nullFallback() ? "" : ", " + fallbackExpression) + ");";
        }
    }

    private QueryResultPattern() {
    }

    static void addProblems(PsiMethod method, InspectionManager manager, boolean isOnTheFly,
                            List<ProblemDescriptor> problems) {
        for (PsiTryStatement statement : PsiTreeUtil.findChildrenOfType(method, PsiTryStatement.class)) {
            if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != method) {
                continue;
            }
            Match match = detect(statement);
            if (match == null) {
                continue;
            }
            PsiElement anchor = match.getSingleResult().getMethodExpression().getReferenceNameElement();
            if (anchor != null) {
                problems.add(manager.createProblemDescriptor(anchor,
                        "A missing query result can use " + match.helperName()
                                + "() without hiding other persistence errors.",
                        new ReplaceQueryResultQuickFix(match.nullFallback()),
                        ProblemHighlightType.WEAK_WARNING, isOnTheFly));
            }
        }
    }

    static @Nullable Match detect(PsiTryStatement statement) {
        if (statement.getResourceList() != null || statement.getFinallyBlock() != null) {
            return null;
        }
        PsiCodeBlock tryBlock = statement.getTryBlock();
        PsiCatchSection[] catches = statement.getCatchSections();
        if (tryBlock == null || tryBlock.getStatements().length != 1 || catches.length != 1
                || !(tryBlock.getStatements()[0] instanceof PsiReturnStatement tryReturn)) {
            return null;
        }
        PsiMethodCallExpression resultCall = directCall(tryReturn);
        if (resultCall == null
                || !"getSingleResult".equals(resultCall.getMethodExpression().getReferenceName())
                || resultCall.getArgumentList().getExpressions().length != 0) {
            return null;
        }
        PsiElement query = resultCall.getMethodExpression().getQualifierExpression();
        if (query == null || !isTypedQuery(resultCall)) {
            return null;
        }

        PsiCatchSection section = catches[0];
        PsiCodeBlock catchBlock = section.getCatchBlock();
        PsiType caught = section.getCatchType();
        if (catchBlock == null || caught == null || catchBlock.getStatements().length != 1
                || !(catchBlock.getStatements()[0] instanceof PsiReturnStatement catchReturn)
                || catchReturn.getReturnValue() == null || !isSupportedCatch(caught)) {
            return null;
        }
        return new Match(statement, resultCall, query.getText(), catchReturn.getReturnValue().getText());
    }

    private static @Nullable PsiMethodCallExpression directCall(PsiReturnStatement statement) {
        return statement.getReturnValue() instanceof PsiMethodCallExpression call ? call : null;
    }

    private static boolean isSupportedCatch(PsiType type) {
        String text = type.getCanonicalText();
        return "java.lang.Exception".equals(text)
                || "Exception".equals(type.getPresentableText())
                || "jakarta.persistence.NoResultException".equals(text)
                || "NoResultException".equals(type.getPresentableText());
    }

    private static boolean isTypedQuery(PsiMethodCallExpression resultCall) {
        PsiType type = resultCall.getMethodExpression().getQualifierExpression() == null
                ? null : resultCall.getMethodExpression().getQualifierExpression().getType();
        if (type == null) {
            return declaringClassIsTypedQuery(resultCall);
        }
        var psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        return psiClass != null && ("jakarta.persistence.TypedQuery".equals(psiClass.getQualifiedName())
                || InheritanceUtil.isInheritor(psiClass, "jakarta.persistence.TypedQuery"))
                || declaringClassIsTypedQuery(resultCall);
    }

    private static boolean declaringClassIsTypedQuery(PsiMethodCallExpression resultCall) {
        PsiMethod resolved = resultCall.resolveMethod();
        if (resolved == null || resolved.getContainingClass() == null) {
            return false;
        }
        var owner = resolved.getContainingClass();
        return "jakarta.persistence.TypedQuery".equals(owner.getQualifiedName())
                || InheritanceUtil.isInheritor(owner, "jakarta.persistence.TypedQuery");
    }

    private static final class ReplaceQueryResultQuickFix implements LocalQuickFix {
        private final boolean nullFallback;

        private ReplaceQueryResultQuickFix(boolean nullFallback) {
            this.nullFallback = nullFallback;
        }

        @Override
        public @NotNull String getName() {
            return "Replace with " + (nullFallback ? "resultOrNull()" : "resultOrDefault()");
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use strict BAB query result helper";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiTryStatement statement = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiTryStatement.class, false);
            Match match = statement == null ? null : detect(statement);
            if (match == null || match.nullFallback() != nullFallback) {
                return;
            }
            PsiReplacement.replaceStatement(project, statement, match.replacement());
        }
    }
}
