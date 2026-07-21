package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/** Replaces a fully recognized legacy menu method with a role-aware MenuDefinition. */
public class MigrateLegacyMainMenuQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getName() {
        return "Replace with createMenuDefinition()";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Migrate BAB main menu";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class, false);
        LegacyMainMenuPattern.Match match = method == null ? null : LegacyMainMenuPattern.detect(method);
        if (match == null) {
            return;
        }
        PsiElement replacement = JavaPsiFacade.getElementFactory(project)
                .createMethodFromText(match.replacementMethod(), method);
        PsiReplacement.finish(project, method.replace(replacement));
    }
}
