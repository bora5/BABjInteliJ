package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;

/**
 * Validates a {@code GenericView}'s {@code @ColumnNames} entries. Each entry is
 * {@code property~key~Header[~filterEnabled[~sortingEnabled]]}: the {@code property} path
 * (optionally {@code *}-prefixed) must resolve against the entity or a JPQL alias declared by
 * {@code getJoin()}, and the {@code key} — when present — must be a property projected by the
 * service, i.e. a field of the view's DTO. The two trailing boolean flags are optional.
 */
public class ColumnNamesInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String GENERIC_VIEW = "rs.co.bora5.programs.bab.front.views.GenericView";
    private static final String COLUMN_NAMES = "rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames";

    private static final Pattern PATH = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_$][\\w$]*");

    @Override
    public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass,
                                                      @NotNull InspectionManager manager,
                                                      boolean isOnTheFly) {
        PsiClass entity = BABjPsi.typeArgument(aClass, GENERIC_VIEW, 0);
        if (entity == null) {
            return null;
        }
        PsiAnnotation annotation = aClass.getAnnotation(COLUMN_NAMES);
        if (annotation == null) {
            return null;
        }
        PsiLiteralExpression literal = BABjPsi.annotationStringLiteral(annotation);
        String value = BABjPsi.stringValue(literal);
        if (value == null || value.isBlank()) {
            return null;
        }

        PsiClass dto = BABjPsi.typeArgument(aClass, GENERIC_VIEW, 2);
        AliasResolver aliases = new AliasResolver(entity, joinClause(aClass));
        List<ProblemDescriptor> problems = new ArrayList<>();

        for (String entry : BABjPsi.splitTopLevel(value)) {
            String[] parts = entry.split("~", -1);
            String prop = parts[0].trim();
            if (prop.startsWith("*")) {
                prop = prop.substring(1);
            }
            if (!prop.isEmpty() && PATH.matcher(prop).matches()) {
                String[] segments = prop.split("\\.");
                String alias = segments[0];
                boolean usesAlias = segments.length > 1 && aliases.isKnown(alias);
                String error = BABjPsi.validatePath(
                        usesAlias ? aliases.classOf(alias) : entity,
                        segments, usesAlias ? 1 : 0);
                if (error != null) {
                    problems.add(problem(manager, literal, isOnTheFly,
                            "@ColumnNames: column '" + prop + "' — " + error + "."));
                }
            }

            String key = parts.length > 1 ? parts[1].trim() : "";
            if (dto != null && IDENT.matcher(key).matches() && BABjPsi.isMissingProperty(dto, key)) {
                problems.add(problem(manager, literal, isOnTheFly,
                        "@ColumnNames: key '" + key + "' is not provided by the service (DTO "
                                + dto.getName() + " has no '" + key + "' property)."));
            }

            validateOptionalBoolean(parts, 3, "filter enabled", manager, literal, isOnTheFly,
                    problems);
            validateOptionalBoolean(parts, 4, "sorting enabled", manager, literal, isOnTheFly,
                    problems);
        }

        return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static @Nullable String joinClause(PsiClass view) {
        PsiMethod joinMethod = BABjPsi.declaredMethod(view, "getJoin");
        if (joinMethod == null) {
            PsiClass home = BABjPsi.typeArgument(view, GENERIC_VIEW, 1);
            joinMethod = home == null ? null : BABjPsi.declaredMethod(home, "getJoin");
        }
        return BABjPsi.stringValue(BABjPsi.firstLiteral(joinMethod));
    }

    private static void validateOptionalBoolean(String[] parts, int index, String label,
                                                InspectionManager manager,
                                                PsiLiteralExpression literal, boolean onTheFly,
                                                List<ProblemDescriptor> problems) {
        if (parts.length <= index) {
            return;
        }
        String value = parts[index].trim();
        if (!value.isEmpty() && !"true".equalsIgnoreCase(value)
                && !"false".equalsIgnoreCase(value)) {
            problems.add(problem(manager, literal, onTheFly,
                    "@ColumnNames: " + label + " must be true or false."));
        }
    }

    private static ProblemDescriptor problem(InspectionManager manager, PsiLiteralExpression literal,
                                             boolean onTheFly, String message) {
        return manager.createProblemDescriptor(literal, message, (LocalQuickFix) null,
                ProblemHighlightType.WARNING, onTheFly);
    }
}
