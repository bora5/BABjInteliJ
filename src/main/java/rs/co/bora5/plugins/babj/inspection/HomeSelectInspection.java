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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLiteralExpression;

/**
 * Validates the field references inside an {@code AbstractHome} subclass' {@code getSelect()}
 * projection against the real entity fields, honouring the aliases declared in {@code getJoin()}.
 * <p>
 * A token like {@code x.naziv} or {@code df.naziv} must use a known alias ({@code x} or a join alias)
 * and name an existing property along the path. Function calls ({@code CONCAT(...)}) and string
 * literals are left alone.
 */
public class HomeSelectInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String ABSTRACT_HOME = "rs.co.bora5.programs.bab.session.AbstractHome";
    private static final Pattern DOTTED_PATH = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+");

    @Override
    public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass,
                                                      @NotNull InspectionManager manager,
                                                      boolean isOnTheFly) {
        PsiClass entity = BabjPsi.typeArgument(aClass, ABSTRACT_HOME, 0);
        if (entity == null) {
            return null;
        }

        PsiLiteralExpression selectLiteral =
                BabjPsi.firstLiteralContaining(BabjPsi.declaredMethod(aClass, "getSelect"));
        String select = BabjPsi.stringValue(selectLiteral);
        if (select == null) {
            return null;
        }

        String joinClause = BabjPsi.stringValue(BabjPsi.firstLiteral(BabjPsi.declaredMethod(aClass, "getJoin")));
        AliasResolver aliases = new AliasResolver(entity, joinClause);

        String cols = stripOuterParens(select);
        List<ProblemDescriptor> problems = new ArrayList<>();

        for (String rawToken : BabjPsi.splitTopLevel(cols)) {
            String token = rawToken.trim();
            if (!DOTTED_PATH.matcher(token).matches()) {
                continue; // function call, literal, or a plain identifier — not a field path
            }
            String[] segs = token.split("\\.");
            String alias = segs[0];
            if (!aliases.isKnown(alias)) {
                problems.add(manager.createProblemDescriptor(selectLiteral,
                        "getSelect: nepoznat alias '" + alias + "' u '" + token
                                + "' (dozvoljeni su 'x' i alias-i iz getJoin).",
                        (LocalQuickFix) null, ProblemHighlightType.WARNING, isOnTheFly));
                continue;
            }
            String error = BabjPsi.validatePath(aliases.classOf(alias), segs, 1);
            if (error != null) {
                problems.add(manager.createProblemDescriptor(selectLiteral,
                        "getSelect: u '" + token + "' — " + error + ".",
                        (LocalQuickFix) null, ProblemHighlightType.WARNING, isOnTheFly));
            }
        }

        return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static String stripOuterParens(String s) {
        String t = s.trim();
        int open = t.indexOf('(');
        int close = t.lastIndexOf(')');
        return open >= 0 && close > open ? t.substring(open + 1, close) : t;
    }
}
