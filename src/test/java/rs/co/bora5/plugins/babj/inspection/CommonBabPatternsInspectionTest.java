package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class CommonBabPatternsInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addFrameworkStubs();
        myFixture.enableInspections(new CommonBabPatternsInspection());
    }

    public void testReplacesMissingSingleResultWithoutHidingOtherErrors() {
        myFixture.configureByText("FirmaHome.java", """
                package example;

                import jakarta.persistence.EntityManager;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                class FirmaHome extends AbstractHome {
                    EntityManager entityManager;

                    String find(String pib) {
                        try {
                            return entityManager.createQuery(
                                    "select f.pib from Firma f where f.pib = ?1", String.class)
                                    .setParameter(1, pib).<caret>getSingleResult();
                        } catch (Exception e) {
                            return null;
                        }
                    }
                }
                """);

        launch("Replace with resultOrNull()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("return resultOrNull(entityManager.createQuery("));
        assertFalse(result.contains("catch (Exception"));
    }

    public void testDoesNotReplaceSingleResultCatchWithLogging() {
        myFixture.configureByText("FirmaHome.java", """
                package example;

                import jakarta.persistence.EntityManager;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                class FirmaHome extends AbstractHome {
                    EntityManager entityManager;
                    String find() {
                        try {
                            return entityManager.createQuery("select f.pib from Firma f", String.class)
                                    .<caret>getSingleResult();
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                            return null;
                        }
                    }
                }
                """);

        assertNoQuickFix("Replace with resultOrNull()");
    }

    public void testReplacesSemanticNotification() {
        myFixture.configureByText("View.java", """
                package example;
                import com.vaadin.flow.component.notification.Notification;
                import com.vaadin.flow.component.notification.NotificationVariant;
                class View {
                    void save() {
                        Notification.show("Saved").<caret>addThemeVariants(
                                NotificationVariant.LUMO_SUCCESS);
                    }
                }
                """);

        launch("Replace with NotificationFactory.showSuccess()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("NotificationFactory.showSuccess(\"Saved\");"));
        assertFalse(result.contains("NotificationVariant"));
    }

    public void testIgnoresComputedNotificationVariant() {
        myFixture.configureByText("View.java", """
                package example;
                import com.vaadin.flow.component.notification.Notification;
                import com.vaadin.flow.component.notification.NotificationVariant;
                class View {
                    NotificationVariant variant() {
                        return NotificationVariant.LUMO_SUCCESS;
                    }
                    void save() {
                        Notification.show("Saved").addThemeVariants(<caret>variant());
                    }
                }
                """);

        myFixture.doHighlighting();
        assertNoQuickFix("Replace with NotificationFactory.showSuccess()");
    }

    public void testReplacesImmediatelyOpenedLocalConfirmDialog() {
        myFixture.configureByText("View.java", """
                package example;
                import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
                class View {
                    void delete() {
                        ConfirmDialog dialog = new <caret>ConfirmDialog("Delete", "Continue?",
                                "Yes", event -> run(), "No", event -> {});
                        dialog.open();
                    }
                    void run() {}
                }
                """);

        launch("Replace with ConfirmDialogs.open()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("ConfirmDialogs.open(\"Delete\", \"Continue?\", \"Yes\""));
        assertFalse(result.contains("dialog.open()"));
        assertFalse(result.contains("new ConfirmDialog"));
    }

    public void testReplacesEnumComboBoxAndLabelGenerator() {
        myFixture.configureByText("EditWindow.java", """
                package example;
                import com.vaadin.flow.component.combobox.ComboBox;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;
                enum Tip { A, B; String title() { return name(); } }
                class EditWindow extends GenericWindow {
                    ComboBox<Tip> cbTip;
                    void createContent() {
                        cbTip = new <caret>ComboBox<>("Type");
                        cbTip.setItems(Tip.values());
                        cbTip.setItemLabelGenerator(Tip::title);
                    }
                }
                """);

        launch("Replace with createEnumComboBox()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("cbTip = createEnumComboBox(\"Type\", Tip.class, Tip::title);"));
        assertFalse(result.contains("setItems"));
        assertFalse(result.contains("setItemLabelGenerator"));
    }

    public void testReplacesSharedUiDateFormat() {
        myFixture.configureByText("View.java", """
                package example;
                import java.time.format.DateTimeFormatter;
                class View {
                    String format(java.time.LocalDate value) {
                        return value.format(DateTimeFormatter.<caret>ofPattern("dd-MM-yyyy"));
                    }
                }
                """);

        launch("Replace with BabDateFormats.UI_DATE");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("value.format(BabDateFormats.UI_DATE)"));
        assertFalse(result.contains("DateTimeFormatter"));
    }

    public void testReplacesNestedAsyncUiCallbacksAndTriggerManagement() {
        myFixture.configureByText("EditWindow.java", """
                package example;
                import com.vaadin.flow.component.UI;
                import com.vaadin.flow.component.button.Button;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;
                class Client {
                    java.util.concurrent.CompletableFuture<String> load() { return null; }
                }
                class EditWindow extends GenericWindow {
                    Button button;
                    Client client;
                    String field;
                    void load() {
                        button.setEnabled(false);
                        client.load()
                                .thenAccept(value -> getUI().ifPresent(ui -> ui.access(() -> {
                                    field = value;
                                })))
                                .exceptionally(error -> {
                                    getUI().ifPresent(ui -> ui.access(() -> {
                                        field = error.getMessage();
                                    }));
                                    return null;
                                })
                                .<caret>whenComplete((result, error) -> getUI().ifPresent(
                                        ui -> ui.access(() -> button.setEnabled(true))));
                    }
                }
                """);

        launch("Replace with handleUiTask()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("handleUiTask(client.load(), button"));
        assertTrue(result.contains("field = value"));
        assertTrue(result.contains("field = error.getMessage()"));
        assertFalse(result.contains("ui.access"));
        assertFalse(result.contains("whenComplete"));
        assertFalse(result.contains("button.setEnabled(false)"));
    }

    private void launch(String text) {
        IntentionAction action = myFixture.filterAvailableIntentions(text).stream()
                .findFirst().orElse(null);
        assertNotNull("Expected quick fix: " + text, action);
        myFixture.launchAction(action);
    }

    private void assertNoQuickFix(String text) {
        assertNull(myFixture.filterAvailableIntentions(text).stream().findFirst().orElse(null));
    }

    private void addFrameworkStubs() {
        myFixture.addClass("""
                package jakarta.persistence;
                public interface TypedQuery<T> {
                    TypedQuery<T> setParameter(int position, Object value);
                    T getSingleResult();
                }
                """);
        myFixture.addClass("""
                package jakarta.persistence;
                public interface EntityManager {
                    <T> TypedQuery<T> createQuery(String query, Class<T> type);
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session;
                public abstract class AbstractHome {
                    protected <T> T resultOrNull(jakarta.persistence.TypedQuery<T> query) { return null; }
                    protected <T> T resultOrDefault(jakarta.persistence.TypedQuery<T> query, T fallback) {
                        return fallback;
                    }
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.notification;
                public enum NotificationVariant {
                    LUMO_SUCCESS, LUMO_ERROR, LUMO_CONTRAST, LUMO_PRIMARY
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.notification;
                public class Notification {
                    public static Notification show(String message) { return null; }
                    public void addThemeVariants(NotificationVariant... variants) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.utils;
                public class NotificationFactory {
                    public static Object showSuccess(String message) { return null; }
                    public static Object showError(String message) { return null; }
                    public static Object showWarning(String message) { return null; }
                    public static Object showInfo(String message) { return null; }
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component;
                @FunctionalInterface
                public interface ComponentEventListener<T> { void onComponentEvent(T event); }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.confirmdialog;
                import com.vaadin.flow.component.ComponentEventListener;
                public class ConfirmDialog {
                    public static class ConfirmEvent {}
                    public static class CancelEvent {}
                    public ConfirmDialog(String h, String t, String ct,
                            ComponentEventListener<ConfirmEvent> c, String xt,
                            ComponentEventListener<CancelEvent> x) {}
                    public void open() {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.utils;
                public class ConfirmDialogs {
                    public static Object open(Object... args) { return null; }
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component;
                public interface HasEnabled { void setEnabled(boolean enabled); }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component;
                public class UI {
                    public void access(Runnable command) {}
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.button;
                public class Button implements com.vaadin.flow.component.HasEnabled {
                    public void setEnabled(boolean enabled) {}
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.combobox;
                public class ComboBox<T> {
                    public ComboBox() {}
                    public ComboBox(String label) {}
                    public void setItems(T... items) {}
                    public void setLabel(String label) {}
                    public void setItemLabelGenerator(java.util.function.Function<T, String> labels) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.windowses;
                import com.vaadin.flow.component.HasEnabled;
                import com.vaadin.flow.component.combobox.ComboBox;
                public class GenericWindow {
                    protected <E extends Enum<E>> ComboBox<E> createEnumComboBox(
                            String caption, Class<E> type) { return null; }
                    protected <E extends Enum<E>> ComboBox<E> createEnumComboBox(
                            String caption, Class<E> type,
                            java.util.function.Function<E, String> labels) { return null; }
                    protected <T> java.util.concurrent.CompletionStage<T> handleUiTask(
                            java.util.concurrent.CompletionStage<T> stage, HasEnabled trigger,
                            java.util.function.Consumer<T> success,
                            java.util.function.Consumer<Throwable> failure) { return stage; }
                    protected java.util.Optional<com.vaadin.flow.component.UI> getUI() {
                        return java.util.Optional.empty();
                    }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.utils;
                public class BabDateFormats {
                    public static final java.time.format.DateTimeFormatter UI_DATE = null;
                }
                """);
    }
}
