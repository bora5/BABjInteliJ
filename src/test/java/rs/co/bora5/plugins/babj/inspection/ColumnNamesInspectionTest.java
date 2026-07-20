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

    public void testAcceptsJoinAliasesDeclaredInView() {
        addBabjStubs();
        myFixture.enableInspections(new ColumnNamesInspection());
        myFixture.configureByText("CompanyPartView.java", """
                package example;

                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames;

                class Company { String naziv; }
                class User { String username; }
                class City { String naziv; }
                class CompanyPart {
                    String naziv;
                    Company ustanova;
                    User korisnik;
                    City grad;
                }
                class CompanyPartDTO {
                    String naziv;
                    String ustanova;
                    String zaduzen;
                    String grad;
                }

                @ColumnNames("naziv~~Naziv,*u.naziv~ustanova~Firma,"
                        + "*k.username~zaduzen~Zadužen,*grad.naziv~grad~Grad")
                class CompanyPartView extends GenericView<CompanyPart, Object,
                        CompanyPartDTO, User> {
                    @Override
                    public String getJoin() {
                        return " LEFT JOIN x.korisnik k LEFT JOIN x.ustanova u"
                                + " LEFT JOIN x.grad grad";
                    }
                }
                """);

        assertEmpty(warnings());
    }

    public void testAcceptsJoinAliasesDeclaredInHome() {
        addBabjStubs();
        myFixture.enableInspections(new ColumnNamesInspection());
        myFixture.configureByText("CompanyPartView.java", """
                package example;

                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames;

                class Company { String naziv; }
                class CompanyPart { Company ustanova; }
                class CompanyPartDTO { String ustanova; }
                class CompanyPartHome {
                    public String getJoin() { return " LEFT JOIN x.ustanova u"; }
                }

                @ColumnNames("*u.naziv~ustanova~Firma")
                class CompanyPartView extends GenericView<CompanyPart, CompanyPartHome,
                        CompanyPartDTO, Object> {}
                """);

        assertEmpty(warnings());
    }

    public void testReportsInvalidPropertyOnResolvedJoinAlias() {
        addBabjStubs();
        myFixture.enableInspections(new ColumnNamesInspection());
        myFixture.configureByText("CompanyPartView.java", """
                package example;

                import rs.co.bora5.programs.bab.front.views.GenericView;
                import rs.co.bora5.programs.bab.front.views.interfaceCheck.ColumnNames;

                class User { String username; }
                class CompanyPart { User korisnik; }
                class CompanyPartDTO { String zaduzen; }

                @ColumnNames("*k.displayName~zaduzen~Zadužen")
                class CompanyPartView extends GenericView<CompanyPart, Object,
                        CompanyPartDTO, User> {
                    @Override
                    public String getJoin() { return " LEFT JOIN x.korisnik AS k"; }
                }
                """);

        assertTrue(warnings().stream().anyMatch(description -> description != null
                && description.contains("'displayName' is not a property of entity User")));
    }

    private java.util.List<String> warnings() {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity() == HighlightSeverity.WARNING)
                .map(HighlightInfo::getDescription)
                .toList();
    }

    private void addBabjStubs() {
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views;
                public class GenericView<E, H, D, K> {
                    public String getJoin() { return ""; }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.views.interfaceCheck;
                public @interface ColumnNames { String value(); }
                """);
    }
}
