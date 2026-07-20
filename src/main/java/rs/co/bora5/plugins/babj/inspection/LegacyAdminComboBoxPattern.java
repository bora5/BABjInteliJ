package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes the old admin-only ComboBox add-button layout idiom. */
final class LegacyAdminComboBoxPattern {

    record Item(@NotNull PsiField wrapperField,
                @NotNull PsiField buttonField,
                @NotNull PsiField comboField,
                @NotNull String comboExpression,
                @NotNull String callbackExpression) {

        String replacementStatement() {
            String type = wrapperField.getTypeElement() != null
                    ? wrapperField.getTypeElement().getText()
                    : "HorizontalLayout";
            return type + " " + wrapperField.getName() + " = comboWithAddButton("
                    + comboExpression + ", " + callbackExpression + ");";
        }
    }

    record Match(@NotNull PsiMethod method,
                 @NotNull PsiIfStatement setupIf,
                 @NotNull PsiIfStatement displayIf,
                 @NotNull List<Item> items,
                 @NotNull List<String> displayStatements) {
    }

    private static final class WrapperBuilder {
        private final PsiField field;
        private boolean spacing;
        private PsiField comboField;
        private String comboExpression;
        private PsiField buttonField;

        private WrapperBuilder(PsiField field) {
            this.field = field;
        }
    }

    private static final class ButtonBuilder {
        private final PsiField field;
        private boolean icon;
        private String callbackExpression;

        private ButtonBuilder(PsiField field) {
            this.field = field;
        }
    }

    private record AddCall(@NotNull String targetExpression,
                           @NotNull PsiField argument) {
    }

    private LegacyAdminComboBoxPattern() {
    }

    static @Nullable Match detect(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        PsiClass owner = method.getContainingClass();
        if (body == null || owner == null
                || owner.findMethodsByName("comboWithAddButton", true).length == 0) {
            return null;
        }

        PsiStatement[] statements = body.getStatements();
        for (int setupIndex = 0; setupIndex < statements.length; setupIndex++) {
            if (!(statements[setupIndex] instanceof PsiIfStatement setupIf)
                    || !isAdminCondition(setupIf.getCondition())
                    || setupIf.getElseBranch() != null) {
                continue;
            }
            List<Item> items = parseSetup(setupIf);
            if (items.isEmpty()) {
                continue;
            }
            for (int displayIndex = setupIndex + 1; displayIndex < statements.length;
                 displayIndex++) {
                if (!(statements[displayIndex] instanceof PsiIfStatement displayIf)
                        || !isAdminCondition(displayIf.getCondition())) {
                    continue;
                }
                List<String> displayStatements = parseDisplay(displayIf, items);
                if (displayStatements == null
                        || hasExternalFieldReferences(owner, setupIf, displayIf, items)) {
                    continue;
                }
                return new Match(method, setupIf, displayIf, List.copyOf(items),
                        List.copyOf(displayStatements));
            }
        }
        return null;
    }

    private static @NotNull List<Item> parseSetup(PsiIfStatement setupIf) {
        List<PsiStatement> statements = branchStatements(setupIf.getThenBranch());
        if (statements.isEmpty()) {
            return List.of();
        }

        Map<PsiField, WrapperBuilder> wrappers = new LinkedHashMap<>();
        Map<PsiField, ButtonBuilder> buttons = new LinkedHashMap<>();
        for (PsiStatement statement : statements) {
            if (!(statement instanceof PsiExpressionStatement expressionStatement)
                    || !(expressionStatement.getExpression()
                    instanceof PsiAssignmentExpression assignment)
                    || assignment.getOperationTokenType() != JavaTokenType.EQ
                    || !(resolveVariable(assignment.getLExpression()) instanceof PsiField field)
                    || !(unwrap(assignment.getRExpression()) instanceof PsiNewExpression created)
                    || created.getClassReference() == null
                    || created.getArgumentList() == null
                    || created.getArgumentList().getExpressions().length != 0) {
                continue;
            }
            String className = created.getClassReference().getReferenceName();
            if ("HorizontalLayout".equals(className)) {
                if (wrappers.put(field, new WrapperBuilder(field)) != null) {
                    return List.of();
                }
            } else if ("Button".equals(className)) {
                if (buttons.put(field, new ButtonBuilder(field)) != null) {
                    return List.of();
                }
            }
        }
        if (wrappers.isEmpty() || buttons.isEmpty()) {
            return List.of();
        }

        for (PsiStatement statement : statements) {
            if (isKnownAssignment(statement, wrappers, buttons)) {
                continue;
            }
            if (!(statement instanceof PsiExpressionStatement expressionStatement)
                    || !(expressionStatement.getExpression()
                    instanceof PsiMethodCallExpression call)
                    || !applySetupCall(call, wrappers, buttons)) {
                return List.of();
            }
        }

        Map<PsiField, ButtonBuilder> usedButtons = new HashMap<>();
        List<Item> result = new ArrayList<>();
        for (WrapperBuilder wrapper : wrappers.values()) {
            ButtonBuilder button = buttons.get(wrapper.buttonField);
            if (!wrapper.spacing || wrapper.comboField == null
                    || wrapper.comboExpression == null || button == null || !button.icon
                    || button.callbackExpression == null || usedButtons.put(button.field, button) != null) {
                return List.of();
            }
            result.add(new Item(wrapper.field, button.field, wrapper.comboField,
                    wrapper.comboExpression, button.callbackExpression));
        }
        return usedButtons.size() == buttons.size() ? List.copyOf(result) : List.of();
    }

