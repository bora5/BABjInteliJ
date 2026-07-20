package rs.co.bora5.plugins.babj.lifecycle;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class BABjLifecycleResolverTest extends LightJavaCodeInsightFixtureTestCase {

    public void testResolvesConcreteViewHookAndInheritedHomeHook() {
        addFrameworkStubs();
        myFixture.configureByText("OrderModule.java", """
                package example;

                import jakarta.persistence.Entity;
                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.projections.AbstractDTO;
                import rs.co.bora5.programs.bab.model.AbstractEntity;
                import rs.co.bora5.programs.bab.session.AbstractHome;
                import rs.co.bora5.programs.bab.session.interfaceCheck.CheckValidateHomeInterface;

                @Entity class Order extends AbstractEntity {}
                class OrderDTO extends AbstractDTO<Order> {}
                class OrderHome extends AbstractHome<Order, OrderDTO>
                        implements CheckValidateHomeInterface<Order, OrderDTO, Object> {}
                class OrderView extends GenericView<Order, OrderHome, OrderDTO, Object> {
                    @Override protected boolean beforeValidate(Order entity) { return true; }
                }
                """);

        PsiClass view = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiClass.class).stream()
                .filter(type -> "OrderView".equals(type.getName())).findFirst().orElseThrow();
        BABjLifecycleResolver.ScanResult result = BABjLifecycleResolver.resolve(view);

        assertTrue(result.found());
        LifecycleDiagram validate = result.diagrams().stream()
                .filter(item -> item.event() == LifecycleEvent.VALIDATE)
                .findFirst().orElseThrow();
        assertEquals(LifecycleDiagram.Implementation.OVERRIDDEN,
                node(validate, "viewBefore").implementation());
        assertEquals(LifecycleDiagram.Implementation.INHERITED,
                node(validate, "homeBefore").implementation());
        assertNotNull(node(validate, "viewBefore").target());
        assertNotNull(node(validate, "homeBefore").target());
    }

    public void testWindowOffersCreateAndEditFlows() {
        addFrameworkStubs();
        myFixture.configureByText("EditOrderWindow.java", """
                package example;

                import jakarta.persistence.Entity;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;
                import rs.co.bora5.programs.bab.front.views.projections.AbstractDTO;
                import rs.co.bora5.programs.bab.model.AbstractEntity;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                @Entity class Order extends AbstractEntity {}
                class OrderDTO extends AbstractDTO<Order> {}
                class OrderHome extends AbstractHome<Order, OrderDTO> {}
                class EditOrderWindow extends GenericWindow<Order, OrderHome, OrderDTO, Object> {
                    @Override protected boolean doBeforePersist() { return true; }
                    @Override protected boolean doAfterEdit() { return true; }
                }
                """);

        PsiClass window = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiClass.class).stream()
                .filter(type -> "EditOrderWindow".equals(type.getName())).findFirst().orElseThrow();
        BABjLifecycleResolver.ScanResult result = BABjLifecycleResolver.resolve(window);

        assertTrue(result.diagrams().stream().anyMatch(
                item -> item.event() == LifecycleEvent.CREATE));
        assertTrue(result.diagrams().stream().anyMatch(
                item -> item.event() == LifecycleEvent.EDIT));
    }

    public void testDoesNotOfferLifecycleForFrameworkDefaultsOnly() {
        addFrameworkStubs();
        myFixture.configureByText("PlainOrderView.java", """
                package example;

                import jakarta.persistence.Entity;
                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.projections.AbstractDTO;
                import rs.co.bora5.programs.bab.model.AbstractEntity;
                import rs.co.bora5.programs.bab.session.AbstractHome;
                import rs.co.bora5.programs.bab.session.interfaceCheck.CheckValidateHomeInterface;

                @Entity class PlainOrder extends AbstractEntity {}
                class PlainOrderDTO extends AbstractDTO<PlainOrder> {}
                class PlainOrderHome extends AbstractHome<PlainOrder, PlainOrderDTO>
                        implements CheckValidateHomeInterface<PlainOrder, PlainOrderDTO, Object> {}
                class PlainOrderView extends GenericView<PlainOrder, PlainOrderHome,
                        PlainOrderDTO, Object> {}
                """);

        PsiClass view = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiClass.class).stream()
                .filter(type -> "PlainOrderView".equals(type.getName()))
                .findFirst().orElseThrow();
        BABjLifecycleResolver.ScanResult result = BABjLifecycleResolver.resolve(view);

        assertFalse(result.found());
        assertEmpty(result.diagrams());
    }

    public void testIgnoresEmptyReportHookButOffersNonEmptyOne() {
        addFrameworkStubs();
        myFixture.configureByText("Reports.java", """
                package example;

                import rs.co.bora5.programs.bab.front.windowses.GenericReportWindow;

                class EmptyReport extends GenericReportWindow {
                    @Override protected void doMain() {}
                }
                class UsefulReport extends GenericReportWindow {
                    @Override protected void doMain() { runReport(); }
                    private void runReport() {}
                }
                """);

        var classes = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiClass.class);
        PsiClass empty = classes.stream().filter(type -> "EmptyReport".equals(type.getName()))
                .findFirst().orElseThrow();
        PsiClass useful = classes.stream().filter(type -> "UsefulReport".equals(type.getName()))
                .findFirst().orElseThrow();

        assertFalse(BABjLifecycleResolver.resolve(empty).found());
        assertTrue(BABjLifecycleResolver.resolve(useful).diagrams().stream()
                .anyMatch(item -> item.event() == LifecycleEvent.REPORT_EXECUTE));
    }

    private static LifecycleDiagram.Node node(LifecycleDiagram diagram, String id) {
        return diagram.nodes().stream().filter(node -> node.id().equals(id))
                .findFirst().orElseThrow();
    }

    private void addFrameworkStubs() {
        myFixture.addClass("""
                package jakarta.persistence;
                public @interface Entity {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.model;
                public class AbstractEntity {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views.projections;
                public class AbstractDTO<E> {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session;
                public class AbstractHome<E, D> {
                    public E find(Long id) { return null; }
                    public E save(E entity) { return entity; }
                    public D findDTO(Long id) { return null; }
                    public void remove(E entity) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views;
                public class GenericView<E, H, D, K> {
                    private void handleValidateAction(E entity, Object annotation) {}
                    protected boolean beforeValidate(E entity) { return true; }
                    protected boolean afterValidate(E entity) { return true; }
                    protected boolean beforeDelete() { return true; }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.windowses;
                public class GenericWindow<E, H, D, K> {
                    protected boolean doBeforePersist() { return true; }
                    protected boolean doAfterPersist() { return true; }
                    protected boolean doBeforeEdit() { return true; }
                    protected boolean doAfterEdit() { return true; }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.windowses;
                public class GenericReportWindow {
                    protected void doBeforeExec() {}
                    protected void doMain() {}
                    protected void doAfterExec() {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session.interfaceCheck;
                public interface CheckValidateHomeInterface<E, D, K> {
                    default D validate(E entity, K operator) { return null; }
                    default E beforeValidate(E entity, K operator) { return entity; }
                }
                """);
    }
}
