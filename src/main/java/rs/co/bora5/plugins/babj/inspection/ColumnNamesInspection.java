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

/**
 * Validates a {@code GenericView}'s {@code @ColumnNames} entries. Each entry is
 * {@code property~key~Header}: the {@code property} path (optionally {@code *}-prefixed) must resolve
 * against the entity, and the {@code key} — when present — must be a property projected by the
 * service, i.e. a field of the view's DTO ("podržano iz servisa").
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
        List<ProblemDescriptor> problems = new ArrayList<>();

        for (String entry : BABjPsi.splitTopLevel(value)) {
            String[] parts = entry.split("~", -1);
            String prop = parts[0].trim();
            if (prop.startsWith("*")) {
                prop = prop.substring(1);
            }
            if (!prop.isEmpty() && PATH.matcher(prop).matches()) {
                String error = BABjPsi.validatePath(entity, prop.split("\\."), 0);
                if (error != null) {
                    problems.add(problem(manager, literal, isOnTheFly,
                            "@ColumnNames: kolona '" + prop + "' — " + error + "."));
                }
            }

            String key = parts.length > 1 ? parts[1].trim() : "";
            if (dto != null && IDENT.matcher(key).matches() && BABjPsi.isMissingProperty(dto, key)) {
                problems.add(problem(manager, literal, isOnTheFly,
                        "@ColumnNames: ključ '" + key + "' nije podržan iz servisa (nema polja '"
                                + key + "' u DTO " + dto.getName() + ")."));
            }
        }

        return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static ProblemDescriptor problem(InspectionManager manager, PsiLiteralExpression literal,
                                             boolean onTheFly, String message) {
        return manager.createProblemDescriptor(literal, message, (LocalQuickFix) null,
                ProblemHighlightType.WARNING, onTheFly);
    }
}
