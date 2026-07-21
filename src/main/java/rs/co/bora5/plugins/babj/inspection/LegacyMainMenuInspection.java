package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Offers migration from imperative FavTab role switches to BAB MenuDefinition. */
public class LegacyMainMenuInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method,
                                                       @NotNull InspectionManager manager,
                                                       boolean isOnTheFly) {
        LegacyMainMenuPattern.Match match = LegacyMainMenuPattern.detect(method);
        if (match == null) {
            return null;
        }
        PsiElement anchor = method.getNameIdentifier();
        if (anchor == null) {
            return null;
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(
                anchor,
                "Legacy BAB menu can be replaced with a declarative MenuDefinition.",
                new MigrateLegacyMainMenuQuickFix(),
                ProblemHighlightType.WEAK_WARNING,
                isOnTheFly)};
    }
}
