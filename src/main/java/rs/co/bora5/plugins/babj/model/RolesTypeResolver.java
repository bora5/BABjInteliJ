package rs.co.bora5.plugins.babj.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;

/** Finds application role registries and their inherited public string constants. */
public final class RolesTypeResolver {

    private static final String ABSTRACT_ROLES = "rs.co.bora5.programs.bab.utils.AbstractRoles";

    private RolesTypeResolver() {
    }

    public record RolesType(String simpleName, String qualifiedName, List<String> roleNames) {
        @Override
        public String toString() {
            return simpleName;
        }
    }

    public static List<RolesType> resolveAll(Project project) {
        if (project == null) {
            return List.of();
        }
        try {
            PsiClass abstractRoles = JavaPsiFacade.getInstance(project)
                    .findClass(ABSTRACT_ROLES, GlobalSearchScope.allScope(project));
            if (abstractRoles == null) {
                return List.of();
            }
            List<RolesType> result = new ArrayList<>();
            for (PsiClass candidate : ClassInheritorsSearch
                    .search(abstractRoles, GlobalSearchScope.projectScope(project), true).findAll()) {
                if (candidate.isInterface() || candidate.hasModifierProperty(PsiModifier.ABSTRACT)
                        || candidate.getName() == null || candidate.getQualifiedName() == null) {
                    continue;
                }
                Set<String> roleNames = new LinkedHashSet<>();
                for (PsiField field : candidate.getAllFields()) {
                    if (field.hasModifierProperty(PsiModifier.PUBLIC)
                            && field.hasModifierProperty(PsiModifier.STATIC)
                            && field.hasModifierProperty(PsiModifier.FINAL)
                            && field.getType().equalsToText("java.lang.String")) {
                        roleNames.add(field.getName());
                    }
                }
                List<String> sortedRoles = roleNames.stream().sorted().toList();
                result.add(new RolesType(
                        candidate.getName(), candidate.getQualifiedName(), sortedRoles));
            }
            result.sort(Comparator.comparing(RolesType::qualifiedName));
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
