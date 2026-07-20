package rs.co.bora5.plugins.babj.inspection;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes ComboBox/MultiSelectComboBox setup populated directly from Enum.values(). */
final class EnumComboBoxPattern {

    record Match(@NotNull PsiNewExpression constructor,
                 @NotNull PsiExpressionStatement itemsStatement,
                 @Nullable PsiExpressionStatement labelStatement,
                 @Nullable PsiExpressionStatement labelGeneratorStatement,
                 @NotNull String factoryMethod,
                 @NotNull String labelExpression,
                 @NotNull String enumClassExpression,
                 @Nullable String labelGeneratorExpression) {

        String replacement() {
            return factoryMethod + "(" + labelExpression + ", " + enumClassExpression + ".class"
                    + (labelGeneratorExpression == null ? "" : ", " + labelGeneratorExpression)
                    + ")";
        }
    }

    private EnumComboBoxPattern() {
    }

    static void addProblems(PsiMethod method, InspectionManager manager, boolean isOnTheFly,
                            List<ProblemDescriptor> problems) {
        for (PsiNewExpression expression : PsiTreeUtil.findChildrenOfType(method,
                PsiNewExpression.class)) {
            Match match = detect(expression, method);
            if (match == null || expression.getClassReference() == null) {
                continue;
            }
            problems.add(manager.createProblemDescriptor(expression.getClassReference(),
                    "Enum values can use " + match.factoryMethod() + "().",
                    new ReplaceEnumComboBoxQuickFix(match.factoryMethod()),
                    ProblemHighlightType.WEAK_WARNING, isOnTheFly));
        }
    }

    static @Nullable Match detect(PsiNewExpression expression, PsiMethod method) {
        if (expression.getClassReference() == null || expression.getArgumentList() == null) {
            return null;
        }
        String type = expression.getClassReference().getReferenceName();
        String factory = switch (type) {
            case "ComboBox" -> "createEnumComboBox";
            case "MultiSelectComboBox" -> "createEnumMultiSelectComboBox";
            default -> null;
        };
        if (factory == null || method.getContainingClass() == null
                || method.getContainingClass().findMethodsByName(factory, true).length == 0) {
            return null;
        }

        PsiVariable variable = assignedVariable(expression);
        if (variable == null) {
            return null;
        }
        List<PsiMethodCallExpression> itemCalls = callsOn(method, variable, "setItems");
        if (itemCalls.size() != 1 || itemCalls.get(0).getArgumentList().getExpressions().length != 1
                || !(itemCalls.get(0).getParent() instanceof PsiExpressionStatement itemsStatement)) {
            return null;
        }
        PsiExpression items = itemCalls.get(0).getArgumentList().getExpressions()[0];
        if (!(items instanceof PsiMethodCallExpression valuesCall)
                || !"values".equals(valuesCall.getMethodExpression().getReferenceName())
                || valuesCall.getArgumentList().getExpressions().length != 0
                || valuesCall.getMethodExpression().getQualifierExpression() == null) {
            return null;
        }
        PsiMethod valuesMethod = valuesCall.resolveMethod();
        if (valuesMethod == null || valuesMethod.getContainingClass() == null
                || !valuesMethod.getContainingClass().isEnum()) {
            return null;
        }
        String enumClass = valuesCall.getMethodExpression().getQualifierExpression().getText();

        PsiExpression[] constructorArgs = expression.getArgumentList().getExpressions();
        String label;
        PsiExpressionStatement labelStatement = null;
        if (constructorArgs.length == 1) {
            label = constructorArgs[0].getText();
        } else if (constructorArgs.length == 0) {
            List<PsiMethodCallExpression> labelCalls = callsOn(method, variable, "setLabel");
            if (labelCalls.size() != 1 || labelCalls.get(0).getArgumentList().getExpressions().length != 1
                    || !(labelCalls.get(0).getParent() instanceof PsiExpressionStatement statement)) {
                return null;
            }
            label = labelCalls.get(0).getArgumentList().getExpressions()[0].getText();
            labelStatement = statement;
        } else {
            return null;
        }

        List<PsiMethodCallExpression> generators = callsOn(method, variable, "setItemLabelGenerator");
        if (generators.size() > 1) {
            return null;
        }
        PsiExpressionStatement generatorStatement = null;
        String generator = null;
        if (generators.size() == 1) {
            PsiMethodCallExpression call = generators.get(0);
            if (call.getArgumentList().getExpressions().length != 1
                    || !(call.getParent() instanceof PsiExpressionStatement statement)) {
                return null;
            }
            generator = call.getArgumentList().getExpressions()[0].getText();
            generatorStatement = statement;
        }
        return new Match(expression, itemsStatement, labelStatement, generatorStatement,
                factory, label, enumClass, generator);
    }

    private static @Nullable PsiVariable assignedVariable(PsiNewExpression expression) {
        if (expression.getParent() instanceof PsiLocalVariable local
                && expression.equals(local.getInitializer())) {
            return local;
        }
        if (expression.getParent() instanceof PsiAssignmentExpression assignment
                && expression.equals(assignment.getRExpression())) {
            return resolveVariable(assignment.getLExpression());
        }
        return null;
    }

    private static List<PsiMethodCallExpression> callsOn(PsiMethod method, PsiVariable variable,
                                                          String methodName) {
        return PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression.class).stream()
                .filter(call -> methodName.equals(call.getMethodExpression().getReferenceName()))
                .filter(call -> variable.equals(resolveVariable(
                        call.getMethodExpression().getQualifierExpression())))
                .toList();
    }

    private static @Nullable PsiVariable resolveVariable(@Nullable PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiVariable variable) {
            return variable;
        }
        return null;
    }

    private static final class ReplaceEnumComboBoxQuickFix implements LocalQuickFix {
        private final String expectedFactory;

        private ReplaceEnumComboBoxQuickFix(String expectedFactory) {
            this.expectedFactory = expectedFactory;
        }

        @Override
        public @NotNull String getName() {
            return "Replace with " + expectedFactory + "()";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Use BAB enum ComboBox factory";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiNewExpression expression = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiNewExpression.class, false);
            PsiMethod method = expression == null ? null
                    : PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            Match match = expression == null || method == null ? null : detect(expression, method);
            if (match == null || !expectedFactory.equals(match.factoryMethod())) {
                return;
            }
            match.itemsStatement().delete();
            if (match.labelStatement() != null) {
                match.labelStatement().delete();
            }
            if (match.labelGeneratorStatement() != null) {
                match.labelGeneratorStatement().delete();
            }
            PsiReplacement.replaceExpression(project, expression, match.replacement());
        }
    }
}
