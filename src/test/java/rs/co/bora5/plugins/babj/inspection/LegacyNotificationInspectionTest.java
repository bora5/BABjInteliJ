package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class LegacyNotificationInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addFrameworkStubs();
        myFixture.enableInspections(new LegacyNotificationInspection());
    }

    public void testMigratesNotificationEntityAndPreservesExtraField() {
        myFixture.configureByText("Obavestenje.java", """
                package example;

                import java.time.LocalDateTime;
                import rs.co.bora5.programs.bab.model.AbstractEntity;

                class Korisnik extends AbstractEntity {}

                class <caret>Obavestenje extends AbstractEntity {
                    private String naziv;
                    private LocalDateTime vreme;
                    private String opis;
                    private String dugme;
                    private boolean procitano;
                    private Korisnik objavio;
                    private Korisnik korisnik;

                    public Obavestenje() {
                        vreme = LocalDateTime.now();
                        procitano = false;
                    }
                    public Obavestenje(String naziv, String opis,
                            Korisnik objavio, Korisnik korisnik) {
                        this.naziv = naziv;
                        this.opis = opis;
                        this.objavio = objavio;
                        this.korisnik = korisnik;
                        vreme = LocalDateTime.now();
                        procitano = false;
                    }
                    public String getNaziv() { return naziv; }
                    public void setNaziv(String naziv) { this.naziv = naziv; }
                    public LocalDateTime getVreme() { return vreme; }
                    public void setVreme(LocalDateTime vreme) { this.vreme = vreme; }
                    public String getOpis() { return opis; }
                    public void setOpis(String opis) { this.opis = opis; }
                    public boolean getProcitano() { return procitano; }
                    public void setProcitano(boolean procitano) { this.procitano = procitano; }
                    public Korisnik getObjavio() { return objavio; }
                    public void setObjavio(Korisnik objavio) { this.objavio = objavio; }
                    public Korisnik getKorisnik() { return korisnik; }
                    public void setKorisnik(Korisnik korisnik) { this.korisnik = korisnik; }
                    public String getDugme() { return dugme; }
                    public void setDugme(String dugme) { this.dugme = dugme; }
                    @Override public String toString() { return naziv; }
                }
                """);

        launch("Inherit AbstractNotification");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("extends AbstractNotification<Korisnik>"));
        assertTrue(result.contains("super(naziv, opis, objavio, korisnik);"));
        assertTrue(result.contains("private String dugme;"));
        assertFalse(result.contains("private String naziv;"));
        assertFalse(result.contains("getProcitano"));
        assertFalse(result.contains("String toString"));
    }

    public void testMigratesNotificationHome() {
        myFixture.configureByText("ObavestenjeHome.java", """
                package example;

                import java.util.List;
                import rs.co.bora5.programs.bab.session.AbstractHome;

                class Obavestenje {}
                class ObavestenjaDTO {}
                class Korisnik {}

                class <caret>ObavestenjeHome extends AbstractHome<Obavestenje, ObavestenjaDTO> {
                    public ObavestenjeHome() { super(Obavestenje.class, ObavestenjaDTO.class); }
                    public long countNew(Long id) { return 0; }
                    public List<Obavestenje> getNew(Korisnik k) { return null; }
                    public void setRead(Long id) {}
                    public void setAllRead(Korisnik k) {}
                }
                """);

        launch("Inherit AbstractNotificationHome");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains(
                "extends AbstractNotificationHome<Obavestenje, ObavestenjaDTO, Korisnik>"));
        assertFalse(result.contains("countNew"));
        assertFalse(result.contains("getNew"));
        assertFalse(result.contains("setAllRead"));
    }

    public void testMigratesCustomMainViewBell() {
        myFixture.configureByText("MainView.java", """
                package example;

                import com.vaadin.flow.component.Component;
                import rs.co.bora5.programs.bab.front.GenericMainView;
                import rs.co.bora5.programs.bab.utils.NotificationsButton;

                class ObavestenjeHome { long countNew(Long id) { return 0; } }

                class <caret>MainView extends GenericMainView {
                    private ObavestenjeHome obavestenjeEJB;
                    private NotificationsButton notificationsButton;

                    public Component getAdditionalButtons() {
                        notificationsButton = buildNotificationsButton();
                        notificationsButton.setUnreadCount(obavestenjeEJB.countNew(getOperater()));
                        return notificationsButton;
                    }
                    private NotificationsButton buildNotificationsButton() {
                        return new NotificationsButton();
                    }
                    public void updateNotificationsCount() {
                        notificationsButton.setUnreadCount(obavestenjeEJB.countNew(getOperater()));
                    }
                }
                """);

        launch("Use BAB notification centre");

        String result = myFixture.getFile().getText();
        assertTrue(result.contains("NotificationProvider"));
        assertTrue(result.contains("getNotificationProvider()"));
        assertTrue(result.contains("return obavestenjeEJB;"));
        assertFalse(result.contains("NotificationsButton"));
        assertFalse(result.contains("obavestenjeEJB.countNew"));
    }

    public void testDoesNotRewriteCustomizedEntityConstructor() {
        myFixture.configureByText("Obavestenje.java", """
                package example;
                import java.time.LocalDateTime;
                import rs.co.bora5.programs.bab.model.AbstractEntity;
                class Korisnik extends AbstractEntity {}
                class <caret>Obavestenje extends AbstractEntity {
                    private String naziv; private LocalDateTime vreme; private String opis;
                    private boolean procitano; private Korisnik objavio; private Korisnik korisnik;
                    public Obavestenje() { vreme = LocalDateTime.now(); audit(); }
                    void audit() {}
                    public String getNaziv(){return naziv;} public void setNaziv(String v){naziv=v;}
                    public LocalDateTime getVreme(){return vreme;} public void setVreme(LocalDateTime v){vreme=v;}
                    public String getOpis(){return opis;} public void setOpis(String v){opis=v;}
                    public boolean getProcitano(){return procitano;} public void setProcitano(boolean v){procitano=v;}
                    public Korisnik getObjavio(){return objavio;} public void setObjavio(Korisnik v){objavio=v;}
                    public Korisnik getKorisnik(){return korisnik;} public void setKorisnik(Korisnik v){korisnik=v;}
                }
                """);

        myFixture.doHighlighting();
        assertTrue(myFixture.doHighlighting().stream().anyMatch(info ->
                info.getDescription() != null
                        && info.getDescription().contains("requires manual migration")));
        assertFalse(myFixture.getAvailableIntentions().stream()
                .anyMatch(action -> "Inherit AbstractNotification".equals(action.getText())));
    }

    private void launch(String name) {
        IntentionAction action = myFixture.findSingleIntention(name);
        myFixture.launchAction(action);
    }

    private void addFrameworkStubs() {
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.model;
                public class AbstractEntity {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.model;
                public class AbstractNotification<K> extends AbstractEntity {
                    protected AbstractNotification() {}
                    protected AbstractNotification(String naziv, String opis, K objavio, K korisnik) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session;
                public class AbstractHome<E, D> {
                    protected AbstractHome(Class<E> entity, Class<D> dto) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.session;
                public class AbstractNotificationHome<E, D, K> extends AbstractHome<E, D> {
                    protected AbstractNotificationHome(Class<E> entity, Class<D> dto) { super(entity, dto); }
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front.notifications;
                public interface NotificationProvider {}
                """);
        myFixture.addClass("""
                package com.vaadin.flow.component;
                public class Component {}
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.utils;
                public class NotificationsButton extends com.vaadin.flow.component.Component {
                    public void setUnreadCount(long count) {}
                }
                """);
        myFixture.addClass("""
                package rs.co.bora5.programs.bab.front;
                public class GenericMainView {
                    protected Long getOperater() { return 0L; }
                    protected rs.co.bora5.programs.bab.front.notifications.NotificationProvider
                            getNotificationProvider() { return null; }
                }
                """);
    }
}
