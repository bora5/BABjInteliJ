package rs.co.bora5.plugins.babj.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Applies one complete, conservatively recognized notification migration. */
public class MigrateLegacyNotificationQuickFix implements LocalQuickFix {

    private static final Set<String> BASE_FIELDS = Set.of(
            "naziv", "vreme", "opis", "procitano", "objavio", "korisnik");

    private final LegacyNotificationPattern.Kind expectedKind;

    MigrateLegacyNotificationQuickFix(LegacyNotificationPattern.Kind expectedKind) {
        this.expectedKind = expectedKind;
    }

    @Override
    public @NotNull String getName() {
        return switch (expectedKind) {
            case ENTITY -> "Inherit AbstractNotification";
            case HOME -> "Inherit AbstractNotificationHome";
            case MAIN_VIEW -> "Use BAB notification centre";
        };
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Migrate BAB notifications";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class, false);
        LegacyNotificationPattern.Match match = psiClass == null ? null : LegacyNotificationPattern.detect(psiClass);
        if (match == null || match.kind() != expectedKind || !match.automaticMigration()) {
            return;
        }
        switch (match.kind()) {
            case ENTITY -> migrateEntity(project, psiClass, match.operatorType());
            case HOME -> migrateHome(project, psiClass, match.operatorType());
            case MAIN_VIEW -> migrateMainView(project, psiClass, match.providerField());
        }
        PsiReplacement.finish(project, psiClass);
    }

    private static void migrateEntity(Project project, PsiClass psiClass, String operatorType) {
        replaceSuperclass(project, psiClass,
                "rs.co.bora5.programs.bab.model.AbstractNotification<" + operatorType + ">");

        for (String fieldName : BASE_FIELDS) {
            PsiField field = psiClass.findFieldByName(fieldName, false);
            if (field != null) {
                field.delete();
            }
            deleteMethod(psiClass, "get" + capitalize(fieldName), 0);
            deleteMethod(psiClass, "set" + capitalize(fieldName), 1);
            if ("procitano".equals(fieldName)) {
                deleteMethod(psiClass, "is" + capitalize(fieldName), 0);
            }
        }

        PsiMethod toString = LegacyNotificationPattern.findMethod(psiClass, "toString", 0);
        if (toString != null && toString.getBody() != null
                && toString.getBody().getText().replaceAll("\\s+", "")
                .equals("{returnnaziv;}")) {
            toString.delete();
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (PsiMethod constructor : psiClass.getConstructors()) {
            PsiCodeBlock body = constructor.getBody();
            if (body == null) {
                continue;
            }
            String replacement = constructor.getParameterList().getParametersCount() == 4
                    ? "{ super(naziv, opis, objavio, korisnik); }"
                    : "{}";
            body.replace(factory.createCodeBlockFromText(replacement, constructor));
        }
    }

    private static void migrateHome(Project project, PsiClass psiClass, String operatorType) {
        PsiClassType superType = LegacyNotificationPattern.directSuperType(psiClass, "AbstractHome");
        if (superType == null || superType.getParameters().length != 2) {
            return;
        }
        String entityType = superType.getParameters()[0].getCanonicalText();
        String dtoType = superType.getParameters()[1].getCanonicalText();
        replaceSuperclass(project, psiClass,
                "rs.co.bora5.programs.bab.session.AbstractNotificationHome<"
                        + entityType + ", " + dtoType + ", " + operatorType + ">");
        deleteMethod(psiClass, "countNew", 1);
        deleteMethod(psiClass, "getNew", 1);
        deleteMethod(psiClass, "setRead", 1);
        deleteMethod(psiClass, "setAllRead", 1);
    }

    private static void migrateMainView(Project project, PsiClass psiClass, String providerField) {
        deleteMethod(psiClass, "getAdditionalButtons", 0);
        deleteMethod(psiClass, "buildNotificationsButton", 0);
        deleteMethod(psiClass, "updateNotificationsCount", 0);

        for (PsiField field : psiClass.getFields()) {
            if (field.getType().getPresentableText().endsWith("NotificationsButton")) {
                field.delete();
            }
        }

        if (LegacyNotificationPattern.findMethod(psiClass, "getNotificationProvider", 0) == null
                && providerField != null) {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiMethod hook = factory.createMethodFromText("""
                    @Override
                    protected rs.co.bora5.programs.bab.front.notifications.NotificationProvider
                            getNotificationProvider() {
                        return %s;
                    }
                    """.formatted(providerField), psiClass);
            psiClass.add(hook);
        }
    }

    private static void replaceSuperclass(Project project, PsiClass psiClass, String replacementText) {
        PsiReferenceList extendsList = psiClass.getExtendsList();
        if (extendsList == null || extendsList.getReferenceElements().length != 1) {
            return;
        }
        PsiElement replacement = JavaPsiFacade.getElementFactory(project)
                .createReferenceFromText(replacementText, psiClass);
        extendsList.getReferenceElements()[0].replace(replacement);
    }

    private static void deleteMethod(PsiClass psiClass, String name, int parameters) {
        PsiMethod method = LegacyNotificationPattern.findMethod(psiClass, name, parameters);
        if (method != null) {
            method.delete();
        }
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
