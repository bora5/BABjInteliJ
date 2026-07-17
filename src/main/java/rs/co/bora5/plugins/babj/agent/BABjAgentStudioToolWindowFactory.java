package rs.co.bora5.plugins.babj.agent;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;

/** Static BAB agent topology browser and event-routing simulator. */
public class BABjAgentStudioToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final String AGENT = "rs.co.bora5.programs.bab.agent.Agent";
    private static final String EVENT = "rs.co.bora5.programs.bab.agent.events.AgentEvent";
    private static final String ACTION = "rs.co.bora5.programs.bab.agent.action.AgentAction";
    private static final String CRITERION = "rs.co.bora5.programs.bab.agent.criterion.SafetyCriterion";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        StudioPanel panel = new StudioPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
        panel.refresh();
    }

    private static final class StudioPanel extends JPanel {
        private final Project project;
        private final JBLabel status = new JBLabel();
        private final JTree tree = new JTree(new DefaultMutableTreeNode("No agents discovered"));
        private final ComboBox<EventOption> eventCombo = new ComboBox<>();
        private List<AgentDescriptor> agents = List.of();

        private StudioPanel(Project project) {
            super(new BorderLayout(JBUI.scale(6), JBUI.scale(6)));
            this.project = project;
            setBorder(JBUI.Borders.empty(8));

            JButton refresh = new JButton("Scan project");
            refresh.addActionListener(event -> refresh());
            JButton simulate = new JButton("Simulate event");
            simulate.addActionListener(event -> render(true));

            JPanel controls = new JPanel();
            controls.add(new JBLabel("Event:"));
            controls.add(eventCombo);
            controls.add(simulate);
            controls.add(refresh);

            JPanel header = new JPanel(new BorderLayout());
            header.add(status, BorderLayout.CENTER);
            header.add(controls, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            tree.setRootVisible(true);
            tree.setShowsRootHandles(true);
            tree.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        navigate();
                    }
                }
            });
            add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);
        }

        private void refresh() {
            if (DumbService.isDumb(project)) {
                status.setText("Agent Studio is available after indexing.");
                return;
            }
            PsiClass agentContract = find(AGENT, GlobalSearchScope.allScope(project));
            PsiClass eventContract = find(EVENT, GlobalSearchScope.allScope(project));
            if (agentContract == null || eventContract == null) {
                status.setText("BAB agent API was not found in this project.");
                tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("BAB dependency not found")));
                return;
            }

            List<PsiClass> eventTypes = concreteInheritors(
                    eventContract, GlobalSearchScope.allScope(project));
            eventCombo.setModel(new DefaultComboBoxModel<>(eventTypes.stream()
                    .map(type -> new EventOption(type.getName(), type.getQualifiedName()))
                    .toArray(EventOption[]::new)));

            agents = concreteInheritors(agentContract, GlobalSearchScope.projectScope(project)).stream()
                    .map(this::describe)
                    .toList();
            render(false);
        }

        private AgentDescriptor describe(PsiClass agent) {
            List<PsiClass> events = new ArrayList<>();
            for (PsiMethod method : agent.findMethodsByName("supports", false)) {
                events.addAll(referencedTypes(method, EVENT));
            }
            return new AgentDescriptor(pointer(agent), distinct(events),
                    distinct(referencedTypes(agent, CRITERION)),
                    distinct(referencedTypes(agent, ACTION)));
        }

        private void render(boolean simulation) {
            EventOption event = simulation ? (EventOption) eventCombo.getSelectedItem() : null;
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                    new StudioNode("BABj agents (" + agents.size() + ")", null));
            int matches = 0;
            for (AgentDescriptor agent : agents) {
                PsiClass agentClass = agent.agent().getElement();
                if (agentClass == null) {
                    continue;
                }
                boolean reacts = event != null && agent.supports().stream()
                        .anyMatch(type -> supports(type, event.qualifiedName()));
                if (reacts) {
                    matches++;
                }
                String prefix = event == null ? "" : reacts ? "✓ reacts — " : "○ ignores — ";
                DefaultMutableTreeNode agentNode = new DefaultMutableTreeNode(
                        new StudioNode(prefix + agentClass.getName(), agent.agent()));
                agentNode.add(group("Events", agent.supports()));
                agentNode.add(group("Safety criteria", agent.criteria()));
                agentNode.add(group("Actions", agent.actions()));
                root.add(agentNode);
            }
            tree.setModel(new DefaultTreeModel(root));
            TreeUtil.expandAll(tree);
            status.setText(event == null
                    ? "Static topology: events → agents → safety criteria → actions."
                    : matches + " agent(s) statically match " + event + ".");
        }

        private DefaultMutableTreeNode group(String name,
                                             List<SmartPsiElementPointer<PsiClass>> types) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(
                    new StudioNode(name + " (" + types.size() + ")", null));
            for (SmartPsiElementPointer<PsiClass> type : types) {
                PsiClass psiClass = type.getElement();
                if (psiClass != null) {
                    group.add(new DefaultMutableTreeNode(new StudioNode(psiClass.getName(), type)));
                }
            }
            if (types.isEmpty()) {
                group.add(new DefaultMutableTreeNode(new StudioNode("No static reference found", null)));
            }
            return group;
        }

        private boolean supports(SmartPsiElementPointer<PsiClass> supported, String selectedFqn) {
            PsiClass supportedClass = supported.getElement();
            PsiClass selectedClass = find(selectedFqn, GlobalSearchScope.allScope(project));
            return supportedClass != null && selectedClass != null
                    && (supportedClass.isEquivalentTo(selectedClass)
                    || InheritanceUtil.isInheritorOrSelf(selectedClass, supportedClass, true));
        }

        private List<PsiClass> referencedTypes(PsiElement root, String contractFqn) {
            Map<String, PsiClass> result = new LinkedHashMap<>();
            for (PsiJavaCodeReferenceElement reference
                    : PsiTreeUtil.findChildrenOfType(root, PsiJavaCodeReferenceElement.class)) {
                if (reference.resolve() instanceof PsiClass psiClass
                        && InheritanceUtil.isInheritor(psiClass, contractFqn)
                        && psiClass.getQualifiedName() != null) {
                    result.put(psiClass.getQualifiedName(), psiClass);
                }
            }
            return result.values().stream()
                    .sorted(Comparator.comparing(PsiClass::getName,
                            Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }

        private List<PsiClass> concreteInheritors(PsiClass base, GlobalSearchScope scope) {
            Collection<PsiClass> found = ClassInheritorsSearch.search(base, scope, true).findAll();
            return found.stream()
                    .filter(type -> !type.isInterface()
                            && !type.hasModifierProperty(PsiModifier.ABSTRACT)
                            && type.getQualifiedName() != null)
                    .sorted(Comparator.comparing(PsiClass::getName,
                            Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }

        private List<SmartPsiElementPointer<PsiClass>> distinct(List<PsiClass> classes) {
            Map<String, SmartPsiElementPointer<PsiClass>> result = new LinkedHashMap<>();
            for (PsiClass psiClass : classes) {
                if (psiClass.getQualifiedName() != null) {
                    result.put(psiClass.getQualifiedName(), pointer(psiClass));
                }
            }
            return List.copyOf(result.values());
        }

        private SmartPsiElementPointer<PsiClass> pointer(PsiClass psiClass) {
            return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiClass);
        }

        private PsiClass find(String fqn, GlobalSearchScope scope) {
            return JavaPsiFacade.getInstance(project).findClass(fqn, scope);
        }

        private void navigate() {
            TreePath selection = tree.getSelectionPath();
            if (selection == null
                    || !(selection.getLastPathComponent() instanceof DefaultMutableTreeNode node)
                    || !(node.getUserObject() instanceof StudioNode studioNode)
                    || studioNode.target() == null) {
                return;
            }
            PsiClass target = studioNode.target().getElement();
            if (target != null && target.getContainingFile() != null
                    && target.getContainingFile().getVirtualFile() != null) {
                PsiNavigationSupport.getInstance().createNavigatable(project,
                        target.getContainingFile().getVirtualFile(), target.getTextOffset())
                        .navigate(true);
            }
        }
    }

    private record AgentDescriptor(SmartPsiElementPointer<PsiClass> agent,
                                   List<SmartPsiElementPointer<PsiClass>> supports,
                                   List<SmartPsiElementPointer<PsiClass>> criteria,
                                   List<SmartPsiElementPointer<PsiClass>> actions) {
    }

    private record EventOption(String name, String qualifiedName) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record StudioNode(String label, SmartPsiElementPointer<PsiClass> target) {
        @Override
        public String toString() {
            return label;
        }
    }
}
