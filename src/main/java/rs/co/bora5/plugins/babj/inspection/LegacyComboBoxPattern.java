package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;

/** Recognizes the old BABj {@code DataProvider.fromFilteringCallbacks(...)} ComboBox idiom. */
final class LegacyComboBoxPattern {

    enum Kind {
        SIMPLE("createSimpleComboBox"),
        DEPENDENT("createDependentComboBox"),
        FLAG("createComboBoxWithFlag"),
        FREE("createFreeComboBox"),
        FREE_FLAG("createFreeComboBoxWithFlag");

        private final String factoryMethod;

        Kind(String factoryMethod) {
            this.factoryMethod = factoryMethod;
        }

        String factoryMethod() {
            return factoryMethod;
        }
    }

    record Match(@NotNull Kind kind,
                 @NotNull PsiMethod refreshMethod,
                 @NotNull PsiMethodCallExpression invocation,
                 @NotNull PsiExpressionStatement invocationStatement,
                 @NotNull PsiExpressionStatement labelStatement,
                 @NotNull String comboExpression,
                 @NotNull String serviceExpression,
                 @NotNull String labelExpression,
                 @NotNull List<String> searchFieldExpressions,
                 @NotNull List<String> middleArgumentExpressions,
                 @NotNull List<PsiExpressionStatement> removableListeners) {

        String replacementStatement() {
            StringBuilder result = new StringBuilder(comboExpression)
                    .append(" = ")
                    .append(kind.factoryMethod())
                    .append('(')
                    .append(labelExpression)
                    .append(", ")
                    .append(serviceExpression);
            for (String middleArgument : middleArgumentExpressions) {
                result.append(", ").append(middleArgument);
            }
            for (String searchField : searchFieldExpressions) {
                result.append(", ").append(searchField);
            }
            return result.append(");").toString();
        }
    }

    private record BodyMatch(@NotNull Kind kind,
                             @NotNull PsiField comboField,
                             @NotNull String comboExpression,
                             @NotNull String serviceExpression,
                             @NotNull List<String> searchFieldExpressions,
                             @NotNull List<String> middleArgumentExpressions,
                             @Nullable PsiVariable parentCombo) {
    }

    private record CallbackMatch(@NotNull Kind kind,
                                 @NotNull PsiVariable service,
                                 @NotNull String serviceExpression,
                                 @NotNull List<String> searchFieldExpressions,
                                 @NotNull List<String> middleArgumentExpressions,
                                 @Nullable PsiVariable parentCombo) {
    }

    private record FlagArgument(boolean value, int filterIndex) {
    }

    private record StringArguments(@NotNull List<String> expressions,
                                   @NotNull List<String> values) {
    }

    private record ListenerScan(boolean safe,
                                @NotNull List<PsiExpressionStatement> removable) {
    }

    private LegacyComboBoxPattern() {
    }

    static @Nullable Match detect(@NotNull PsiMethod refreshMethod) {
        if (!refreshMethod.hasModifierProperty(PsiModifier.PRIVATE)
                || refreshMethod.getParameterList().getParametersCount() != 0) {
            return null;
        }
        PsiClass owner = refreshMethod.getContainingClass();
        BodyMatch body = detectBody(refreshMethod);
        if (owner == null || body == null
                || owner.findMethodsByName(body.kind().factoryMethod(), true).length == 0) {
            return null;
        }

        List<PsiMethodCallExpression> invocations = PsiTreeUtil
                .findChildrenOfType(owner, PsiMethodCallExpression.class).stream()
                .filter(call -> PsiTreeUtil.getParentOfType(call, PsiClass.class) == owner)
                .filter(call -> refreshMethod.equals(call.resolveMethod()))
                .toList();
        if (invocations.size() != 1
                || !(invocations.get(0).getParent() instanceof PsiExpressionStatement invocationStatement)) {
            return null;
        }

        PsiMethodCallExpression invocation = invocations.get(0);
        PsiExpressionStatement labelStatement = uniqueLabel(invocationStatement, body.comboField());
        if (labelStatement == null) {
            return null;
        }
        PsiMethodCallExpression labelCall = (PsiMethodCallExpression) labelStatement.getExpression();
        String labelExpression = labelCall.getArgumentList().getExpressions()[0].getText();

        List<PsiExpressionStatement> listeners = List.of();
        if (body.kind() == Kind.DEPENDENT) {
            ListenerScan scan = dependentListeners(owner, body.parentCombo(), body.comboField());
            if (!scan.safe()) {
                return null;
            }
            listeners = scan.removable();
        }

        return new Match(body.kind(), refreshMethod, invocation, invocationStatement,
                labelStatement, body.comboExpression(), body.serviceExpression(), labelExpression,
                body.searchFieldExpressions(), body.middleArgumentExpressions(), listeners);
    }

