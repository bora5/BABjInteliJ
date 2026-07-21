package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class LegacyMainMenuInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addFrameworkStubs();
        myFixture.enableInspections(new LegacyMainMenuInspection());
    }

    public void testMigratesSectionsRolesAndDuplicateAdds() {
        myFixture.configureByText("MainView.java", """
                package example;

                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.component.html.H6;
                import com.vaadin.flow.component.tabs.Tab;
                import org.vaadin.lineawesome.LineAwesomeIcon;
                import rs.co.bora5.programs.bab.front.GenericMainView;
                import java.util.List;
                import java.util.Set;

                class Roles {
                    static final String USER = "USER";
                    static final String ADMIN = "ADMIN";
                }
                class OrdersView extends Component {}
                class SettingsView extends Component {}
                class DeadView extends Component {}

                class MainView extends GenericMainView {
                    @Override
                    public void <caret>createMenuItems(List<Tab> tabs,
                            Set<Class<? extends Component>> routes) {
                        H6 lblFavourite = new H6("Favourite");
                        H6 lblOrders = new H6("Orders");
                        lblFavourite.addComponentAsFirst(LineAwesomeIcon.STAR_SOLID.create());
                        lblOrders.addComponentAsFirst(LineAwesomeIcon.TRUCK_SOLID.create());

                        Tab btnOrders = new FavTab("Orders", OrdersView.class);
                        Tab btnSettings = new FavTab("Settings", SettingsView.class);
                        Tab btnDead = new FavTab("Dead", DeadView.class);
                        btnOrders.setVisible(false);
                        btnSettings.setVisible(false);
                        btnDead.setVisible(false);

                        Tab tabFavourite = new Tab(lblFavourite);
                        tabFavourite.setEnabled(false);
                        tabs.add(tabFavourite);
                        Tab tabOrders = new Tab(lblOrders);
                        tabOrders.setEnabled(false);
                        tabs.add(tabOrders);
                        tabs.add(btnOrders);
                        tabs.add(btnOrders);
                        tabs.add(btnSettings);
                        tabs.add(btnDead);

                        getOperaterEJB().getRoles(getOperater()).parallelStream().forEach(r -> {
                            switch (r) {
                                case Roles.USER:
                                    btnOrders.setVisible(true);
                                    routes.add(OrdersView.class);
                                    break;
                                case Roles.ADMIN:
                                    btnOrders.setVisible(true);
                                    btnSettings.setVisible(true);
                                    routes.add(SettingsView.class);
                                    break;
                                default:
                                    break;
                            }
                        });
                    }
                }
                """);

        IntentionAction action = myFixture.findSingleIntention("Replace with createMenuDefinition()");
        myFixture.launchAction(action);

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("protected MenuDefinition createMenuDefinition()"));
        assertTrue(result.contains("MenuDefinition.section(\"orders\", \"Orders\","
                + " LineAwesomeIcon.TRUCK_SOLID, 10"));
        assertTrue(result.contains("MenuDefinition.item(\"Orders\", OrdersView.class, 10,"
                + " Roles.USER, Roles.ADMIN)"));
        assertTrue(result.contains("MenuDefinition.item(\"Settings\", SettingsView.class, 20,"
                + " Roles.ADMIN)"));
        assertEquals(1, occurrences(result, "OrdersView.class"));
        assertFalse(result.contains("DeadView.class"));
        assertFalse(result.contains("parallelStream"));
        assertFalse(result.contains("createMenuItems"));
    }

    public void testDoesNotOfferFixWhenMenuContainsCustomBehavior() {
        myFixture.configureByText("MainView.java", """
                package example;

                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.component.html.H6;
                import com.vaadin.flow.component.tabs.Tab;
                import org.vaadin.lineawesome.LineAwesomeIcon;
                import rs.co.bora5.programs.bab.front.GenericMainView;
                import java.util.List;
                import java.util.Set;

                class OrdersView extends Component {}
                class MainView extends GenericMainView {
                    @Override
                    public void <caret>createMenuItems(List<Tab> tabs,
                            Set<Class<? extends Component>> routes) {
                        H6 lblOrders = new H6("Orders");
                        lblOrders.addComponentAsFirst(LineAwesomeIcon.TRUCK_SOLID.create());
                        Tab btnOrders = new FavTab("Orders", OrdersView.class);
                        Tab tabOrders = new Tab(lblOrders);
                        tabs.add(tabOrders);
                        tabs.add(btnOrders);
                        configureMenu();
                    }
                    void configureMenu() {}
                }
                """);

        myFixture.doHighlighting();
        assertFalse(myFixture.getAvailableIntentions().stream()
                .anyMatch(action -> "Replace with createMenuDefinition()".equals(action.getText())));
    }

    private static int occurrences(String text, String fragment) {
        return (text.length() - text.replace(fragment, "").length()) / fragment.length();
    }

    private void addFrameworkStubs() {
        myFixture.addClass("""
                package com.vaadin.flow.component;
                public class Component {}
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.html;
                public class H6 {
                    public H6(String caption) {}
                    public void addComponentAsFirst(Object component) {}
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.tabs;
                public class Tab {
                    public Tab(Object... components) {}
                    public void setEnabled(boolean enabled) {}
                    public void setVisible(boolean visible) {}
                }
                """);
        myFixture.addClass("""
                package org.vaadin.lineawesome;
                public enum LineAwesomeIcon {
                    STAR_SOLID, TRUCK_SOLID;
                    public Object create() { return null; }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.menu;
                import com.vaadin.flow.component.Component;
                import org.vaadin.lineawesome.LineAwesomeIcon;
                public class MenuDefinition {
                    public static MenuDefinition menu(Object... sections) { return null; }
                    public static Object section(String id, String caption, LineAwesomeIcon icon,
                            int order, Object... items) { return null; }
                    public static Object item(String caption, Class<? extends Component> target,
                            int order, String... roles) { return null; }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front;
                import com.vaadin.flow.component.Component;
                import com.vaadin.flow.component.tabs.Tab;
                import java.util.List;
                import java.util.Set;
                public class GenericMainView {
                    public void createMenuItems(List<Tab> tabs,
                            Set<Class<? extends Component>> routes) {}
                    protected rs.co.bora5.programs.bab.front.menu.MenuDefinition
                            createMenuDefinition() { return null; }
                    protected final class FavTab extends Tab {
                        public FavTab(String caption, Class<? extends Component> target) {}
                    }
                    protected Home getOperaterEJB() { return null; }
                    protected Long getOperater() { return null; }
                    protected static class Home {
                        public Set<String> getRoles(Long id) { return Set.of(); }
                    }
                }
                """);
    }
}
