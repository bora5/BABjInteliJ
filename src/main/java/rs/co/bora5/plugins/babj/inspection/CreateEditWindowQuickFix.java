package rs.co.bora5.plugins.babj.inspection;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;

import rs.co.bora5.plugins.babj.gen.BabjGenerator;
import rs.co.bora5.plugins.babj.model.BabjNaming;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;
import rs.co.bora5.plugins.babj.model.OperaterTypeResolver;

/**
 * Quick fix that generates the missing {@code Edit<Entity>Window} for a {@code GenericView}.
 */
public class CreateEditWindowQuickFix implements LocalQuickFix {

    private final String entitySimpleName;
    private final String entityFqn;

    public CreateEditWindowQuickFix(String entitySimpleName, String entityFqn) {
        this.entitySimpleName = entitySimpleName;
        this.entityFqn = entityFqn;
    }

    @Override
    public @NotNull String getName() {
        return "Generiši Edit" + entitySimpleName + "Window";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "babj";
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                         @NotNull ProblemDescriptor previewDescriptor) {
        // The fix creates a brand-new file in another package; there is nothing to preview in place.
        return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass entity = JavaPsiFacade.getInstance(project)
                .findClass(entityFqn, GlobalSearchScope.projectScope(project));
        if (entity == null) {
            return;
        }
        PsiFile file = entity.getContainingFile();
        VirtualFile vFile = file != null ? file.getVirtualFile() : null;
        if (vFile == null) {
            return;
        }
        VirtualFile sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(vFile);
        if (sourceRoot == null) {
            return;
        }

        EntityModel model = EntityModel.from(entity);
        String base = BabjNaming.basePackage(model.getPackageName());
        GenerationContext ctx = new GenerationContext(
                base, model.getSimpleName(), OperaterTypeResolver.resolve(project), "ADMIN",
                model.getSimpleName() + "View", BabjNaming.decapitalize(model.getSimpleName()) + "View",
                BabjNaming.label(model.getSimpleName()), model.getFields(),
                false, false, false, true);

        BabjGenerator.generate(project, sourceRoot, ctx);
    }
}