    private static @Nullable BodyMatch detectBody(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null || body.getStatements().length != 3) {
            return null;
        }

        PsiLocalVariable dataProvider = null;
        CallbackMatch callback = null;
        PsiField assignedCombo = null;
        String comboExpression = null;
        PsiField itemsCombo = null;
        PsiLocalVariable itemsProvider = null;

        for (PsiStatement statement : body.getStatements()) {
            if (statement instanceof PsiDeclarationStatement declaration) {
                PsiElement[] declared = declaration.getDeclaredElements();
                if (declared.length != 1 || !(declared[0] instanceof PsiLocalVariable local)
                        || !(local.getInitializer() instanceof PsiMethodCallExpression initializer)
                        || !isFilteringCallbacksFactory(initializer)) {
                    return null;
                }
                dataProvider = local;
                callback = detectCallbacks(initializer);
                if (callback == null) {
                    return null;
                }
                continue;
            }

            if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
                return null;
            }
            PsiExpression expression = expressionStatement.getExpression();
            if (expression instanceof PsiAssignmentExpression assignment
                    && assignment.getOperationTokenType() == JavaTokenType.EQ
                    && unwrap(assignment.getRExpression()) instanceof PsiNewExpression newExpression
                    && newExpression.getClassReference() != null
                    && "ComboBox".equals(newExpression.getClassReference().getReferenceName())) {
                PsiVariable variable = resolveVariable(assignment.getLExpression());
                if (!(variable instanceof PsiField field)) {
                    return null;
                }
                assignedCombo = field;
                comboExpression = assignment.getLExpression().getText();
                continue;
            }

