package rs.co.bora5.plugins.babj.agent;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.concurrency.AppExecutorUtil;

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
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        panel.refresh();
    }

    private static final class StudioPanel extends JPanel implements Disposable {
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
            status.setText("Scanning BABj agent topology…");
            ReadAction.nonBlocking(this::scanProject)
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

        private ScanResult scanProject() {
            PsiClass agentContract = find(AGENT, GlobalSearchScope.allScope(project));
            PsiClass eventContract = find(EVENT, GlobalSearchScope.allScope(project));
            if (agentContract == null || eventContract == null) {
                return ScanResult.apiMissing();
            }

            List<PsiClass> eventTypes = concreteInheritors(
                    eventContract, GlobalSearchScope.allScope(project));
            List<EventOption> events = eventTypes.stream()
                    .map(type -> new EventOption(type.getName(), type.getQualifiedName()))
                    .toList();

            List<AgentDescriptor> discoveredAgents = concreteInheritors(
                    agentContract, GlobalSearchScope.projectScope(project)).stream()
                    .map(agent -> describe(agent, eventTypes))
                    .toList();
            return new ScanResult(true, events, discoveredAgents);
        }

        private void applyScanResult(ScanResult result) {
            if (!result.apiAvailable()) {
                agents = List.of();
                eventCombo.setModel(new DefaultComboBoxModel<>());
                status.setText("BAB agent API was not found in this project.");
                tree.setModel(new DefaultTreeModel(
                        new DefaultMutableTreeNode("BAB dependency not found")));
                return;
            }
            eventCombo.setModel(new DefaultComboBoxModel<>(
                    result.events().toArray(EventOption[]::new)));
            agents = result.agents();
            render(false);
        }

        private AgentDescriptor describe(PsiClass agent, List<PsiClass> eventTypes) {
            List<PsiClass> events = new ArrayList<>();
            for (PsiMethod method : agent.findMethodsByName("supports", false)) {
                events.addAll(referencedTypes(method, EVENT));
            }
            List<TypeDescriptor> supportedTypes = distinct(events);
            Set<String> matchingEvents = new LinkedHashSet<>();
            for (PsiClass eventType : eventTypes) {
                for (PsiClass supportedType : events) {
                    if (eventType.isEquivalentTo(supportedType)
                            || InheritanceUtil.isInheritorOrSelf(eventType, supportedType, true)) {
                        matchingEvents.add(eventType.getQualifiedName());
                        break;
                    }
                }
            }
            return new AgentDescriptor(descriptor(agent), supportedTypes,
                    distinct(referencedTypes(agent, CRITERION)),
                    distinct(referencedTypes(agent, ACTION)), Set.copyOf(matchingEvents));
        }

        private void render(boolean simulation) {
            EventOption event = simulation ? (EventOption) eventCombo.getSelectedItem() : null;
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                    new StudioNode("BABj agents (" + agents.size() + ")", null));
            int matches = 0;
            for (AgentDescriptor agent : agents) {
                boolean reacts = event != null
                        && agent.matchingEvents().contains(event.qualifiedName());
                if (reacts) {
                    matches++;
                }
                String prefix = event == null ? "" : reacts ? "✓ reacts — " : "○ ignores — ";
                DefaultMutableTreeNode agentNode = new DefaultMutableTreeNode(
                        new StudioNode(prefix + agent.agent().name(), agent.agent().target()));
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
                                             List<TypeDescriptor> types) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(
                    new StudioNode(name + " (" + types.size() + ")", null));
            for (TypeDescriptor type : types) {
                group.add(new DefaultMutableTreeNode(
                        new StudioNode(type.name(), type.target())));
            }
            if (types.isEmpty()) {
                group.add(new DefaultMutableTreeNode(new StudioNode("No static reference found", null)));
            }
            return group;
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

        private List<TypeDescriptor> distinct(List<PsiClass> classes) {
            Map<String, TypeDescriptor> result = new LinkedHashMap<>();
            for (PsiClass psiClass : classes) {
                if (psiClass.getQualifiedName() != null) {
                    result.put(psiClass.getQualifiedName(), descriptor(psiClass));
                }
            }
            return List.copyOf(result.values());
        }

        private TypeDescriptor descriptor(PsiClass psiClass) {
            return new TypeDescriptor(psiClass.getName(), psiClass.getQualifiedName(),
                    pointer(psiClass));
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
            NavigationTarget navigation = ApplicationManager.getApplication().runReadAction(
                    (Computable<NavigationTarget>) () -> {
                        PsiClass target = studioNode.target().getElement();
                        if (target == null || target.getContainingFile() == null
                                || target.getContainingFile().getVirtualFile() == null) {
                            return null;
                        }
                        return new NavigationTarget(target.getContainingFile().getVirtualFile(),
                                target.getTextOffset());
                    });
            if (navigation != null) {
                PsiNavigationSupport.getInstance().createNavigatable(project,
                        navigation.file(), navigation.offset())
                        .navigate(true);
            }
        }
    }

    private record ScanResult(boolean apiAvailable, List<EventOption> events,
                              List<AgentDescriptor> agents) {
        private static ScanResult apiMissing() {
            return new ScanResult(false, List.of(), List.of());
        }
    }

    private record AgentDescriptor(TypeDescriptor agent, List<TypeDescriptor> supports,
                                   List<TypeDescriptor> criteria, List<TypeDescriptor> actions,
                                   Set<String> matchingEvents) {
    }

    private record TypeDescriptor(String name, String qualifiedName,
                                  SmartPsiElementPointer<PsiClass> target) {
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

    private record NavigationTarget(VirtualFile file, int offset) {
    }
}
