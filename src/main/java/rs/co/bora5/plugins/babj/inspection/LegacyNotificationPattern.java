package rs.co.bora5.plugins.babj.inspection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Recognizes the notification implementation that predates BAB's generic notification centre. */
final class LegacyNotificationPattern {

    enum Kind {
        ENTITY,
        HOME,
        MAIN_VIEW
    }

    record Match(Kind kind, String operatorType, @Nullable String providerField,
                 boolean automaticMigration) {
    }

    private static final Set<String> BASE_FIELDS = Set.of(
            "naziv", "vreme", "opis", "procitano", "objavio", "korisnik");

    private LegacyNotificationPattern() {
    }

    static @Nullable Match detect(PsiClass psiClass) {
        Match entity = detectEntity(psiClass);
        if (entity != null) {
            return entity;
        }
        Match home = detectHome(psiClass);
        if (home != null) {
            return home;
        }
        return detectMainView(psiClass);
    }

    private static @Nullable Match detectEntity(PsiClass psiClass) {
        PsiClassType superType = directSuperType(psiClass, "AbstractEntity");
        if (superType == null) {
            return null;
        }

        PsiField naziv = psiClass.findFieldByName("naziv", false);
        PsiField vreme = psiClass.findFieldByName("vreme", false);
        PsiField opis = psiClass.findFieldByName("opis", false);
        PsiField procitano = psiClass.findFieldByName("procitano", false);
        PsiField objavio = psiClass.findFieldByName("objavio", false);
        PsiField korisnik = psiClass.findFieldByName("korisnik", false);
        if (!isType(naziv, "java.lang.String") || !isType(opis, "java.lang.String")
                || !isType(vreme, "java.time.LocalDateTime") || !isType(procitano, "boolean")
                || objavio == null || korisnik == null
                || !objavio.getType().getCanonicalText().equals(korisnik.getType().getCanonicalText())) {
            return null;
        }

        boolean standard = BASE_FIELDS.stream().allMatch(field -> hasTrivialAccessors(psiClass, field))
                && constructorsAreStandard(psiClass);
        return new Match(Kind.ENTITY, korisnik.getType().getCanonicalText(), null, standard);
    }

    private static @Nullable Match detectHome(PsiClass psiClass) {
        PsiClassType superType = directSuperType(psiClass, "AbstractHome");
        if (superType == null || superType.getParameters().length != 2) {
            return null;
        }
        if (!hasMethod(psiClass, "countNew", 1) || !hasMethod(psiClass, "getNew", 1)
                || !hasMethod(psiClass, "setRead", 1) || !hasMethod(psiClass, "setAllRead", 1)) {
            return null;
        }
        PsiMethod setAllRead = findMethod(psiClass, "setAllRead", 1);
        if (setAllRead == null) {
            return null;
        }
        boolean standard = true;
        for (String methodName : Set.of("countNew", "getNew", "setRead", "setAllRead")) {
            PsiMethod method = findMethod(psiClass, methodName, 1);
            String body = method == null || method.getBody() == null ? "" : method.getBody().getText();
            if (body.contains(".persist(") || body.contains(".save(") || body.contains(".remove(")
                    || body.contains(".broadcast(") || body.contains(".send(")) {
                standard = false;
            }
        }
        PsiType operatorType = setAllRead.getParameterList().getParameters()[0].getType();
        return new Match(Kind.HOME, operatorType.getCanonicalText(), null, standard);
    }

