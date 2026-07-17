package rs.co.bora5.plugins.babj.model;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class EntityModelTest extends LightJavaCodeInsightFixtureTestCase {

    public void testDetectsAbstractEntityAssociationWhenJpaAnnotationIsOnGetter() {
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.model;
                public abstract class AbstractEntity {}
                """);
        myFixture.addClass("""
                package jakarta.persistence;
                public @interface ManyToOne {}
                """);
        PsiClass entity = myFixture.addClass("""
                package example.model;

                import jakarta.persistence.ManyToOne;
                import rs.co.bora5.programs.bab.model.AbstractEntity;

                public class TestPlugin extends AbstractEntity {
                    private Korisnik korisnik;
                    private NazivEntity poNazivu;
                    private NameEntity poImenu;
                    private CodeEntity poOznaci;
                    private DescriptionEntity poOpisu;
                    private TestStatus status;

                    @ManyToOne
                    public Korisnik getKorisnik() {
                        return korisnik;
                    }
                }

                class Korisnik extends AbstractEntity {
                    private Long naziv;
                    private String username;
                    private String name;
                    private String description;
                }

                class NazivEntity extends AbstractEntity {
                    private String naziv;
                    private String username;
                }

                class NameEntity extends AbstractEntity {
                    private String name;
                    private String description;
                }

                class CodeEntity extends AbstractEntity {
                    private String oznaka;
                    private String description;
                }

                class DescriptionEntity extends AbstractEntity {
                    private String description;
                }

                enum TestStatus {
                    ACTIVE, INACTIVE
                }
                """);

        EntityModel model = EntityModel.from(entity);
        BABjField korisnik = field(model, "korisnik");
        BABjField status = field(model, "status");

        assertEquals(BABjField.Kind.ASSOCIATION, korisnik.kind());
        assertEquals("Korisnik", korisnik.typeSimpleName());
        assertEquals("username", korisnik.displayProperty());
        assertEquals("String", korisnik.getDtoType());
        assertEquals("naziv", field(model, "poNazivu").displayProperty());
        assertEquals("name", field(model, "poImenu").displayProperty());
        assertEquals("oznaka", field(model, "poOznaci").displayProperty());
        assertEquals("description", field(model, "poOpisu").displayProperty());

        assertEquals(BABjField.Kind.ENUM, status.kind());
        assertEquals("TestStatus", status.getDtoType());
    }

    private static BABjField field(EntityModel model, String name) {
        return model.getFields().stream()
                .filter(field -> name.equals(field.name()))
                .findFirst()
                .orElseThrow();
    }
}
