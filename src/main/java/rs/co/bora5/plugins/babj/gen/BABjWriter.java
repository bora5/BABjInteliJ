package rs.co.bora5.plugins.babj.gen;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;

/**
 * Creates Java source files inside a project's source root, materialising the target package as
 * directories when they do not yet exist. Must be called inside a write action.
 */
public final class BABjWriter {

    public record WriteResult(PsiFile file, boolean replaced) {
    }

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
     * unless {@code overwriteExisting} is explicitly enabled by the caller after user confirmation.
     */
    public static WriteResult writeJava(Project project, PsiDirectory dir, String fileName,
                                        String text, boolean overwriteExisting) {
        PsiFile existing = dir.findFile(fileName);
        if (existing != null && !overwriteExisting) {
            return new WriteResult(null, false);
        }
        if (existing != null) {
            PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
            Document document = documents.getDocument(existing);
            if (document == null) {
                return new WriteResult(null, false);
            }
            documents.doPostponedOperationsAndUnblockDocument(document);
            document.setText(text);
            documents.commitDocument(document);
            return new WriteResult(existing, true);
        }
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, text);
        return new WriteResult((PsiFile) dir.add(file), false);
    }
}
