package rs.co.bora5.plugins.babj.model;

import java.util.Collection;

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
        if (project == null) {
            return FALLBACK;
        }
        try {
            PsiClass iface = JavaPsiFacade.getInstance(project)
                    .findClass(OPERATER_INTERFACE, GlobalSearchScope.allScope(project));
            if (iface == null) {
                return FALLBACK;
            }
            Collection<PsiClass> implementors = ClassInheritorsSearch
                    .search(iface, GlobalSearchScope.projectScope(project), true).findAll();
            for (PsiClass c : implementors) {
                if (!c.isInterface()
                        && !c.hasModifierProperty(PsiModifier.ABSTRACT)
                        && c.getName() != null) {
                    return c.getName();
                }
            }
        } catch (Exception e) {
            return FALLBACK;
        }
        return FALLBACK;
    }
}