    private static boolean isKnownAssignment(PsiStatement statement,
                                             Map<PsiField, WrapperBuilder> wrappers,
                                             Map<PsiField, ButtonBuilder> buttons) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression()
                instanceof PsiAssignmentExpression assignment)
                || assignment.getOperationTokenType() != JavaTokenType.EQ
                || !(resolveVariable(assignment.getLExpression()) instanceof PsiField field)
                || !(unwrap(assignment.getRExpression()) instanceof PsiNewExpression created)
                || created.getClassReference() == null
                || created.getArgumentList() == null
                || created.getArgumentList().getExpressions().length != 0) {
            return false;
        }
        String className = created.getClassReference().getReferenceName();
        return wrappers.containsKey(field) && "HorizontalLayout".equals(className)
                || buttons.containsKey(field) && "Button".equals(className);
    }

    private static boolean applySetupCall(PsiMethodCallExpression call,
                                          Map<PsiField, WrapperBuilder> wrappers,
                                          Map<PsiField, ButtonBuilder> buttons) {
        PsiVariable qualifier = resolveVariable(
                call.getMethodExpression().getQualifierExpression());
        String methodName = call.getMethodExpression().getReferenceName();
        PsiExpression[] arguments = call.getArgumentList().getExpressions();

        if (qualifier instanceof PsiField wrapperField && wrappers.containsKey(wrapperField)) {
            WrapperBuilder wrapper = wrappers.get(wrapperField);
            if ("setSpacing".equals(methodName) && arguments.length == 1
                    && isBooleanLiteral(arguments[0], true) && !wrapper.spacing) {
                wrapper.spacing = true;
                return true;
            }
            if (!"add".equals(methodName) || arguments.length == 0) {
                return false;
            }
            for (PsiExpression argument : arguments) {
                if (!(resolveVariable(argument) instanceof PsiField field)) {
                    return false;
                }
                if (buttons.containsKey(field) && wrapper.buttonField == null) {
                    wrapper.buttonField = field;
                } else if (!buttons.containsKey(field) && wrapper.comboField == null
                        && isComboBoxField(field)) {
                    wrapper.comboField = field;
                    wrapper.comboExpression = argument.getText();
                } else {
                    return false;
                }
            }
            return true;
        }

        if (qualifier instanceof PsiField buttonField && buttons.containsKey(buttonField)) {
            ButtonBuilder button = buttons.get(buttonField);
            if ("setIcon".equals(methodName) && arguments.length == 1 && !button.icon
                    && arguments[0].getText().endsWith("PLUS_CIRCLE_SOLID.create()")) {
                button.icon = true;
                return true;
            }
            if ("addClickListener".equals(methodName) && arguments.length == 1
                    && arguments[0] instanceof PsiLambdaExpression lambda
                    && button.callbackExpression == null) {
                button.callbackExpression = simplifiedCallback(lambda);
                return button.callbackExpression != null;
            }
        }
        return false;
    }

    private static @Nullable String simplifiedCallback(PsiLambdaExpression lambda) {
        if (!(lambda.getBody() instanceof PsiCodeBlock block)
                || block.getStatements().length != 2
                || !(block.getStatements()[0] instanceof PsiExpressionStatement initStatement)
                || !(initStatement.getExpression() instanceof PsiMethodCallExpression init)
                || !"init".equals(init.getMethodExpression().getReferenceName())
                || !(block.getStatements()[1] instanceof PsiExpressionStatement openStatement)
                || !(openStatement.getExpression() instanceof PsiMethodCallExpression open)
                || !"open".equals(open.getMethodExpression().getReferenceName())
                || open.getArgumentList().getExpressions().length != 0) {
            return null;
        }
        PsiExpression initTarget = init.getMethodExpression().getQualifierExpression();
        PsiExpression openTarget = open.getMethodExpression().getQualifierExpression();
        PsiVariable initVariable = resolveVariable(initTarget);
        PsiVariable openVariable = resolveVariable(openTarget);
        if (initTarget == null || openTarget == null || initVariable == null
                || !initVariable.equals(openVariable)) {
            return null;
        }
        return lambda.getParameterList().getText() + " -> " + init.getText() + ".open()";
    }

    private static @Nullable List<String> parseDisplay(PsiIfStatement displayIf,
                                                        List<Item> items) {
        List<PsiStatement> adminStatements = branchStatements(displayIf.getThenBranch());
        List<PsiStatement> regularStatements = branchStatements(displayIf.getElseBranch());
        if (adminStatements.size() != items.size() || regularStatements.size() != items.size()) {
            return null;
        }

        Map<PsiField, Item> byWrapper = new HashMap<>();
        Map<PsiField, Item> byCombo = new HashMap<>();
        for (Item item : items) {
            byWrapper.put(item.wrapperField(), item);
            if (byCombo.put(item.comboField(), item) != null) {
                return null;
            }
        }

        List<String> result = new ArrayList<>();
        for (int index = 0; index < adminStatements.size(); index++) {
            AddCall admin = addCall(adminStatements.get(index));
            AddCall regular = addCall(regularStatements.get(index));
            if (admin == null || regular == null
                    || !admin.targetExpression().equals(regular.targetExpression())) {
                return null;
            }
            Item item = byWrapper.get(admin.argument());
            if (item == null || byCombo.get(regular.argument()) != item) {
                return null;
            }
            result.add(adminStatements.get(index).getText());
        }
        return result;
    }

    private static @Nullable AddCall addCall(PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof PsiMethodCallExpression call)
                || !"add".equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 1
                || !(resolveVariable(call.getArgumentList().getExpressions()[0])
                instanceof PsiField argument)
                || call.getMethodExpression().getQualifierExpression() == null) {
            return null;
        }
        return new AddCall(call.getMethodExpression().getQualifierExpression().getText(), argument);
    }

    private static boolean hasExternalFieldReferences(PsiClass owner, PsiIfStatement setupIf,
                                                       PsiIfStatement displayIf, List<Item> items) {
        List<PsiField> removable = new ArrayList<>();
        for (Item item : items) {
            removable.add(item.wrapperField());
            removable.add(item.buttonField());
        }
        return PsiTreeUtil.findChildrenOfType(owner, PsiReferenceExpression.class).stream()
                .anyMatch(reference -> reference.resolve() instanceof PsiField field
                        && removable.contains(field)
                        && !PsiTreeUtil.isAncestor(setupIf, reference, false)
                        && !PsiTreeUtil.isAncestor(displayIf, reference, false));
    }

    private static boolean isAdminCondition(@Nullable PsiExpression condition) {
        PsiExpression unwrapped = unwrap(condition);
        if (!(unwrapped instanceof PsiMethodCallExpression call)
                || !"getAdmin".equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 0) {
            return false;
        }
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        return qualifier == null || "this".equals(qualifier.getText());
    }

    private static @NotNull List<PsiStatement> branchStatements(@Nullable PsiStatement branch) {
        if (branch instanceof PsiBlockStatement block) {
            return List.of(block.getCodeBlock().getStatements());
        }
        return branch != null ? List.of(branch) : List.of();
    }

    private static boolean isBooleanLiteral(PsiExpression expression, boolean value) {
        PsiExpression unwrapped = unwrap(expression);
        return unwrapped instanceof PsiLiteralExpression literal
                && Boolean.valueOf(value).equals(literal.getValue());
    }

    private static boolean isComboBoxField(PsiField field) {
        String type = field.getType().getPresentableText();
        return "ComboBox".equals(type) || type.startsWith("ComboBox<");
    }

    private static @Nullable PsiVariable resolveVariable(@Nullable PsiExpression expression) {
        PsiExpression unwrapped = unwrap(expression);
        if (unwrapped instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiVariable variable) {
            return variable;
        }
        return null;
    }

    private static @Nullable PsiExpression unwrap(@Nullable PsiExpression expression) {
        PsiExpression result = expression;
        while (result instanceof PsiParenthesizedExpression parenthesized) {
            result = parenthesized.getExpression();
        }
        return result;
    }
}
