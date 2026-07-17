package rs.co.bora5.plugins.babj.action;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;

import rs.co.bora5.plugins.babj.gen.BABjGenerator;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;

/**
 * "BABj CRUD" generate action: from the JPA entity under the caret, produces the DTO / Home / View /
 * EditWindow quartet in the conventional sibling packages.
 */
public class GenerateBABjCrudAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // update() reads the editor caret + light PSI, both safe on the EDT.
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Find Action and some Generate menu contexts do not expose PSI_FILE even when a Java editor
        // is active. Fall back to the selected editor so the action is not incorrectly disabled.
        e.getPresentation().setEnabledAndVisible(findJavaFile(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        PsiClass entityClass = findEntityClass(e);
        if (entityClass == null) {
            Messages.showInfoMessage(project,
                    "No JPA entity was found in this file (a class annotated with @Entity or extending AbstractEntity).",
                    "BABj CRUD Generator");
            return;
        }

        PsiFile containingFile = entityClass.getContainingFile();
        VirtualFile entityVFile = containingFile != null ? containingFile.getVirtualFile() : null;
        if (entityVFile == null) {
            return;
        }
        VirtualFile sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(entityVFile);
        if (sourceRoot == null) {
            Messages.showErrorDialog(project,
                    "Could not find the source root for the entity.", "BABj CRUD Generator");
            return;
        }

        EntityModel model = EntityModel.from(entityClass);
        GenerateBABjCrudDialog dialog = new GenerateBABjCrudDialog(project, model);
        if (!dialog.showAndGet()) {
            return;
        }
        GenerationContext ctx = dialog.buildContext();

        BABjGenerator.Result result = WriteCommandAction.writeCommandAction(project)
                .withName("BABj CRUD Generator")
                .compute(() -> BABjGenerator.generate(project, sourceRoot, ctx));

        if (result.fileToOpen() != null && result.fileToOpen().getVirtualFile() != null) {
            FileEditorManager.getInstance(project).openFile(result.fileToOpen().getVirtualFile(), true);
        }
        report(project, result);
    }

    private static void report(Project project, BABjGenerator.Result result) {
        StringBuilder sb = new StringBuilder();
        if (!result.created().isEmpty()) {
            sb.append("Created:\n");
            result.created().forEach(n -> sb.append("  • ").append(n).append('\n'));
        }
        if (!result.skipped().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Skipped:\n");
            result.skipped().forEach(n -> sb.append("  • ").append(n).append('\n'));
        }
        if (sb.isEmpty()) {
            sb.append("Nothing was generated.");
        }
        Messages.showInfoMessage(project, sb.toString(), "BABj CRUD Generator");
    }

    private static PsiClass findEntityClass(AnActionEvent e) {
        PsiJavaFile javaFile = findJavaFile(e);
        if (javaFile == null) {
            return null;
        }
        // Prefer the class under the caret.
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            PsiElement at = javaFile.findElementAt(editor.getCaretModel().getOffset());
            PsiClass cls = PsiTreeUtil.getParentOfType(at, PsiClass.class);
            if (EntityModel.isEntity(cls)) {
                return cls;
            }
        }
        // Fall back to any entity declared in the file, regardless of caret position.
        for (PsiClass cls : javaFile.getClasses()) {
            if (EntityModel.isEntity(cls)) {
                return cls;
            }
        }
        return null;
    }

    private static PsiJavaFile findJavaFile(AnActionEvent e) {
        if (e.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile javaFile) {
            return javaFile;
        }

        Project project = e.getProject();
        if (project == null) {
            return null;
        }
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        }
        if (editor == null) {
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        return psiFile instanceof PsiJavaFile javaFile ? javaFile : null;
    }
}
