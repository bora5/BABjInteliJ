package rs.co.bora5.plugins.babj.gen;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;

/**
 * Creates Java source files inside a project's source root, materialising the target package as
 * directories when they do not yet exist. Must be called inside a write action.
 */
public final class BABjWriter {

    private BABjWriter() {
    }

    /**
     * Resolves (creating as needed) the {@link PsiDirectory} for {@code packageName} under
     * {@code sourceRoot}.
     */
    public static PsiDirectory findOrCreatePackageDir(Project project, VirtualFile sourceRoot, String packageName) {
        PsiDirectory dir = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (dir == null) {
            return null;
        }
        for (String segment : packageName.split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            PsiDirectory child = dir.findSubdirectory(segment);
            dir = (child != null) ? child : dir.createSubdirectory(segment);
        }
        return dir;
    }

    /**
     * Writes {@code fileName} with {@code text} into {@code dir}. Existing files are left untouched
     * (returns {@code null}) so generation never clobbers hand-written code.
     */
    public static PsiFile writeJava(Project project, PsiDirectory dir, String fileName, String text) {
        if (dir.findFile(fileName) != null) {
            return null;
        }
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, text);
        return (PsiFile) dir.add(file);
    }
}
