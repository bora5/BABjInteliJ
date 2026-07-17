package rs.co.bora5.plugins.babj.inspection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import rs.co.bora5.plugins.babj.model.BABjNaming;

/**
 * Shared PSI helpers for the BABj inspections: resolving a subclass' generic type arguments,
 * checking entity properties (fields <i>or</i> getters), extracting string literals from a method,
 * and splitting a projection/column string at top level.
 */
final class BABjPsi {

    private BABjPsi() {
    }

    /**
     * Resolves the {@code index}-th type argument of the direct {@code extends <superFqn><...>} of
     * {@code subclass} to a class, or {@code null} if it does not extend that type / cannot resolve.
     */
    static @Nullable PsiClass typeArgument(PsiClass subclass, String superFqn, int index) {
        for (PsiClassType t : subclass.getExtendsListTypes()) {
            PsiClass resolved = t.resolve();
            if (resolved == null || !superFqn.equals(resolved.getQualifiedName())) {
                continue;
            }
            PsiType[] params = t.getParameters();
            if (index < params.length) {
                return PsiUtil.resolveClassInClassTypeOnly(params[index]);
            }
        }
        return null;
    }

    /** Whether {@code owner} is missing property {@code name} — it has neither a field nor a getter for it. */
    static boolean isMissingProperty(PsiClass owner, String name) {
        if (owner.findFieldByName(name, true) != null) {
            return false;
        }
        String cap = BABjNaming.capitalize(name);
        return owner.findMethodsByName("get" + cap, true).length == 0
               && owner.findMethodsByName("is" + cap, true).length == 0;
    }

    /** The class a property navigates to (its field/getter type), or {@code null} for scalars. */
    static @Nullable PsiClass propertyClass(PsiClass owner, String name) {
        PsiType type = null;
        PsiField field = owner.findFieldByName(name, true);
        if (field != null) {
            type = field.getType();
        } else {
            String cap = BABjNaming.capitalize(name);
            PsiMethod[] getters = owner.findMethodsByName("get" + cap, true);
            if (getters.length == 0) {
                getters = owner.findMethodsByName("is" + cap, true);
            }
            if (getters.length > 0) {
                type = getters[0].getReturnType();
            }
        }
        return type == null ? null : PsiUtil.resolveClassInClassTypeOnly(type);
    }

    /**
     * Walks a dotted property path starting from {@code base}, over segments {@code segs[start..]}.
     *
     * @return an error message describing the first invalid segment, or {@code null} if the whole
     * path resolves.
     */
    static @Nullable String validatePath(PsiClass base, String[] segs, int start) {
        PsiClass cur = base;
        for (int i = start; i < segs.length; i++) {
            String seg = segs[i];
            if (cur == null) {
                return "ne mogu da razrešim tip za '" + seg + "'";
            }
            if (isMissingProperty(cur, seg)) {
                return "'" + seg + "' nije polje entiteta " + cur.getName();
            }
            if (i < segs.length - 1) {
                cur = propertyClass(cur, seg);
            }
        }
        return null;
    }

    /** The first string-literal (or text block) value inside {@code method} containing {@code ch}. */
    static @Nullable PsiLiteralExpression firstLiteralContaining(@Nullable PsiMethod method) {
        if (method == null) {
            return null;
        }
        Collection<PsiLiteralExpression> literals =
                PsiTreeUtil.findChildrenOfType(method, PsiLiteralExpression.class);
        for (PsiLiteralExpression lit : literals) {
            if (lit.getValue() instanceof String s && s.indexOf('(') >= 0) {
                return lit;
            }
        }
        return null;
    }

    /** Any string-literal value inside {@code method}, or {@code null}. */
    static @Nullable PsiLiteralExpression firstLiteral(@Nullable PsiMethod method) {
        if (method == null) {
            return null;
        }
        for (PsiLiteralExpression lit : PsiTreeUtil.findChildrenOfType(method, PsiLiteralExpression.class)) {
            if (lit.getValue() instanceof String) {
                return lit;
            }
        }
        return null;
    }

    static @Nullable String stringValue(@Nullable PsiLiteralExpression lit) {
        return lit != null && lit.getValue() instanceof String s ? s : null;
    }

    static @Nullable PsiMethod declaredMethod(PsiClass c, String name) {
        PsiMethod[] ms = c.findMethodsByName(name, false);
        return ms.length > 0 ? ms[0] : null;
    }

    /** The string literal backing a single-string annotation attribute, or {@code null}. */
    static @Nullable PsiLiteralExpression annotationStringLiteral(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        return value instanceof PsiLiteralExpression lit && lit.getValue() instanceof String ? lit : null;
    }

    /**
     * Splits {@code s} on {@code delim} at top level only — commas inside parentheses or single
     * quotes are not split, so function calls like {@code CONCAT(a, ' ', b)} stay whole.
     */
    static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote && c == '(') {
                depth++;
            } else if (!inQuote && c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0 && !inQuote) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }
}
