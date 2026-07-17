package rs.co.bora5.plugins.babj.inspection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;

/**
 * Flags a {@code GenericView} subclass that has no matching {@code Edit<Entity>Window}, which the
 * BABj convention requires for every editable view. Offers a quick fix that generates the window.
 */
public class MissingEditWindowInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String GENERIC_VIEW = "rs.co.bora5.programs.bab.front.views.GenericView";

    @Override
    public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass,
                                                      @NotNull InspectionManager manager,
                                                      boolean isOnTheFly) {
        PsiElement anchor = aClass.getNameIdentifier();
        if (anchor == null) {
            return null;
        }

        PsiClass entity = resolveViewEntity(aClass);
        if (entity == null || entity.getName() == null || entity.getQualifiedName() == null) {
            return null;
        }

        String expected = "Edit" + entity.getName() + "Window";
        Project project = manager.getProject();
        PsiClass[] found = PsiShortNamesCache.getInstance(project)
                .getClassesByName(expected, GlobalSearchScope.projectScope(project));
        if (found.length > 0) {
            return null;
        }

        ProblemDescriptor problem = manager.createProblemDescriptor(
                anchor,
                "Missing " + expected + " — BABj convention requires every editable GenericView to have a matching Edit window.",
                new CreateEditWindowQuickFix(entity.getName(), entity.getQualifiedName()),
                ProblemHighlightType.WARNING,
                isOnTheFly);
        return new ProblemDescriptor[]{problem};
    }

    /** Returns the entity type argument of a direct {@code extends GenericView<Entity, ...>}, or null. */
    private static @Nullable PsiClass resolveViewEntity(PsiClass aClass) {
        for (PsiClassType superType : aClass.getExtendsListTypes()) {
            PsiClass resolved = superType.resolve();
            if (resolved == null) {
                continue;
            }
            boolean isGenericView = GENERIC_VIEW.equals(resolved.getQualifiedName())
                    || "GenericView".equals(resolved.getName());
            if (!isGenericView) {
                continue;
            }
            PsiType[] params = superType.getParameters();
            if (params.length == 0) {
                return null;
            }
            return PsiUtil.resolveClassInClassTypeOnly(params[0]);
        }
        return null;
    }
}
