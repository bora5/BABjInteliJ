package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class LegacyComboBoxInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addFrameworkStubs();
        myFixture.enableInspections(new LegacyComboBoxInspection());
    }

    public void testReplacesSimpleProviderAndRemovesLegacyMethodAndImport() {
        myFixture.configureByText("EditGradWindow.java", """
                package example;

                import com.vaadin.flow.component.combobox.ComboBox;
                import com.vaadin.flow.data.provider.DataProvider;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;

                class Filijala {}
                class FilijalaHome {
                    java.util.stream.Stream<Filijala> findAllLazy(
                            int offset, int limit, String filter, String... fields) { return null; }
                    int findSizeLazy(String filter, String... fields) { return 0; }
                }

                class EditGradWindow extends GenericWindow {
                    FilijalaHome filijalaEJB;
                    ComboBox<Filijala> cbFilijala;

                    void createContent() {
                        <caret>refreshFilijala();
                        cbFilijala.setLabel("Filijala");
                    }

                    private void refreshFilijala() {
                        DataProvider<Filijala, String> dataProvider =
                                DataProvider.fromFilteringCallbacks(
                                        query -> filijalaEJB.findAllLazy(query.getOffset(),
                                                query.getLimit(),
                                                query.getFilter().orElse(null), "naziv", "oznaka"),
                                        query -> filijalaEJB.findSizeLazy(
                                                query.getFilter().orElse(null), "naziv", "oznaka"));
                        cbFilijala = new ComboBox<>();
                        cbFilijala.setItems(dataProvider);
                    }
                }
                """);

        launch("Replace with createSimpleComboBox()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("cbFilijala = createSimpleComboBox(\"Filijala\", filijalaEJB,"
                + " \"naziv\", \"oznaka\");"));
        assertFalse(result.contains("refreshFilijala"));
        assertFalse(result.contains("data.provider.DataProvider"));
    }

    public void testReplacesDependentProviderAndRemovesRedundantListener() {
        myFixture.configureByText("EditPartWindow.java", """
                package example;

                import com.vaadin.flow.component.combobox.ComboBox;
                import com.vaadin.flow.data.provider.DataProvider;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;

                class Ustanova {}
                class DeoUstanove {}
                class DeoUstanoveHome {
                    java.util.stream.Stream<DeoUstanove> findAllLazyWithOtherEntity(
                            int offset, int limit, String property, Ustanova value,
                            String filter, String... fields) { return null; }
                    int findSizeLazyWithOtherEntity(String property, Ustanova value,
                            String filter, String... fields) { return 0; }
                }

                class EditPartWindow extends GenericWindow {
                    DeoUstanoveHome deoUstanoveEJB;
                    ComboBox<Ustanova> cbUstanova;
                    ComboBox<DeoUstanove> cbDeoUstanove;

                    void createContent() {
                        <caret>refreshDeoUstanove();
                        cbDeoUstanove.setLabel("Deo ustanove:");
                        cbUstanova.addValueChangeListener(event -> {
                            cbDeoUstanove.getLazyDataView().refreshAll();
                            cbDeoUstanove.setValue(null);
                        });
                    }

                    private void refreshDeoUstanove() {
                        DataProvider<DeoUstanove, String> dataProvider =
                                DataProvider.fromFilteringCallbacks(
                                        query -> deoUstanoveEJB.findAllLazyWithOtherEntity(
                                                query.getOffset(), query.getLimit(), "ustanova",
                                                cbUstanova.getValue(),
                                                query.getFilter().orElse(null), "naziv"),
                                        query -> deoUstanoveEJB.findSizeLazyWithOtherEntity(
                                                "ustanova", cbUstanova.getValue(),
                                                query.getFilter().orElse(null), "naziv"));
                        cbDeoUstanove = new ComboBox<>();
                        cbDeoUstanove.setItems(dataProvider);
                    }
                }
                """);

        launch("Replace with createDependentComboBox()");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("cbDeoUstanove = createDependentComboBox("
                + "\"Deo ustanove:\", deoUstanoveEJB, \"ustanova\", cbUstanova, \"naziv\");"));
        assertFalse(result.contains("refreshDeoUstanove"));
        assertFalse(result.contains("addValueChangeListener"));
        assertFalse(result.contains("data.provider.DataProvider"));
    }

    public void testDoesNotReplaceDependentProviderWhenListenerHasCustomBehavior() {
        myFixture.configureByText("EditPartWindow.java", """
                package example;

                import com.vaadin.flow.component.combobox.ComboBox;
                import com.vaadin.flow.data.provider.DataProvider;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;

                class Ustanova {}
                class DeoUstanove {}
                class DeoUstanoveHome {
                    java.util.stream.Stream<DeoUstanove> findAllLazyWithOtherEntity(
                            int offset, int limit, String property, Ustanova value,
                            String filter, String... fields) { return null; }
                    int findSizeLazyWithOtherEntity(String property, Ustanova value,
                            String filter, String... fields) { return 0; }
                }

                class EditPartWindow extends GenericWindow {
                    DeoUstanoveHome deoUstanoveEJB;
                    ComboBox<Ustanova> cbUstanova;
                    ComboBox<DeoUstanove> cbDeoUstanove;

                    void createContent() {
                        <caret>refreshDeoUstanove();
                        cbDeoUstanove.setLabel("Deo ustanove:");
                        cbUstanova.addValueChangeListener(event -> {
                            cbDeoUstanove.getLazyDataView().refreshAll();
                            cbDeoUstanove.setValue(null);
                            updateOtherFields();
                        });
                    }

                    void updateOtherFields() {}

                    private void refreshDeoUstanove() {
                        DataProvider<DeoUstanove, String> dataProvider =
                                DataProvider.fromFilteringCallbacks(
                                        query -> deoUstanoveEJB.findAllLazyWithOtherEntity(
                                                query.getOffset(), query.getLimit(), "ustanova",
                                                cbUstanova.getValue(),
                                                query.getFilter().orElse(null), "naziv"),
                                        query -> deoUstanoveEJB.findSizeLazyWithOtherEntity(
                                                "ustanova", cbUstanova.getValue(),
                                                query.getFilter().orElse(null), "naziv"));
                        cbDeoUstanove = new ComboBox<>();
                        cbDeoUstanove.setItems(dataProvider);
                    }
                }
                """);

        assertNoQuickFix("Replace with createDependentComboBox()");
    }

    public void testDoesNotReplaceUnsupportedFlagProvider() {
        myFixture.configureByText("EditCompanyWindow.java", """
                package example;

                import com.vaadin.flow.component.combobox.ComboBox;
                import com.vaadin.flow.data.provider.DataProvider;
                import rs.co.bora5.programs.bab.front.windowses.GenericWindow;

                class Company {}
                class CompanyHome {
                    java.util.stream.Stream<Company> findAllLazyWithFlag(
                            int offset, int limit, String flag, String filter,
                            String... fields) { return null; }
                    int findSizeLazyWithFlag(String flag, String filter,
                            String... fields) { return 0; }
                }

                class EditCompanyWindow extends GenericWindow {
                    CompanyHome companyEJB;
                    ComboBox<Company> cbCompany;

                    void createContent() {
                        <caret>refreshCompany();
                        cbCompany.setLabel("Company");
                    }

                    private void refreshCompany() {
                        DataProvider<Company, String> dataProvider =
                                DataProvider.fromFilteringCallbacks(
                                        query -> companyEJB.findAllLazyWithFlag(query.getOffset(),
                                                query.getLimit(), "active",
                                                query.getFilter().orElse(null), "name"),
                                        query -> companyEJB.findSizeLazyWithFlag("active",
                                                query.getFilter().orElse(null), "name"));
                        cbCompany = new ComboBox<>();
                        cbCompany.setItems(dataProvider);
                    }
                }
                """);

        assertNoQuickFix("Replace with createSimpleComboBox()");
    }

    private void launch(String name) {
        IntentionAction action = myFixture.findSingleIntention(name);
        myFixture.launchAction(action);
    }

    private void assertNoQuickFix(String name) {
        myFixture.doHighlighting();
        assertFalse(myFixture.getAvailableIntentions().stream()
                .anyMatch(action -> name.equals(action.getText())));
    }

    private void addFrameworkStubs() {
        myFixture.addClass("""
                package com.vaadin.flow.data.provider;

                public class DataProvider<T, F> {
                    @FunctionalInterface
                    public interface FetchCallback<T, F> {
                        java.util.stream.Stream<T> fetch(Query<T, F> query);
                    }
                    @FunctionalInterface
                    public interface CountCallback<T, F> {
                        int count(Query<T, F> query);
                    }
                    public static class Query<T, F> {
                        public int getOffset() { return 0; }
                        public int getLimit() { return 0; }
                        public java.util.Optional<F> getFilter() { return java.util.Optional.empty(); }
                    }
                    public static <T, F> DataProvider<T, F> fromFilteringCallbacks(
                            FetchCallback<T, F> fetch, CountCallback<T, F> count) {
                        return null;
                    }
                }
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component.combobox;

                import com.vaadin.flow.data.provider.DataProvider;

                public class ComboBox<T> {
                    public void setItems(DataProvider<T, String> provider) {}
                    public void setLabel(String label) {}
                    public T getValue() { return null; }
                    public void setValue(T value) {}
                    public LazyDataView getLazyDataView() { return null; }
                    public void addValueChangeListener(
                            java.util.function.Consumer<Object> listener) {}
                    public static class LazyDataView {
                        public void refreshAll() {}
                    }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.windowses;

                import com.vaadin.flow.component.combobox.ComboBox;

                public class GenericWindow {
                    protected <A, H> ComboBox<A> createSimpleComboBox(
                            String caption, H service, String... searchFields) { return null; }
                    protected <P, A, H> ComboBox<A> createDependentComboBox(
                            String caption, H service, String dependsOn,
                            ComboBox<P> parent, String... searchFields) { return null; }
                }
                """);
    }
}
