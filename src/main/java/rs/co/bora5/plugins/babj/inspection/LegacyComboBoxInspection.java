package rs.co.bora5.plugins.babj.inspection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/** Offers a safe migration from hand-written lazy ComboBox providers to BAB factory methods. */
public class LegacyComboBoxInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method,
                                                       @NotNull InspectionManager manager,
                                                       boolean isOnTheFly) {
        LegacyComboBoxPattern.Match match = LegacyComboBoxPattern.detect(method);
        if (match == null) {
            return null;
        }
        PsiElement anchor = match.invocation().getMethodExpression().getReferenceNameElement();
        if (anchor == null) {
            return null;
        }
        String factory = match.kind().factoryMethod();
        ProblemDescriptor descriptor = manager.createProblemDescriptor(
                anchor,
                "Legacy ComboBox setup can be replaced with " + factory + "().",
                new SimplifyLegacyComboBoxQuickFix(match.kind()),
                ProblemHighlightType.WEAK_WARNING,
                isOnTheFly);
        return new ProblemDescriptor[]{descriptor};
    }
}
