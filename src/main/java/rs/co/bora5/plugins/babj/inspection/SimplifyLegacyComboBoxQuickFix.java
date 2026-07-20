package rs.co.bora5.plugins.babj.inspection;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

/** Replaces one fully recognized legacy ComboBox setup without touching custom behavior. */
public class SimplifyLegacyComboBoxQuickFix implements LocalQuickFix {

    private final LegacyComboBoxPattern.Kind expectedKind;

    SimplifyLegacyComboBoxQuickFix(LegacyComboBoxPattern.Kind expectedKind) {
        this.expectedKind = expectedKind;
    }

    @Override
    public @NotNull String getName() {
        return "Replace with " + expectedKind.factoryMethod() + "()";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Simplify BABj ComboBox setup";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethodCallExpression invocation = PsiTreeUtil.getParentOfType(
                descriptor.getPsiElement(), PsiMethodCallExpression.class, false);
        PsiMethod refreshMethod = invocation != null ? invocation.resolveMethod() : null;
        if (refreshMethod == null) {
            return;
        }
        LegacyComboBoxPattern.Match match = LegacyComboBoxPattern.detect(refreshMethod);
        if (match == null || match.kind() != expectedKind) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiStatement replacement = factory.createStatementFromText(
                match.replacementStatement(), match.invocationStatement());
        PsiElement inserted = match.invocationStatement().replace(replacement);
        match.labelStatement().delete();
        for (PsiExpressionStatement listener : match.removableListeners()) {
            listener.delete();
        }
        match.refreshMethod().delete();

        CodeStyleManager.getInstance(project).reformat(inserted);
        if (inserted.getContainingFile() instanceof PsiJavaFile javaFile) {
            JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
        }
    }
}