            if (expression instanceof PsiMethodCallExpression call
                    && "setItems".equals(call.getMethodExpression().getReferenceName())
                    && call.getArgumentList().getExpressions().length == 1) {
                PsiVariable combo = resolveVariable(call.getMethodExpression().getQualifierExpression());
                PsiVariable provider = resolveVariable(call.getArgumentList().getExpressions()[0]);
                if (!(combo instanceof PsiField field) || !(provider instanceof PsiLocalVariable local)) {
                    return null;
                }
                itemsCombo = field;
                itemsProvider = local;
                continue;
            }
            return null;
        }

        if (dataProvider == null || callback == null || assignedCombo == null
                || !assignedCombo.equals(itemsCombo) || !dataProvider.equals(itemsProvider)
                || comboExpression == null) {
            return null;
        }
        return new BodyMatch(callback.kind(), assignedCombo, comboExpression,
                callback.serviceExpression(), callback.searchFieldExpressions(),
                callback.middleArgumentExpressions(), callback.parentCombo());
    }

    private static boolean isFilteringCallbacksFactory(PsiMethodCallExpression call) {
        if (!"fromFilteringCallbacks".equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 2) {
            return false;
        }
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null && qualifier.getText().endsWith("DataProvider")) {
            return true;
        }
        PsiMethod resolved = call.resolveMethod();
        return resolved != null && resolved.getContainingClass() != null
                && "com.vaadin.flow.data.provider.DataProvider"
                .equals(resolved.getContainingClass().getQualifiedName());
    }

    private static @Nullable CallbackMatch detectCallbacks(PsiMethodCallExpression factory) {
        PsiExpression[] callbacks = factory.getArgumentList().getExpressions();
        if (!(callbacks[0] instanceof PsiLambdaExpression fetchLambda)
                || !(callbacks[1] instanceof PsiLambdaExpression sizeLambda)) {
            return null;
        }
        PsiMethodCallExpression fetch = lambdaCall(fetchLambda);
        PsiMethodCallExpression size = lambdaCall(sizeLambda);
        PsiParameter fetchQuery = singleParameter(fetchLambda);
        PsiParameter sizeQuery = singleParameter(sizeLambda);
        if (fetch == null || size == null || fetchQuery == null || sizeQuery == null) {
            return null;
        }

        String fetchName = fetch.getMethodExpression().getReferenceName();
        String sizeName = size.getMethodExpression().getReferenceName();
        if ("findAllLazy".equals(fetchName) && "findSizeLazy".equals(sizeName)) {
            return detectSimpleCallbacks(fetch, size, fetchQuery, sizeQuery);
        }
        if ("findAllLazyWithOtherEntity".equals(fetchName)
                && "findSizeLazyWithOtherEntity".equals(sizeName)) {
            return detectDependentCallbacks(fetch, size, fetchQuery, sizeQuery);
        }
        if ("findAllLazyWithFlag".equals(fetchName) && "findSizeLazyWithFlag".equals(sizeName)) {
            return detectFlagCallbacks(fetch, size, fetchQuery, sizeQuery);
        }
        if ("findAllLazyFree".equals(fetchName) && "findSizeLazyFree".equals(sizeName)) {
            return detectFreeCallbacks(fetch, size, fetchQuery, sizeQuery);
        }
        if ("findAllLazyFreeWithFlag".equals(fetchName)
                && "findSizeLazyFreeWithFlag".equals(sizeName)) {
            return detectFreeFlagCallbacks(fetch, size, fetchQuery, sizeQuery);
        }
        return null;
    }

    private static @Nullable CallbackMatch detectSimpleCallbacks(PsiMethodCallExpression fetch,
                                                                  PsiMethodCallExpression size,
                                                                  PsiParameter fetchQuery,
                                                                  PsiParameter sizeQuery) {
        PsiExpression[] fetchArgs = fetch.getArgumentList().getExpressions();
        PsiExpression[] sizeArgs = size.getArgumentList().getExpressions();
        PsiVariable fetchService = resolveVariable(fetch.getMethodExpression().getQualifierExpression());
        PsiVariable sizeService = resolveVariable(size.getMethodExpression().getQualifierExpression());
        if (fetchService == null || !fetchService.equals(sizeService)
                || fetchArgs.length < 3 || sizeArgs.length < 1
                || !isQueryCall(fetchArgs[0], fetchQuery, "getOffset")
                || !isQueryCall(fetchArgs[1], fetchQuery, "getLimit")
                || !isFilterExpression(fetchArgs[2], fetchQuery)
                || !isFilterExpression(sizeArgs[0], sizeQuery)) {
            return null;
        }
        StringArguments fetchFields = stringArguments(fetchArgs, 3);
        StringArguments sizeFields = stringArguments(sizeArgs, 1);
        if (fetchFields == null || sizeFields == null
                || !fetchFields.values().equals(sizeFields.values())) {
            return null;
        }
        return new CallbackMatch(Kind.SIMPLE, fetchService,
                fetch.getMethodExpression().getQualifierExpression().getText(),
                fetchFields.expressions(), List.of(), null);
    }

    private static @Nullable CallbackMatch detectDependentCallbacks(PsiMethodCallExpression fetch,
                                                                     PsiMethodCallExpression size,
                                                                     PsiParameter fetchQuery,
                                                                     PsiParameter sizeQuery) {
        PsiExpression[] fetchArgs = fetch.getArgumentList().getExpressions();
        PsiExpression[] sizeArgs = size.getArgumentList().getExpressions();
        PsiVariable fetchService = resolveVariable(fetch.getMethodExpression().getQualifierExpression());
        PsiVariable sizeService = resolveVariable(size.getMethodExpression().getQualifierExpression());
        if (fetchService == null || !fetchService.equals(sizeService)
                || fetchArgs.length < 5 || sizeArgs.length < 3
                || !isQueryCall(fetchArgs[0], fetchQuery, "getOffset")
                || !isQueryCall(fetchArgs[1], fetchQuery, "getLimit")
                || !sameStringLiteral(fetchArgs[2], sizeArgs[0])
                || !isFilterExpression(fetchArgs[4], fetchQuery)
                || !isFilterExpression(sizeArgs[2], sizeQuery)) {
            return null;
        }

        PsiVariable fetchParent = getValueQualifier(fetchArgs[3]);
        PsiVariable sizeParent = getValueQualifier(sizeArgs[1]);
        StringArguments fetchFields = stringArguments(fetchArgs, 5);
        StringArguments sizeFields = stringArguments(sizeArgs, 3);
        if (fetchParent == null || !fetchParent.equals(sizeParent)
                || fetchFields == null || sizeFields == null
                || !fetchFields.values().equals(sizeFields.values())) {
            return null;
        }
        PsiMethodCallExpression parentCall = (PsiMethodCallExpression) unwrap(fetchArgs[3]);
        String parentComboExpression = Objects.requireNonNull(
                parentCall.getMethodExpression().getQualifierExpression()).getText();
        return new CallbackMatch(Kind.DEPENDENT, fetchService,
                fetch.getMethodExpression().getQualifierExpression().getText(),
                fetchFields.expressions(), List.of(fetchArgs[2].getText(), parentComboExpression),
                fetchParent);
    }

    private static @Nullable CallbackMatch detectFlagCallbacks(PsiMethodCallExpression fetch,
                                                                PsiMethodCallExpression size,
                                                                PsiParameter fetchQuery,
                                                                PsiParameter sizeQuery) {
        PsiExpression[] fetchArgs = fetch.getArgumentList().getExpressions();
        PsiExpression[] sizeArgs = size.getArgumentList().getExpressions();
        PsiVariable fetchService = resolveVariable(fetch.getMethodExpression().getQualifierExpression());
        PsiVariable sizeService = resolveVariable(size.getMethodExpression().getQualifierExpression());
        if (fetchService == null || !fetchService.equals(sizeService)
                || fetchArgs.length < 4 || sizeArgs.length < 2
                || !isQueryCall(fetchArgs[0], fetchQuery, "getOffset")
                || !isQueryCall(fetchArgs[1], fetchQuery, "getLimit")
                || !sameStringLiteral(fetchArgs[2], sizeArgs[0])) {
            return null;
        }
        FlagArgument fetchFlag = flagArgument(fetchArgs, 3, fetchQuery);
        FlagArgument sizeFlag = flagArgument(sizeArgs, 1, sizeQuery);
        if (fetchFlag == null || sizeFlag == null || fetchFlag.value() != sizeFlag.value()) {
            return null;
        }
        StringArguments fetchFields = stringArguments(fetchArgs, fetchFlag.filterIndex() + 1);
        StringArguments sizeFields = stringArguments(sizeArgs, sizeFlag.filterIndex() + 1);
        if (fetchFields == null || sizeFields == null
                || !fetchFields.values().equals(sizeFields.values())) {
            return null;
        }
        List<String> middleArguments = fetchFlag.value()
                ? List.of(fetchArgs[2].getText())
                : List.of(fetchArgs[2].getText(), "false");
        return new CallbackMatch(Kind.FLAG, fetchService,
                fetch.getMethodExpression().getQualifierExpression().getText(),
                fetchFields.expressions(), middleArguments, null);
    }

    private static @Nullable CallbackMatch detectFreeCallbacks(PsiMethodCallExpression fetch,
                                                                PsiMethodCallExpression size,
                                                                PsiParameter fetchQuery,
                                                                PsiParameter sizeQuery) {
        PsiExpression[] fetchArgs = fetch.getArgumentList().getExpressions();
        PsiExpression[] sizeArgs = size.getArgumentList().getExpressions();
        PsiVariable fetchService = resolveVariable(fetch.getMethodExpression().getQualifierExpression());
        PsiVariable sizeService = resolveVariable(size.getMethodExpression().getQualifierExpression());
        if (fetchService == null || !fetchService.equals(sizeService)
                || fetchArgs.length < 4 || sizeArgs.length < 2
                || !isQueryCall(fetchArgs[0], fetchQuery, "getOffset")
                || !isQueryCall(fetchArgs[1], fetchQuery, "getLimit")
                || !isFilterExpression(fetchArgs[2], fetchQuery)
                || !isFilterExpression(sizeArgs[0], sizeQuery)
                || !sameStringLiteral(fetchArgs[3], sizeArgs[1])) {
            return null;
        }
        StringArguments fetchFields = stringArguments(fetchArgs, 4);
        StringArguments sizeFields = stringArguments(sizeArgs, 2);
        if (fetchFields == null || sizeFields == null
                || !fetchFields.values().equals(sizeFields.values())) {
            return null;
        }
        return new CallbackMatch(Kind.FREE, fetchService,
                fetch.getMethodExpression().getQualifierExpression().getText(),
                fetchFields.expressions(), List.of(fetchArgs[3].getText()), null);
    }

    private static @Nullable CallbackMatch detectFreeFlagCallbacks(PsiMethodCallExpression fetch,
                                                                    PsiMethodCallExpression size,
                                                                    PsiParameter fetchQuery,
                                                                    PsiParameter sizeQuery) {
        PsiExpression[] fetchArgs = fetch.getArgumentList().getExpressions();
        PsiExpression[] sizeArgs = size.getArgumentList().getExpressions();
        PsiVariable fetchService = resolveVariable(fetch.getMethodExpression().getQualifierExpression());
        PsiVariable sizeService = resolveVariable(size.getMethodExpression().getQualifierExpression());
        if (fetchService == null || !fetchService.equals(sizeService)
                || fetchArgs.length < 5 || sizeArgs.length < 3
                || !isQueryCall(fetchArgs[0], fetchQuery, "getOffset")
                || !isQueryCall(fetchArgs[1], fetchQuery, "getLimit")
                || !isFilterExpression(fetchArgs[2], fetchQuery)
                || !isFilterExpression(sizeArgs[0], sizeQuery)
                || !sameStringLiteral(fetchArgs[3], sizeArgs[1])
                || !sameStringLiteral(fetchArgs[4], sizeArgs[2])) {
            return null;
        }
        int fetchFieldsStart = 5;
        int sizeFieldsStart = 3;
        Boolean fetchFlagValue = fetchArgs.length > 5 ? booleanLiteralValue(fetchArgs[5]) : null;
        Boolean sizeFlagValue = sizeArgs.length > 3 ? booleanLiteralValue(sizeArgs[3]) : null;
        if (fetchFlagValue != null || sizeFlagValue != null) {
            // createFreeComboBoxWithFlag has no flagValue overload, so only true is replaceable.
            if (!Boolean.TRUE.equals(fetchFlagValue) || !Boolean.TRUE.equals(sizeFlagValue)) {
                return null;
            }
            fetchFieldsStart = 6;
            sizeFieldsStart = 4;
        }
        StringArguments fetchFields = stringArguments(fetchArgs, fetchFieldsStart);
        StringArguments sizeFields = stringArguments(sizeArgs, sizeFieldsStart);
        if (fetchFields == null || sizeFields == null
                || !fetchFields.values().equals(sizeFields.values())) {
            return null;
        }
        return new CallbackMatch(Kind.FREE_FLAG, fetchService,
                fetch.getMethodExpression().getQualifierExpression().getText(),
                fetchFields.expressions(),
                List.of(fetchArgs[3].getText(), fetchArgs[4].getText()), null);
    }

    private static @Nullable FlagArgument flagArgument(PsiExpression[] arguments, int index,
                                                       PsiParameter query) {
        if (arguments.length <= index) {
            return null;
        }
        if (isFilterExpression(arguments[index], query)) {
            return new FlagArgument(true, index);
        }
        Boolean value = booleanLiteralValue(arguments[index]);
        if (value != null && arguments.length > index + 1
                && isFilterExpression(arguments[index + 1], query)) {
            return new FlagArgument(value, index + 1);
        }
        return null;
    }

    private static @Nullable Boolean booleanLiteralValue(PsiExpression expression) {
        PsiExpression unwrapped = unwrap(expression);
        return unwrapped instanceof PsiLiteralExpression literal
                && literal.getValue() instanceof Boolean value ? value : null;
    }

    private static @Nullable PsiMethodCallExpression lambdaCall(PsiLambdaExpression lambda) {
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression expression) {
            PsiExpression unwrapped = unwrap(expression);
            return unwrapped instanceof PsiMethodCallExpression call ? call : null;
        }
        if (body instanceof PsiCodeBlock block) {
            PsiStatement[] statements = block.getStatements();
            if (statements.length == 1 && statements[0] instanceof PsiReturnStatement returned) {
                PsiExpression unwrapped = unwrap(returned.getReturnValue());
                return unwrapped instanceof PsiMethodCallExpression call ? call : null;
            }
        }
        return null;
    }

    private static @Nullable PsiParameter singleParameter(PsiLambdaExpression lambda) {
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        return parameters.length == 1 ? parameters[0] : null;
    }

    private static boolean isQueryCall(PsiExpression expression, PsiParameter query,
                                       String methodName) {
        PsiExpression unwrapped = unwrap(expression);
        if (!(unwrapped instanceof PsiMethodCallExpression call)
                || !methodName.equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 0) {
            return false;
        }
        return query.equals(resolveVariable(call.getMethodExpression().getQualifierExpression()));
    }

    private static boolean isFilterExpression(PsiExpression expression, PsiParameter query) {
        PsiExpression unwrapped = unwrap(expression);
        if (!(unwrapped instanceof PsiMethodCallExpression orElse)
                || !"orElse".equals(orElse.getMethodExpression().getReferenceName())
                || orElse.getArgumentList().getExpressions().length != 1
                || !isNullLiteral(orElse.getArgumentList().getExpressions()[0])) {
            return false;
        }
        PsiExpression optional = unwrap(orElse.getMethodExpression().getQualifierExpression());
        return optional instanceof PsiMethodCallExpression getFilter
                && "getFilter".equals(getFilter.getMethodExpression().getReferenceName())
                && getFilter.getArgumentList().getExpressions().length == 0
                && query.equals(resolveVariable(getFilter.getMethodExpression().getQualifierExpression()));
    }

    private static @Nullable StringArguments stringArguments(PsiExpression[] arguments, int start) {
        List<String> expressions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = start; i < arguments.length; i++) {
            PsiExpression expression = unwrap(arguments[i]);
            if (!(expression instanceof PsiLiteralExpression literal)
                    || !(literal.getValue() instanceof String value)) {
                return null;
            }
            expressions.add(arguments[i].getText());
            values.add(value);
        }
        return new StringArguments(List.copyOf(expressions), List.copyOf(values));
    }

    private static boolean sameStringLiteral(PsiExpression first, PsiExpression second) {
        PsiExpression left = unwrap(first);
        PsiExpression right = unwrap(second);
        return left instanceof PsiLiteralExpression leftLiteral
                && right instanceof PsiLiteralExpression rightLiteral
                && leftLiteral.getValue() instanceof String
                && Objects.equals(leftLiteral.getValue(), rightLiteral.getValue());
    }

    private static @Nullable PsiVariable getValueQualifier(PsiExpression expression) {
        PsiExpression unwrapped = unwrap(expression);
        if (!(unwrapped instanceof PsiMethodCallExpression call)
                || !"getValue".equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 0) {
            return null;
        }
        return resolveVariable(call.getMethodExpression().getQualifierExpression());
    }

    private static @Nullable PsiExpressionStatement uniqueLabel(PsiExpressionStatement invocation,
                                                                PsiField comboField) {
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(invocation, PsiMethod.class);
        if (containingMethod == null) {
            return null;
        }
        List<PsiExpressionStatement> labels = PsiTreeUtil
                .findChildrenOfType(containingMethod, PsiExpressionStatement.class).stream()
                .filter(statement -> PsiTreeUtil.getParentOfType(statement, PsiMethod.class)
                        == containingMethod)
                .filter(statement -> labelStatement(statement, comboField) != null)
                .limit(2)
                .toList();
        return labels.size() == 1 ? labels.get(0) : null;
    }

    private static @Nullable PsiExpressionStatement labelStatement(PsiStatement statement,
                                                                   PsiField comboField) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof PsiMethodCallExpression call)
                || !"setLabel".equals(call.getMethodExpression().getReferenceName())
                || call.getArgumentList().getExpressions().length != 1
                || !comboField.equals(resolveVariable(
                call.getMethodExpression().getQualifierExpression()))) {
            return null;
        }
        return expressionStatement;
    }

    private static ListenerScan dependentListeners(PsiClass owner, PsiVariable parentCombo,
                                                    PsiField dependentCombo) {
        if (parentCombo == null) {
            return new ListenerScan(false, List.of());
        }
        Collection<PsiMethodCallExpression> calls = PsiTreeUtil
                .findChildrenOfType(owner, PsiMethodCallExpression.class);
        List<PsiExpressionStatement> removable = new ArrayList<>();
        for (PsiMethodCallExpression call : calls) {
            if (PsiTreeUtil.getParentOfType(call, PsiClass.class) != owner
                    || !"addValueChangeListener".equals(
                    call.getMethodExpression().getReferenceName())
                    || !parentCombo.equals(resolveVariable(
                    call.getMethodExpression().getQualifierExpression()))) {
                continue;
            }
            PsiExpression[] arguments = call.getArgumentList().getExpressions();
            if (arguments.length != 1 || !(arguments[0] instanceof PsiLambdaExpression lambda)
                    || !referencesVariable(lambda, dependentCombo)) {
                continue;
            }
            if (!(call.getParent() instanceof PsiExpressionStatement statement)
                    || !isPureDependentListener(lambda, dependentCombo)) {
                return new ListenerScan(false, List.of());
            }
            removable.add(statement);
        }
        return new ListenerScan(true, List.copyOf(removable));
    }

    private static boolean referencesVariable(PsiElement scope, PsiVariable variable) {
        return PsiTreeUtil.findChildrenOfType(scope, PsiReferenceExpression.class).stream()
                .anyMatch(reference -> variable.equals(reference.resolve()));
    }

    private static boolean isPureDependentListener(PsiLambdaExpression lambda,
                                                   PsiField dependentCombo) {
        if (!(lambda.getBody() instanceof PsiCodeBlock block)
                || block.getStatements().length != 2) {
            return false;
        }
        boolean refresh = false;
        boolean clear = false;
        for (PsiStatement statement : block.getStatements()) {
            refresh |= isRefreshAll(statement, dependentCombo);
            clear |= isSetNull(statement, dependentCombo);
        }
        return refresh && clear;
    }

    private static boolean isRefreshAll(PsiStatement statement, PsiField comboField) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof PsiMethodCallExpression refresh)
                || !"refreshAll".equals(refresh.getMethodExpression().getReferenceName())
                || refresh.getArgumentList().getExpressions().length != 0) {
            return false;
        }
        PsiExpression dataViewExpression = unwrap(
                refresh.getMethodExpression().getQualifierExpression());
        return dataViewExpression instanceof PsiMethodCallExpression dataView
                && "getLazyDataView".equals(dataView.getMethodExpression().getReferenceName())
                && dataView.getArgumentList().getExpressions().length == 0
                && comboField.equals(resolveVariable(
                dataView.getMethodExpression().getQualifierExpression()));
    }

    private static boolean isSetNull(PsiStatement statement, PsiField comboField) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof PsiMethodCallExpression setValue)
                || !"setValue".equals(setValue.getMethodExpression().getReferenceName())
                || setValue.getArgumentList().getExpressions().length != 1
                || !isNullLiteral(setValue.getArgumentList().getExpressions()[0])) {
            return false;
        }
        return comboField.equals(resolveVariable(
                setValue.getMethodExpression().getQualifierExpression()));
    }

    private static boolean isNullLiteral(PsiExpression expression) {
        PsiExpression unwrapped = unwrap(expression);
        return unwrapped instanceof PsiLiteralExpression literal && literal.getValue() == null;
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
