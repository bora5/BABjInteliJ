package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class HomeSelectInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session;
                public class AbstractHome<E, D> {}
                """);
        myFixture.enableInspections(new HomeSelectInspection());
    }

    public void testJoinAliasUsesSortedSetElementType() {
        myFixture.configureByText("DokumentHome.java", """
                package example;

                import java.util.SortedSet;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                class ValidateCondition {
                    boolean validated;
                    boolean denied;
                }
                class Dokument {
                    Long id;
                    SortedSet<ValidateCondition> validateConditions;
                }
                class DokumentDTO {}

                class DokumentHome extends AbstractHome<Dokument, DokumentDTO> {
                    public String getSelect() {
                        return super.toString() + "(x.id, kdvc.validated, kdvc.denied)";
                    }
                    public String getJoin() {
                        return " LEFT JOIN x.validateConditions kdvc";
                    }
                }
                """);

        assertEmpty(warnings());
    }

    public void testCollectionAliasStillReportsInvalidElementProperty() {
        myFixture.configureByText("DokumentHome.java", """
                package example;

                import java.util.SortedSet;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                class ValidateCondition { boolean validated; }
                class Dokument { Long id; SortedSet<ValidateCondition> validateConditions; }
                class DokumentDTO {}

                class DokumentHome extends AbstractHome<Dokument, DokumentDTO> {
                    public String getSelect() {
                        return "(x.id, kdvc.missing)";
                    }
                    public String getJoin() {
                        return " LEFT JOIN x.validateConditions kdvc";
                    }
                }
                """);

        assertTrue(warnings().stream().anyMatch(description -> description != null
                && description.contains("'missing' is not a property of entity ValidateCondition")));
    }

    private java.util.List<String> warnings() {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity() == HighlightSeverity.WARNING)
                .map(HighlightInfo::getDescription)
                .toList();
    }
}
