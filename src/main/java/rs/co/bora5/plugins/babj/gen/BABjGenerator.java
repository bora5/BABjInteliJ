package rs.co.bora5.plugins.babj.gen;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

import rs.co.bora5.plugins.babj.model.GenerationContext;

/**
 * Orchestrates writing the selected babj artifacts for one entity. Must run inside a write action.
 */
public final class BABjGenerator {

    private BABjGenerator() {
    }

    /** Outcome of a generation run: which files were created, which were skipped, and what to open. */
    public record Result(List<String> created, List<String> skipped, PsiFile fileToOpen) {
    }

    public static Result generate(Project project, VirtualFile sourceRoot, GenerationContext ctx) {
        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        PsiFile toOpen = null;
        String entity = ctx.getEntityName();

        if (ctx.isGenerateDto()) {
            PsiFile f = write(project, sourceRoot, ctx.dtoPackage(), entity + "DTO.java",
                    CodeTemplates.dto(ctx), created, skipped);
            toOpen = firstNonNull(toOpen, f);
        }
        if (ctx.isGenerateHome()) {
            PsiFile f = write(project, sourceRoot, ctx.homePackage(), entity + "Home.java",
                    CodeTemplates.home(ctx), created, skipped);
            toOpen = firstNonNull(toOpen, f);
        }
        if (ctx.isGenerateView()) {
            PsiFile f = write(project, sourceRoot, ctx.viewPackage(), ctx.getViewName() + ".java",
                    CodeTemplates.view(ctx), created, skipped);
            toOpen = firstNonNull(toOpen, f);
        }
        if (ctx.isGenerateWindow()) {
            PsiFile f = write(project, sourceRoot, ctx.windowPackage(), "Edit" + entity + "Window.java",
                    CodeTemplates.window(ctx), created, skipped);
            toOpen = firstNonNull(toOpen, f);
        }

        return new Result(created, skipped, toOpen);
    }

    private static PsiFile write(Project project, VirtualFile sourceRoot, String pkg, String fileName,
                                 String text, List<String> created, List<String> skipped) {
        PsiDirectory dir = BABjWriter.findOrCreatePackageDir(project, sourceRoot, pkg);
        if (dir == null) {
            skipped.add(fileName + " (paket se ne može kreirati)");
            return null;
        }
        PsiFile f = BABjWriter.writeJava(project, dir, fileName, text);
        if (f == null) {
            skipped.add(fileName + " (već postoji)");
        } else {
            created.add(fileName);
        }
        return f;
    }

    private static PsiFile firstNonNull(PsiFile current, PsiFile candidate) {
        return current != null ? current : candidate;
    }
}
