package rs.co.bora5.plugins.babj.completion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;

import rs.co.bora5.plugins.babj.model.BABjNaming;
import rs.co.bora5.plugins.babj.navigation.BABjArtifactResolver;

/** Context-aware completion for string-valued BABj annotations. */
public class BABjCompletionContributor extends CompletionContributor {

    private static final String COLUMN_NAMES =
            "rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames";
    private static final String ADD_CONDITION =
            "rs.co.bora5.programs.bab.front.views.interfaceCheck.AddCondition";
    private static final String ADMIN_VISIBLE_FIELDS =
            "rs.co.bora5.programs.bab.front.views.interfaceCheck.AdminVisibleFields";
    private static final String ENABLED_FOR_STATUS =
            "rs.co.bora5.programs.bab.front.views.interfaceCheck.EnabledForStatus";
    private static final String SQL_FIELD_NAME =
            "rs.co.bora5.programs.bab.front.views.interfaceCheck.SqlFieldName";
    private static final String PROPERTY_ID = "com.vaadin.flow.data.binder.PropertyId";
    private static final String SINGLE_UNIQUE_FIELD =
            "rs.co.bora5.programs.bab.front.windowses.interfaceCheck.SingleUniqueField";

    public BABjCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getOriginalPosition() != null
                    ? parameters.getOriginalPosition() : parameters.getPosition();
            PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(
                    position, PsiLiteralExpression.class, false);
            if (literal == null || !(literal.getValue() instanceof String)) {
                return;
            }
            PsiAnnotation annotation = PsiTreeUtil.getParentOfType(
                    literal, PsiAnnotation.class, true);
            String annotationName = annotation == null ? null : annotation.getQualifiedName();
            if (annotationName == null) {
                return;
            }

            String attribute = attributeName(literal);
            if (!supports(annotationName, attribute)) {
                return;
            }
            PsiClass owner = PsiTreeUtil.getParentOfType(annotation, PsiClass.class, true);
            PsiClass entity = BABjArtifactResolver.entityFor(owner);
            if (entity == null) {
                return;
            }

            if (COLUMN_NAMES.equals(annotationName) && "value".equals(attribute)) {
                for (String column : columnSuggestions(entity)) {
                    result.addElement(LookupElementBuilder.create(column)
                            .withPresentableText(column)
                            .withTypeText("BABj column", true));
                }
                return;
            }

            for (String property : propertyPaths(entity)) {
                result.addElement(LookupElementBuilder.create(property)
                        .withTypeText(entity.getName(), true));
            }
        }
    }

    private static boolean supports(String annotation, String attribute) {
        if (COLUMN_NAMES.equals(annotation)) {
            return "value".equals(attribute);
        }
        if (ADD_CONDITION.equals(annotation)) {
            return "field".equals(attribute);
        }
        if (ADMIN_VISIBLE_FIELDS.equals(annotation)) {
            return "fields".equals(attribute);
        }
        if (ENABLED_FOR_STATUS.equals(annotation)) {
            return "korisnik".equals(attribute);
        }
        if (SQL_FIELD_NAME.equals(annotation)) {
            return "name".equals(attribute) || "secundaryName".equals(attribute);
        }
        return (PROPERTY_ID.equals(annotation) || SINGLE_UNIQUE_FIELD.equals(annotation))
                && "value".equals(attribute);
    }

    private static String attributeName(PsiLiteralExpression literal) {
        PsiNameValuePair pair = PsiTreeUtil.getParentOfType(
                literal, PsiNameValuePair.class, true);
        return pair == null || pair.getName() == null ? "value" : pair.getName();
    }

    private static List<String> propertyPaths(PsiClass entity) {
        Set<String> result = new LinkedHashSet<>();
        for (PsiField field : entity.getAllFields()) {
            if (!isProperty(field)) {
                continue;
            }
            result.add(field.getName());
            PsiClass nested = PsiUtil.resolveClassInClassTypeOnly(field.getType());
            if (!isNavigableDomainClass(nested)) {
                continue;
            }
            for (PsiField nestedField : nested.getAllFields()) {
                if (isProperty(nestedField)) {
                    result.add(field.getName() + "." + nestedField.getName());
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<String> columnSuggestions(PsiClass entity) {
        List<String> result = new ArrayList<>();
        for (PsiField field : entity.getAllFields()) {
            if (!isProperty(field)) {
                continue;
            }
            String fieldName = field.getName();
            String label = BABjNaming.label(fieldName);
            PsiClass nested = PsiUtil.resolveClassInClassTypeOnly(field.getType());
            if (isNavigableDomainClass(nested)) {
                String display = displayProperty(nested);
                result.add("*" + fieldName + "." + display + "~" + fieldName + "~" + label);
            } else {
                result.add(fieldName + "~" + fieldName + "~" + label);
            }
        }
        return result;
    }

    private static boolean isProperty(PsiField field) {
        return !field.hasModifierProperty(PsiModifier.STATIC)
                && !"serialVersionUID".equals(field.getName());
    }

    private static boolean isNavigableDomainClass(PsiClass psiClass) {
        if (psiClass == null || psiClass.isEnum()) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return qualifiedName != null
                && !qualifiedName.startsWith("java.")
                && !qualifiedName.startsWith("jakarta.")
                && !qualifiedName.startsWith("javax.");
    }

    private static String displayProperty(PsiClass psiClass) {
        if (psiClass.findFieldByName("naziv", true) != null) {
            return "naziv";
        }
        if (psiClass.findFieldByName("username", true) != null) {
            return "username";
        }
        for (PsiField field : psiClass.getAllFields()) {
            PsiType type = field.getType();
            if (isProperty(field) && type.equalsToText("java.lang.String")) {
                return field.getName();
            }
        }
        return "naziv";
    }
}
