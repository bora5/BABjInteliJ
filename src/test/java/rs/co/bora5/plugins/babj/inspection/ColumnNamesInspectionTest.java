package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ColumnNamesInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    public void testAcceptsOptionalFilterAndSortingFlags() {
        addBabjStubs();
        myFixture.enableInspections(new ColumnNamesInspection());
        myFixture.configureByText("OrderView.java", """
                package example;

                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames;

                class Order { String name; }
                class OrderDTO { String name; }

                @ColumnNames("name~name~Name~true~false")
                class OrderView extends GenericView<Order, Object, OrderDTO, Object> {}
                """);

        assertEmpty(myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity() == HighlightSeverity.WARNING)
                .map(HighlightInfo::getDescription)
                .toList());
    }

    public void testReportsInvalidOptionalBooleanFlag() {
        addBabjStubs();
        myFixture.enableInspections(new ColumnNamesInspection());
        myFixture.configureByText("OrderView.java", """
                package example;

                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames;

                class Order { String name; }
                class OrderDTO { String name; }

                @ColumnNames("name~name~Name~sometimes")
                class OrderView extends GenericView<Order, Object, OrderDTO, Object> {}
                """);

        assertTrue(myFixture.doHighlighting().stream()
                .map(HighlightInfo::getDescription)
                .anyMatch(description -> description != null
                        && description.contains("filter enabled must be true or false")));
    }

    private void addBabjStubs() {
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views;
                public class GenericView<E, H, D, K> {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views.interfaceCheck;
                public @interface ColumnNames { String value(); }
                """);
    }
}
