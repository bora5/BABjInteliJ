package rs.co.bora5.plugins.babj.inspection;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.intellij.psi.PsiClass;

/**
 * Resolves JPQL aliases used in a Home's {@code getSelect()} projection. The entity is always bound
 * to {@code x}; every {@code JOIN <source>.<field> <alias>} in {@code getJoin()} binds {@code alias}
 * to the joined association's target entity. Joins are processed left-to-right so chained joins
 * (e.g. {@code JOIN putniNalog.putniNalogPlan putniNalogPlan}) resolve.
 */
final class AliasResolver {

    // JOIN <src>.<field> <alias>  — FETCH and LEFT/INNER keywords are ignored by matching only JOIN.
    private static final Pattern JOIN = Pattern.compile("JOIN\\s+(\\w+)\\.(\\w+)\\s+(\\w+)");

    private final Map<String, PsiClass> aliases = new HashMap<>();

    AliasResolver(PsiClass entity, @Nullable String joinClause) {
        aliases.put("x", entity);
        if (joinClause == null) {
            return;
        }
        Matcher m = JOIN.matcher(joinClause);
        while (m.find()) {
            PsiClass source = aliases.get(m.group(1));
            if (source == null) {
                continue;
            }
            PsiClass target = BabjPsi.propertyClass(source, m.group(2));
            if (target != null) {
                aliases.put(m.group(3), target);
            }
        }
    }

    boolean isKnown(String alias) {
        return aliases.containsKey(alias);
    }

    @Nullable PsiClass classOf(String alias) {
        return aliases.get(alias);
    }
}
