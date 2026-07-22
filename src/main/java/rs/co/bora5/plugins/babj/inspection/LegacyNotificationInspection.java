package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Offers migration to BAB's generic persistent notification model and notification centre. */
public class LegacyNotificationInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass,
                                                      @NotNull InspectionManager manager,
                                                      boolean isOnTheFly) {
        LegacyNotificationPattern.Match match = LegacyNotificationPattern.detect(aClass);
        if (match == null) {
            return null;
        }
        PsiElement anchor = aClass.getNameIdentifier();
        if (anchor == null) {
            return null;
        }
        LocalQuickFix quickFix = match.automaticMigration()
                ? new MigrateLegacyNotificationQuickFix(match.kind()) : null;
        return new ProblemDescriptor[]{manager.createProblemDescriptor(
                anchor,
                message(match),
                quickFix,
                ProblemHighlightType.WEAK_WARNING,
                isOnTheFly)};
    }

    private static String message(LegacyNotificationPattern.Match match) {
        String base = switch (match.kind()) {
            case ENTITY -> "Legacy notification entity can inherit AbstractNotification.";
            case HOME -> "Legacy notification service can inherit AbstractNotificationHome.";
            case MAIN_VIEW -> "Custom notification bell can use BAB's generic notification centre.";
        };
        return match.automaticMigration() ? base
                : base + " Custom behavior requires manual migration.";
    }
}
