package rs.co.bora5.plugins.babj.inspection;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes UI date/time patterns covered by BabDateFormats. */
final class DateFormatPattern {

    private static final Map<String, String> CONSTANTS = Map.of(
            "dd-MM-yyyy", "UI_DATE",
            "dd-MM-yyyy HH:mm", "UI_DATE_TIME",
            "dd-MM-yyyy HH:mm:ss", "UI_DATE_TIME_SECONDS",
            "HH:mm", "UI_TIME",
            "dd.MM.yyyy", "UI_DOTTED_DATE",
            "dd/MM/yyyy", "UI_SLASH_DATE",
            "dd/MM/yy HH:mm", "UI_SHORT_DATE_TIME");

    record Match(@NotNull PsiMethodCallExpression call, @NotNull String constant) {
    }

    private DateFormatPattern() {
    }

    static void addProblems(PsiMethod method, InspectionManager manager, boolean isOnTheFly,
                            List<ProblemDescriptor> problems) {
        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(method,
                PsiMethodCallExpression.class)) {
            Match match = detect(call);
            if (match == null) {
                continue;
            }
            PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
            if (anchor != null) {
                problems.add(manager.createProblemDescriptor(anchor,
                        "Shared BAB UI formatter " + match.constant() + " is available.",
                        new ReplaceDateFormatQuickFix(match.constant()),
                        ProblemHighlightType.WEAK_WARNING, isOnTheFly));
            }
        }
    }

    static @Nullable Match detect(PsiMethodCallExpression call) {
        if (!"ofPattern".equals(call.getMethodExpression().getReferenceName())) {
            return null;
        }
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        PsiMethod resolved = call.resolveMethod();
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        boolean formatterOwner = resolved != null && resolved.getContainingClass() != null
                && "java.time.format.DateTimeFormatter".equals(
                        resolved.getContainingClass().getQualifiedName());
        if (!formatterOwner && qualifier instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiClass owner) {
            formatterOwner = "java.time.format.DateTimeFormatter".equals(owner.getQualifiedName());
        }
        if (!formatterOwner && qualifier != null) {
            String qualifierText = qualifier.getText();
            formatterOwner = "java.time.format.DateTimeFormatter".equals(qualifierText)
                    || "DateTimeFormatter".equals(qualifierText) && hasFormatterImport(call);
        }
        if (!formatterOwner
                || arguments.length != 1 || !(arguments[0] instanceof PsiLiteralExpression literal)
                || !(literal.getValue() instanceof String pattern)) {
            return null;
        }
        String constant = CONSTANTS.get(pattern);
        return constant == null ? null : new Match(call, constant);
    }

    private static boolean hasFormatterImport(PsiMethodCallExpression call) {
        if (!(call.getContainingFile() instanceof PsiJavaFile javaFile)
                || javaFile.getImportList() == null) {
            return false;
        }
        return java.util.Arrays.stream(javaFile.getImportList().getImportStatements())
                .map(statement -> statement.getImportReference() == null ? null
                        : statement.getImportReference().getQualifiedName())
                .anyMatch("java.time.format.DateTimeFormatter"::equals);
    }

    private static final class ReplaceDateFormatQuickFix implements LocalQuickFix {
        private final String expectedConstant;

        private ReplaceDateFormatQuickFix(String expectedConstant) {
            this.expectedConstant = expectedConstant;
        }

        @Override
        public @NotNull String getName() {
            return "Replace with BabDateFormats." + expectedConstant;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use shared BAB date format";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiMethodCallExpression.class, false);
            Match match = call == null ? null : detect(call);
            if (match == null || !expectedConstant.equals(match.constant())) {
                return;
            }
            PsiReplacement.replaceExpression(project, call,
                    "rs.co.bora5.programs.bab.utils.BabDateFormats." + expectedConstant);
        }
    }
}
