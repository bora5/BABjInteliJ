package rs.co.bora5.plugins.babj.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import rs.co.bora5.plugins.babj.model.EntityModel;

/** Resolves the classes that participate in an entity's conventional BABj module. */
public final class BABjArtifactResolver {

    public static final String ABSTRACT_DTO =
            "rs.co.bora5.programs.bab.front.views.projections.AbstractDTO";
    public static final String ABSTRACT_HOME = "rs.co.bora5.programs.bab.session.AbstractHome";
    public static final String GENERIC_VIEW = "rs.co.bora5.programs.bab.front.views.GenericView";
    public static final String GENERIC_WINDOW =
            "rs.co.bora5.programs.bab.front.windowses.GenericWindow";

    private BABjArtifactResolver() {
    }

    public record Artifact(@NotNull BABjArtifactRole role, @NotNull PsiClass psiClass) {
    }

    public static @Nullable BABjArtifactRole roleOf(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        if (EntityModel.isEntity(psiClass)) {
            return BABjArtifactRole.ENTITY;
        }
        if (typeArgument(psiClass, ABSTRACT_DTO, 0) != null) {
            return BABjArtifactRole.DTO;
        }
        if (typeArgument(psiClass, ABSTRACT_HOME, 0) != null) {
            return BABjArtifactRole.HOME;
        }
        if (typeArgument(psiClass, GENERIC_VIEW, 0) != null) {
            return BABjArtifactRole.VIEW;
        }
        if (typeArgument(psiClass, GENERIC_WINDOW, 0) != null) {
            return BABjArtifactRole.WINDOW;
        }
        return null;
    }

    public static @Nullable PsiClass entityFor(@Nullable PsiClass psiClass) {
        BABjArtifactRole role = roleOf(psiClass);
        if (role == null || psiClass == null) {
            return null;
        }
        return switch (role) {
            case ENTITY -> psiClass;
            case DTO -> typeArgument(psiClass, ABSTRACT_DTO, 0);
            case HOME -> typeArgument(psiClass, ABSTRACT_HOME, 0);
            case VIEW -> typeArgument(psiClass, GENERIC_VIEW, 0);
            case WINDOW -> typeArgument(psiClass, GENERIC_WINDOW, 0);
        };
    }

    /** Resolves a direct BABj base class generic argument. BABj application artifacts use this form. */
    public static @Nullable PsiClass typeArgument(PsiClass subclass, String superFqn, int index) {
        for (PsiClassType type : subclass.getExtendsListTypes()) {
            PsiClass resolved = type.resolve();
            String resolvedName = resolved == null ? null : resolved.getQualifiedName();
            String expectedShortName = superFqn.substring(superFqn.lastIndexOf('.') + 1);
            if (!superFqn.equals(resolvedName) && !expectedShortName.equals(type.getClassName())) {
                continue;
            }
            PsiType[] parameters = type.getParameters();
            if (index < parameters.length) {
                return PsiUtil.resolveClassInClassTypeOnly(parameters[index]);
            }
        }
        return null;
    }

    /**
     * Returns all project classes related to {@code context}, ordered as
     * Entity -> DTO -> Home -> View -> Edit window.
     */
    public static @NotNull List<Artifact> relatedArtifacts(@NotNull PsiClass context) {
        PsiClass entity = entityFor(context);
        if (entity == null) {
            return List.of();
        }
        return CachedValuesManager.getCachedValue(entity, () -> CachedValueProvider.Result.create(
                computeRelatedArtifacts(entity), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static List<Artifact> computeRelatedArtifacts(PsiClass entity) {
        Project project = entity.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        addIfRelated(artifacts, entity, entity);

        String entityName = entity.getName();
        if (entityName != null) {
            addExpected(artifacts, entity, entityName + "DTO", scope);
            addExpected(artifacts, entity, entityName + "Home", scope);
            addExpected(artifacts, entity, entityName + "View", scope);
            addExpected(artifacts, entity, "Edit" + entityName + "Window", scope);
        }

        ReferencesSearch.search(entity, scope).forEach(reference -> {
            PsiElement element = reference.getElement();
            PsiClass candidate = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
            addIfRelated(artifacts, entity, candidate);
            return true;
        });

        List<Artifact> result = new ArrayList<>(artifacts.values());
        result.sort(Comparator.comparing((Artifact a) -> a.role().ordinal())
                .thenComparing(a -> String.valueOf(a.psiClass().getName())));
        return List.copyOf(result);
    }

    private static void addExpected(Map<String, Artifact> artifacts, PsiClass entity,
                                    String shortName, GlobalSearchScope scope) {
        PsiClass[] candidates = PsiShortNamesCache.getInstance(entity.getProject())
                .getClassesByName(shortName, scope);
        for (PsiClass candidate : candidates) {
            addIfRelated(artifacts, entity, candidate);
        }
    }

    private static void addIfRelated(Map<String, Artifact> artifacts, PsiClass entity,
                                     @Nullable PsiClass candidate) {
        BABjArtifactRole role = roleOf(candidate);
        PsiClass candidateEntity = entityFor(candidate);
        if (role == null || candidate == null || candidateEntity == null
                || !sameClass(entity, candidateEntity)) {
            return;
        }
        String key = candidate.getQualifiedName();
        if (key == null) {
            key = role + ":" + candidate.getName() + ":" + candidate.getTextOffset();
        }
        artifacts.putIfAbsent(key, new Artifact(role, candidate));
    }

    private static boolean sameClass(PsiClass left, PsiClass right) {
        String leftName = left.getQualifiedName();
        String rightName = right.getQualifiedName();
        return left.equals(right) || leftName != null && leftName.equals(rightName);
    }
}
