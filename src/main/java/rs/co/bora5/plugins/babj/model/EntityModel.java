package rs.co.bora5.plugins.babj.model;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.InheritanceUtil;

/**
 * Parses a JPA entity {@link PsiClass} into the metadata the generator needs: its name, package and
 * the list of generatable {@link BABjField properties} (scalars, enums and single-valued
 * associations; collections are skipped).
 */
public final class EntityModel {

    private static final String[] TRANSIENT = {"jakarta.persistence.Transient", "javax.persistence.Transient"};
    private static final String[] MANY_TO_ONE = {"jakarta.persistence.ManyToOne", "javax.persistence.ManyToOne"};
    private static final String[] ONE_TO_ONE = {"jakarta.persistence.OneToOne", "javax.persistence.OneToOne"};
    private static final String[] TO_MANY = {
            "jakarta.persistence.OneToMany", "javax.persistence.OneToMany",
            "jakarta.persistence.ManyToMany", "javax.persistence.ManyToMany"
    };
    private static final String[] ENTITY = {"jakarta.persistence.Entity", "javax.persistence.Entity"};
    private static final String ABSTRACT_ENTITY = "rs.co.bora5.programs.bab.model.AbstractEntity";
    private static final String REST_PUBLIC_ID =
            "rs.co.bora5.programs.bab.model.interfaceCheck.RestPublicIdEntityInterface";

    private final String simpleName;
    private final String packageName;
    private final List<BABjField> fields;
    private final boolean restPublicIdCapable;

    private EntityModel(String simpleName, String packageName, List<BABjField> fields,
                        boolean restPublicIdCapable) {
        this.simpleName = simpleName;
        this.packageName = packageName;
        this.fields = fields;
        this.restPublicIdCapable = restPublicIdCapable;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getPackageName() {
        return packageName;
    }

    public List<BABjField> getFields() {
        return fields;
    }

    public boolean isRestPublicIdCapable() {
        return restPublicIdCapable;
    }

    /** Whether the class looks like a BABj entity: a JPA {@code @Entity} or an {@code AbstractEntity}. */
    public static boolean isEntity(PsiClass psiClass) {
        if (psiClass == null || psiClass.getName() == null) {
            return false;
        }
        if (hasAny(psiClass, ENTITY)) {
            return true;
        }
        for (PsiClass c = psiClass.getSuperClass(); c != null; c = c.getSuperClass()) {
            if (ABSTRACT_ENTITY.equals(c.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    public static EntityModel from(PsiClass psiClass) {
        String pkg = psiClass.getContainingFile() instanceof PsiJavaFile jf ? jf.getPackageName() : "";
        List<BABjField> fields = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            String name = field.getName();
            if ("serialVersionUID".equals(name) || hasAny(field, TRANSIENT)) {
                continue;
            }
            PsiType type = field.getType();
            if (isCollectionOrMap(type) || hasAny(field, TO_MANY)) {
                continue;
            }

            if (hasAny(field, MANY_TO_ONE) || hasAny(field, ONE_TO_ONE)) {
                PsiClass tc = PsiUtil.resolveClassInClassTypeOnly(type);
                String simple = tc != null ? tc.getName() : type.getPresentableText();
                String fqn = tc != null ? tc.getQualifiedName() : null;
                fields.add(new BABjField(name, BABjField.Kind.ASSOCIATION, simple, fqn, pickDisplayProperty(tc)));
                continue;
            }

            PsiClass tc = PsiUtil.resolveClassInClassTypeOnly(type);
            if (tc != null && tc.isEnum()) {
                fields.add(new BABjField(name, BABjField.Kind.ENUM, tc.getName(), tc.getQualifiedName(), null));
                continue;
            }

            fields.add(new BABjField(name, BABjField.Kind.SIMPLE, simpleTypeName(type, tc), importFqn(type, tc), null));
        }

        return new EntityModel(psiClass.getName(), pkg, fields,
                InheritanceUtil.isInheritor(psiClass, REST_PUBLIC_ID));
    }

    /**
     * Chooses the property to project for an association's grid column: prefers {@code naziv}, then
     * {@code username} (operator-like entities), then the first {@code String} field, falling back
     * to {@code naziv} when the target cannot be inspected.
     */
    private static String pickDisplayProperty(PsiClass target) {
        if (target == null) {
            return "naziv";
        }
        if (target.findFieldByName("naziv", true) != null) {
            return "naziv";
        }
        if (target.findFieldByName("username", true) != null) {
            return "username";
        }
        for (PsiField f : target.getAllFields()) {
            if (f.hasModifierProperty(PsiModifier.STATIC) || "serialVersionUID".equals(f.getName())) {
                continue;
            }
            if (f.getType().equalsToText("java.lang.String")) {
                return f.getName();
            }
        }
        return "naziv";
    }

    private static String simpleTypeName(PsiType type, PsiClass tc) {
        if (type instanceof PsiPrimitiveType) {
            return type.getPresentableText();
        }
        return tc != null ? tc.getName() : type.getPresentableText();
    }

    private static String importFqn(PsiType type, PsiClass tc) {
        if (type instanceof PsiPrimitiveType || tc == null) {
            return null;
        }
        String qn = tc.getQualifiedName();
        if (qn == null || qn.startsWith("java.lang.")) {
            return null;
        }
        return qn;
    }

    private static boolean isCollectionOrMap(PsiType type) {
        String c = type.getCanonicalText();
        return c.startsWith("java.util.Set") || c.startsWith("java.util.List")
                || c.startsWith("java.util.Collection") || c.startsWith("java.util.Map")
                || c.startsWith("java.util.SortedSet") || c.startsWith("java.util.SortedMap");
    }

    private static boolean hasAny(PsiModifierListOwner owner, String[] fqns) {
        for (String fqn : fqns) {
            if (owner.hasAnnotation(fqn)) {
                return true;
            }
        }
        return false;
    }
}
