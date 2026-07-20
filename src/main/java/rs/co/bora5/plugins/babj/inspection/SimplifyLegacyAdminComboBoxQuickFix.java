package rs.co.bora5.plugins.babj.inspection;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

/** Replaces a fully recognized admin-only add-button layout with the BABj helper. */
public class SimplifyLegacyAdminComboBoxQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getName() {
        return "Replace admin ComboBox wrappers with comboWithAddButton()";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Simplify BABj ComboBox setup";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
        LegacyAdminComboBoxPattern.Match match = method != null
                ? LegacyAdminComboBoxPattern.detect(method) : null;
        if (match == null || !(match.setupIf().getParent() instanceof PsiCodeBlock block)) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiElement firstInserted = null;
        PsiElement previous = null;
        for (LegacyAdminComboBoxPattern.Item item : match.items()) {
            PsiStatement statement = factory.createStatementFromText(
                    item.replacementStatement(), match.setupIf());
            previous = previous == null
                    ? block.addBefore(statement, match.setupIf())
                    : block.addAfter(statement, previous);
            if (firstInserted == null) {
                firstInserted = previous;
            }
        }
        match.setupIf().delete();

        previous = null;
        for (String displayStatement : match.displayStatements()) {
            PsiStatement statement = factory.createStatementFromText(
                    displayStatement, match.displayIf());
            previous = previous == null
                    ? block.addBefore(statement, match.displayIf())
                    : block.addAfter(statement, previous);
        }
        match.displayIf().delete();

        for (LegacyAdminComboBoxPattern.Item item : match.items()) {
            deleteField(item.buttonField());
            deleteField(item.wrapperField());
        }

        PsiElement reformatTarget = firstInserted != null ? firstInserted : match.method();
        CodeStyleManager.getInstance(project).reformat(reformatTarget.getParent());
        if (match.method().getContainingFile() instanceof PsiJavaFile javaFile) {
            JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
        }
    }

    private static void deleteField(PsiField field) {
        if (field.isValid()) {
            field.delete();
        }
    }
}
