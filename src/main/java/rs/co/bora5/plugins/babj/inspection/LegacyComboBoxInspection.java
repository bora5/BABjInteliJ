package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.List;

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
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        LegacyComboBoxPattern.Match match = LegacyComboBoxPattern.detect(method);
        if (match != null) {
            PsiElement anchor = match.invocation().getMethodExpression().getReferenceNameElement();
            if (anchor != null) {
                String factory = match.kind().factoryMethod();
                descriptors.add(manager.createProblemDescriptor(
                        anchor,
                        "Legacy ComboBox setup can be replaced with " + factory + "().",
                        new SimplifyLegacyComboBoxQuickFix(match.kind()),
                        ProblemHighlightType.WEAK_WARNING,
                        isOnTheFly));
            }
        }

        LegacyAdminComboBoxPattern.Match adminMatch = LegacyAdminComboBoxPattern.detect(method);
        if (adminMatch != null) {
            PsiElement anchor = adminMatch.setupIf().getFirstChild();
            descriptors.add(manager.createProblemDescriptor(
                    anchor,
                    "Admin-only ComboBox add buttons can be replaced with "
                            + "comboWithAddButton().",
                    new SimplifyLegacyAdminComboBoxQuickFix(),
                    ProblemHighlightType.WEAK_WARNING,
                    isOnTheFly));
        }
        return descriptors.isEmpty() ? null : descriptors.toArray(ProblemDescriptor[]::new);
    }
}
