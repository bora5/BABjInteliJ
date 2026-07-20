package rs.co.bora5.plugins.babj.lifecycle;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;

/** Independent visual navigator for BAB template-method execution flows. */
public final class BABjLifecycleToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LifecyclePanel panel = new LifecyclePanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        panel.refreshFromEditor();
    }

    private static final class LifecyclePanel extends JPanel implements Disposable {
        private final Project project;
        private final JBLabel status = new JBLabel("Place the caret in a BABj class.");
        private final ComboBox<LifecycleDiagram> eventCombo = new ComboBox<>();
        private final LifecycleDiagramPanel diagram = new LifecycleDiagramPanel(this::navigate);
        private final Timer refreshTimer;
        private boolean applyingResult;

        private LifecyclePanel(Project project) {
            super(new BorderLayout(JBUI.scale(6), JBUI.scale(6)));
            this.project = project;
            setBorder(JBUI.Borders.empty(8));

            JButton refresh = new JButton("Refresh from editor");
            refresh.addActionListener(event -> refreshFromEditor());
            eventCombo.addActionListener(event -> {
                if (!applyingResult) {
                    diagram.setDiagram((LifecycleDiagram) eventCombo.getSelectedItem());
                }
            });

            JPanel controls = new JPanel();
            controls.add(new JBLabel("Event:"));
            controls.add(eventCombo);
            controls.add(refresh);

            JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), JBUI.scale(4)));
            header.add(status, BorderLayout.CENTER);
            header.add(controls, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            add(ScrollPaneFactory.createScrollPane(diagram, true), BorderLayout.CENTER);
            add(new JBLabel("Blue hook: overridden  •  Gray hook: inherited  •  "
                    + "Green: side effect  •  Double-click a coded step to navigate."),
                    BorderLayout.SOUTH);

            refreshTimer = new Timer(400, event -> refreshFromEditor());
            refreshTimer.setRepeats(false);
            project.getMessageBus().connect(this).subscribe(
                    FileEditorManagerListener.FILE_EDITOR_MANAGER,
                    new FileEditorManagerListener() {
                        @Override
                        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                            refreshTimer.restart();
                        }
                    });
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                    new DocumentListener() {
                        @Override
                        public void documentChanged(@NotNull DocumentEvent event) {
                            Editor editor = FileEditorManager.getInstance(project)
                                    .getSelectedTextEditor();
                            if (editor != null && editor.getDocument() == event.getDocument()) {
                                refreshTimer.restart();
                            }
                        }
                    }, this);
        }

        private void refreshFromEditor() {
            if (DumbService.isDumb(project)) {
                status.setText("Lifecycle Navigator is available after indexing.");
                return;
            }
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                applyScanResult(BABjLifecycleResolver.ScanResult.none());
                return;
            }
            EditorSnapshot snapshot = new EditorSnapshot(
                    editor.getDocument(), editor.getCaretModel().getOffset());
            status.setText("Resolving BAB lifecycle…");
            PsiDocumentManager.getInstance(project).performForCommittedDocument(
                    snapshot.document(), () -> submitScan(snapshot));
        }

        private void submitScan(EditorSnapshot snapshot) {
            ReadAction.nonBlocking(() -> scanEditor(snapshot))
                    .inSmartMode(project)
                    .expireWith(this)
                    .coalesceBy(this)
                    .finishOnUiThread(ModalityState.any(), this::applyScanResult)
                    .submit(AppExecutorUtil.getAppExecutorService());
        }

        private BABjLifecycleResolver.ScanResult scanEditor(EditorSnapshot snapshot) {
            return BABjLifecycleResolver.resolve(currentClass(snapshot));
        }

        private PsiClass currentClass(EditorSnapshot snapshot) {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(snapshot.document());
            if (file == null) {
                return null;
            }
            int textLength = snapshot.document().getTextLength();
            int offset = textLength == 0 ? 0 : Math.min(snapshot.offset(), textLength - 1);
            PsiElement atCaret = file.findElementAt(offset);
            PsiClass psiClass = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
            if (psiClass == null && file instanceof PsiJavaFile javaFile
                    && javaFile.getClasses().length > 0) {
                psiClass = javaFile.getClasses()[0];
            }
            return psiClass;
        }

        private void applyScanResult(BABjLifecycleResolver.ScanResult result) {
            LifecycleEvent previous = selectedEvent();
            applyingResult = true;
            try {
                List<LifecycleDiagram> diagrams = result.diagrams();
                eventCombo.setModel(new DefaultComboBoxModel<>(
                        diagrams.toArray(LifecycleDiagram[]::new)));
                if (previous != null) {
                    diagrams.stream().filter(item -> item.event() == previous).findFirst()
                            .ifPresent(eventCombo::setSelectedItem);
                }
                LifecycleDiagram selected = (LifecycleDiagram) eventCombo.getSelectedItem();
                diagram.setDiagram(selected);
                eventCombo.setEnabled(!diagrams.isEmpty());
            } finally {
                applyingResult = false;
            }

            if (!result.found()) {
                status.setText(result.contextName() == null
                        ? "Place the caret in a BABj View, Home, Window, Entity, DTO, or report window."
                        : "No supported lifecycle was found for " + result.contextName() + ".");
            } else {
                status.setText(result.contextName() + " — " + result.diagrams().size()
                        + " lifecycle(s) available.");
            }
        }

        private LifecycleEvent selectedEvent() {
            Object item = eventCombo.getSelectedItem();
            return item instanceof LifecycleDiagram selected ? selected.event() : null;
        }

        private void navigate(LifecycleDiagram.Node node) {
            NavigationTarget target = ApplicationManager.getApplication().runReadAction(
                    (Computable<NavigationTarget>) () -> navigationTarget(node));
            if (target != null) {
                PsiNavigationSupport.getInstance().createNavigatable(
                        project, target.file(), target.offset()).navigate(true);
            }
        }

        private NavigationTarget navigationTarget(LifecycleDiagram.Node node) {
            if (node.target() == null) {
                return null;
            }
            PsiMethod method = node.target().getElement();
            if (method == null || !method.isValid() || method.getContainingFile() == null
                    || method.getContainingFile().getVirtualFile() == null) {
                return null;
            }
            return new NavigationTarget(method.getContainingFile().getVirtualFile(),
                    method.getTextOffset());
        }

        @Override
        public void dispose() {
            refreshTimer.stop();
        }

        private record EditorSnapshot(Document document, int offset) {
        }

        private record NavigationTarget(VirtualFile file, int offset) {
        }
    }
}
