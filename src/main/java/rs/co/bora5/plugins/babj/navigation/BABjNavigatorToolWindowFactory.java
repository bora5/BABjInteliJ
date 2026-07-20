package rs.co.bora5.plugins.babj.navigation;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.ide.util.PsiNavigationSupport;

/** Creates the BABj module diagram shown in the IDE's right tool-window stripe. */
public class BABjNavigatorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        NavigatorPanel panel = new NavigatorPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        panel.refreshFromEditor();
    }

    private static final class NavigatorPanel extends JPanel implements Disposable {

        private final Project project;
        private final JBLabel status = new JBLabel("Place the caret in a BABj class.");
        private final JTree tree = new Tree(new DefaultMutableTreeNode("No BABj module selected"));

        private NavigatorPanel(Project project) {
            super(new BorderLayout(JBUI.scale(6), JBUI.scale(6)));
            this.project = project;
            setBorder(JBUI.Borders.empty(8));

            JButton refresh = new JButton("Refresh from editor");
            refresh.addActionListener(event -> refreshFromEditor());

            JPanel header = new JPanel(new BorderLayout(JBUI.scale(6), 0));
            header.add(status, BorderLayout.CENTER);
            header.add(refresh, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            tree.setRootVisible(true);
            tree.setShowsRootHandles(true);
            tree.setCellRenderer(new ArtifactRenderer());
            tree.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        navigateToSelection();
                    }
                }
            });
            add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);
        }

        private void refreshFromEditor() {
            if (DumbService.isDumb(project)) {
                status.setText("BABj Navigator is available after indexing.");
                return;
            }
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                applyScanResult(NavigatorScanResult.none());
                return;
            }
            EditorSnapshot snapshot = new EditorSnapshot(
                    editor.getDocument(), editor.getCaretModel().getOffset());
            status.setText("Scanning BABj module…");
            ReadAction.nonBlocking(() -> scanEditor(snapshot))
                    .inSmartMode(project)
                    .expireWith(this)
                    .coalesceBy(this)
                    .finishOnUiThread(ModalityState.any(), this::applyScanResult)
                    .submit(AppExecutorUtil.getAppExecutorService());
        }

        @Override
        public void dispose() {
            // The panel is the lifecycle parent for background scans.
        }

        private NavigatorScanResult scanEditor(EditorSnapshot snapshot) {
            PsiClass context = currentClass(snapshot);
            PsiClass entity = BABjArtifactResolver.entityFor(context);
            if (context == null || entity == null) {
                return NavigatorScanResult.none();
            }

            List<BABjArtifactResolver.Artifact> artifacts =
                    BABjArtifactResolver.relatedArtifacts(context);
            Map<BABjArtifactRole, Integer> counts = new EnumMap<>(BABjArtifactRole.class);
            List<ArtifactDescriptor> nodes = new java.util.ArrayList<>();

            for (BABjArtifactResolver.Artifact artifact : artifacts) {
                counts.merge(artifact.role(), 1, Integer::sum);
                nodes.add(new ArtifactDescriptor(
                        artifact.role().getDisplayName() + " — " + artifact.psiClass().getName(),
                        pointer(artifact.psiClass())));
            }
            for (BABjArtifactRole role : BABjArtifactRole.values()) {
                if (!counts.containsKey(role)) {
                    nodes.add(new ArtifactDescriptor(
                            role.getDisplayName() + " — not found", null));
                }
            }
            return new NavigatorScanResult(entity.getName(), pointer(entity), List.copyOf(nodes));
        }

        private void applyScanResult(NavigatorScanResult result) {
            if (!result.found()) {
                status.setText("Place the caret in a BABj Entity, DTO, Home, View, or Edit window.");
                tree.setModel(new DefaultTreeModel(
                        new DefaultMutableTreeNode("No BABj module selected")));
                return;
            }
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(new ArtifactNode(
                    "BABj module: " + result.entityName(), result.entity()));
            for (ArtifactDescriptor artifact : result.artifacts()) {
                root.add(new DefaultMutableTreeNode(
                        new ArtifactNode(artifact.label(), artifact.target())));
            }
            status.setText("Double-click an artifact to open it.");
            tree.setModel(new DefaultTreeModel(root));
            TreeUtil.expandAll(tree);
        }

        private PsiClass currentClass(EditorSnapshot snapshot) {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(snapshot.document());
            if (file == null) {
                return null;
            }
            PsiElement atCaret = file.findElementAt(snapshot.offset());
            PsiClass psiClass = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
            if (psiClass == null && file instanceof PsiJavaFile javaFile
                    && javaFile.getClasses().length > 0) {
                psiClass = javaFile.getClasses()[0];
            }
            return psiClass;
        }

        private void navigateToSelection() {
            TreePath selection = tree.getSelectionPath();
            if (selection == null
                    || !(selection.getLastPathComponent() instanceof DefaultMutableTreeNode node)
                    || !(node.getUserObject() instanceof ArtifactNode artifactNode)) {
                return;
            }
            NavigationTarget navigation = ReadAction.compute(() -> {
                PsiClass target = artifactNode.target();
                if (target == null || !target.isValid() || target.getContainingFile() == null
                        || target.getContainingFile().getVirtualFile() == null) {
                    return null;
                }
                return new NavigationTarget(target.getContainingFile().getVirtualFile(),
                        target.getTextOffset());
            });
            if (navigation != null) {
                PsiNavigationSupport.getInstance().createNavigatable(
                        project, navigation.file(), navigation.offset())
                        .navigate(true);
            }
        }

        private SmartPsiElementPointer<PsiClass> pointer(PsiClass target) {
            return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(target);
        }

        private record ArtifactNode(String label, SmartPsiElementPointer<PsiClass> pointer) {

            private PsiClass target() {
                        return pointer == null ? null : pointer.getElement();
                    }

                    private boolean hasTarget() {
                        return pointer != null;
                    }

                    @Override
                    public @NotNull String toString() {
                        return label;
                    }
                }

        private static final class ArtifactRenderer extends DefaultTreeCellRenderer {
            @Override
            public Component getTreeCellRendererComponent(javax.swing.JTree tree, Object value,
                                                          boolean selected, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                Component component = super.getTreeCellRendererComponent(
                        tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof ArtifactNode artifactNode) {
                    setText(artifactNode.toString());
                    if (artifactNode.hasTarget()) {
                        setIcon(IconLoader.getIcon(
                                "/icons/babjNavigate.svg", BABjNavigatorToolWindowFactory.class));
                    }
                }
                return component;
            }
        }

        private record EditorSnapshot(Document document, int offset) {
        }

        private record ArtifactDescriptor(String label,
                                          SmartPsiElementPointer<PsiClass> target) {
        }

        private record NavigatorScanResult(String entityName,
                                           SmartPsiElementPointer<PsiClass> entity,
                                           List<ArtifactDescriptor> artifacts) {
            private static NavigatorScanResult none() {
                return new NavigatorScanResult(null, null, List.of());
            }

            private boolean found() {
                return entity != null;
            }
        }

        private record NavigationTarget(VirtualFile file, int offset) {
        }
    }
}
