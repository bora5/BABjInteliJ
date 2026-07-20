package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiMethod;

/** Finds common hand-written code that can be expressed by BAB 1.7 helper APIs. */
public class CommonBabPatternsInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method,
                                                       @NotNull InspectionManager manager,
                                                       boolean isOnTheFly) {
        List<ProblemDescriptor> problems = new ArrayList<>();
        QueryResultPattern.addProblems(method, manager, isOnTheFly, problems);
        UiTaskPattern.addProblems(method, manager, isOnTheFly, problems);
        FeedbackPattern.addProblems(method, manager, isOnTheFly, problems);
        EnumComboBoxPattern.addProblems(method, manager, isOnTheFly, problems);
        DateFormatPattern.addProblems(method, manager, isOnTheFly, problems);
        return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor[]::new);
    }
}
