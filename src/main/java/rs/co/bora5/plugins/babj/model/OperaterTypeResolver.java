package rs.co.bora5.plugins.babj.model;

import java.util.Collection;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;

/**
 * Resolves the project's operator ({@code K}) entity — the concrete entity implementing
 * {@code OperaterEntityInterface} that parameterises every {@code GenericView}/{@code GenericWindow}
 * (typically {@code Korisnik}). Falls back to {@code "Korisnik"} when nothing can be resolved.
 */
public final class OperaterTypeResolver {

    private static final String OPERATER_INTERFACE =
            "rs.co.bora5.programs.bab.model.interfaceCheck.OperaterEntityInterface";
    private static final String FALLBACK = "Korisnik";

    private OperaterTypeResolver() {
    }

    public static String resolve(Project project) {
        return resolveAll(project).get(0);
    }

    /** Returns every concrete project implementation, sorted for stable presentation in the UI. */
    public static List<String> resolveAll(Project project) {
        if (project == null) {
            return List.of(FALLBACK);
        }
        try {
            PsiClass iface = JavaPsiFacade.getInstance(project)
                    .findClass(OPERATER_INTERFACE, GlobalSearchScope.allScope(project));
            if (iface == null) {
                return List.of(FALLBACK);
            }
            Collection<PsiClass> implementors = ClassInheritorsSearch
                    .search(iface, GlobalSearchScope.projectScope(project), true).findAll();
            List<String> concreteTypes = implementors.stream()
                    .filter(c -> !c.isInterface())
                    .filter(c -> !c.hasModifierProperty(PsiModifier.ABSTRACT))
                    .map(PsiClass::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
            if (!concreteTypes.isEmpty()) {
                return concreteTypes;
            }
        } catch (Exception e) {
            return List.of(FALLBACK);
        }
        return List.of(FALLBACK);
    }
}