    private static @Nullable Match detectMainView(PsiClass psiClass) {
        if (directSuperType(psiClass, "GenericMainView") == null) {
            return null;
        }
        PsiMethod additional = findMethod(psiClass, "getAdditionalButtons", 0);
        PsiMethod builder = findMethod(psiClass, "buildNotificationsButton", 0);
        PsiMethod updater = findMethod(psiClass, "updateNotificationsCount", 0);
        if (additional == null || builder == null || updater == null
                || additional.getBody() == null || builder.getBody() == null) {
            return null;
        }
        String additionalText = additional.getBody().getText();
        String builderText = builder.getBody().getText();
        if (!additionalText.contains("NotificationsButton") && !builderText.contains("NotificationsButton")) {
            return null;
        }
        boolean standard = isStandardAdditionalButtons(additional)
                && !builderText.contains("getDugme(") && !builderText.contains(".navigate(");

        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(psiClass, PsiMethodCallExpression.class)) {
            if (!"countNew".equals(call.getMethodExpression().getReferenceName())) {
                continue;
            }
            if (call.getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression qualifier
                    && qualifier.resolve() instanceof PsiField field
                    && field.getContainingClass() == psiClass) {
                return new Match(Kind.MAIN_VIEW, "", field.getName(), standard);
            }
        }
        return null;
    }

    private static boolean isStandardAdditionalButtons(PsiMethod method) {
        boolean returnsButton = false;
        for (PsiStatement statement : method.getBody().getStatements()) {
            String text = compact(statement.getText());
            boolean standard = text.contains("=buildNotificationsButton();")
                    || text.contains(".addClassName(")
                    || text.contains(".setUnreadCount(")
                    || text.startsWith("return");
            if (!standard) {
                return false;
            }
            returnsButton |= text.startsWith("return");
        }
        return returnsButton;
    }

    private static boolean constructorsAreStandard(PsiClass psiClass) {
        for (PsiMethod constructor : psiClass.getConstructors()) {
            PsiCodeBlock body = constructor.getBody();
            if (body == null) {
                return false;
            }
            int parameterCount = constructor.getParameterList().getParametersCount();
            if (parameterCount != 0 && parameterCount != 4) {
                return false;
            }
            if (parameterCount == 4) {
                PsiParameter[] parameters = constructor.getParameterList().getParameters();
                if (!"naziv".equals(parameters[0].getName()) || !"opis".equals(parameters[1].getName())
                        || !"objavio".equals(parameters[2].getName())
                        || !"korisnik".equals(parameters[3].getName())) {
                    return false;
                }
            }
            for (PsiStatement statement : body.getStatements()) {
                String compact = compact(statement.getText());
                boolean baseAssignment = BASE_FIELDS.stream()
                        .anyMatch(field -> compact.startsWith("this." + field + "=")
                                || compact.startsWith(field + "="));
                boolean standardDefault = compact.equals("vreme=LocalDateTime.now();")
                        || compact.equals("this.vreme=LocalDateTime.now();")
                        || compact.equals("procitano=false;")
                        || compact.equals("this.procitano=false;");
                if (!baseAssignment && !standardDefault) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasTrivialAccessors(PsiClass psiClass, String fieldName) {
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        PsiMethod getter = findMethod(psiClass, "get" + suffix, 0);
        PsiMethod setter = findMethod(psiClass, "set" + suffix, 1);
        if (getter == null && "procitano".equals(fieldName)) {
            getter = findMethod(psiClass, "is" + suffix, 0);
        }
        if (getter == null || setter == null || getter.getBody() == null || setter.getBody() == null) {
            return false;
        }
        String getterBody = compact(getter.getBody().getText());
        String setterBody = compact(setter.getBody().getText());
        PsiParameter parameter = setter.getParameterList().getParameters()[0];
        return getterBody.equals("{return" + fieldName + ";}")
                && (setterBody.equals("{this." + fieldName + "=" + parameter.getName() + ";}")
                || setterBody.equals("{" + fieldName + "=" + parameter.getName() + ";}"));
    }

    private static boolean isType(@Nullable PsiField field, String canonicalType) {
        if (field == null) {
            return false;
        }
        String simpleType = canonicalType.substring(canonicalType.lastIndexOf('.') + 1);
        return canonicalType.equals(field.getType().getCanonicalText())
                || simpleType.equals(field.getType().getPresentableText());
    }

    private static boolean hasMethod(PsiClass psiClass, String name, int parameters) {
        return findMethod(psiClass, name, parameters) != null;
    }

    static @Nullable PsiMethod findMethod(PsiClass psiClass, String name, int parameters) {
        for (PsiMethod method : psiClass.findMethodsByName(name, false)) {
            if (method.getParameterList().getParametersCount() == parameters) {
                return method;
            }
        }
        return null;
    }

    static @Nullable PsiClassType directSuperType(PsiClass psiClass, String simpleName) {
        for (PsiClassType type : psiClass.getExtendsListTypes()) {
            String text = type.getClassName();
            if (simpleName.equals(text)) {
                return type;
            }
        }
        return null;
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }
}
