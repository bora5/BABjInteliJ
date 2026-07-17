package rs.co.bora5.plugins.babj.model;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.search.GlobalSearchScope;

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
    private static final String ABSTRACT_SETTINGS = "rs.co.bora5.programs.bab.model.AbstractSettings";
    private static final String DATABASE_ATTACHMENTS =
            "rs.co.bora5.programs.bab.model.interfaceCheck.MultiAttachmentEntityInterface";
    private static final String FILE_ATTACHMENTS =
            "rs.co.bora5.programs.bab.model.interfaceCheck.MultiFileSystemAttachmentEntityInterface";

    private final String simpleName;
    private final String packageName;
    private final List<BABjField> fields;
    private final boolean restPublicIdCapable;
    private final boolean settingsCapable;
    private final AttachmentSupport attachmentSupport;

    private EntityModel(String simpleName, String packageName, List<BABjField> fields,
                        boolean restPublicIdCapable, boolean settingsCapable,
                        AttachmentSupport attachmentSupport) {
        this.simpleName = simpleName;
        this.packageName = packageName;
        this.fields = fields;
        this.restPublicIdCapable = restPublicIdCapable;
        this.settingsCapable = settingsCapable;
        this.attachmentSupport = attachmentSupport;
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

    public boolean isSettingsCapable() {
        return settingsCapable;
    }

    public AttachmentSupport getAttachmentSupport() {
        return attachmentSupport;
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

            PsiClass tc = PsiUtil.resolveClassInClassTypeOnly(type);
            if (hasAny(field, MANY_TO_ONE) || hasAny(field, ONE_TO_ONE)
                    || isAbstractEntityType(tc)) {
                String simple = tc != null ? tc.getName() : type.getPresentableText();
                String fqn = tc != null ? tc.getQualifiedName() : null;
                fields.add(new BABjField(name, BABjField.Kind.ASSOCIATION, simple, fqn, pickDisplayProperty(tc)));
                continue;
            }

            if (tc != null && tc.isEnum()) {
                fields.add(new BABjField(name, BABjField.Kind.ENUM, tc.getName(), tc.getQualifiedName(), null));
                continue;
            }

            fields.add(new BABjField(name, BABjField.Kind.SIMPLE, simpleTypeName(type, tc), importFqn(type, tc), null));
        }

        return new EntityModel(psiClass.getName(), pkg, fields,
                InheritanceUtil.isInheritor(psiClass, REST_PUBLIC_ID),
                InheritanceUtil.isInheritor(psiClass, ABSTRACT_SETTINGS),
                resolveAttachmentSupport(psiClass));
    }

    private static AttachmentSupport resolveAttachmentSupport(PsiClass entity) {
        AttachmentSupport fileSystem = resolveAttachmentSupport(
                entity, FILE_ATTACHMENTS, AttachmentKind.FILE_SYSTEM);
        if (fileSystem.isAvailable()) {
            return fileSystem;
        }
        return resolveAttachmentSupport(entity, DATABASE_ATTACHMENTS, AttachmentKind.DATABASE);
    }

    private static AttachmentSupport resolveAttachmentSupport(PsiClass entity, String interfaceFqn,
                                                              AttachmentKind kind) {
        PsiClass contract = JavaPsiFacade.getInstance(entity.getProject())
                .findClass(interfaceFqn, GlobalSearchScope.allScope(entity.getProject()));
        if (contract == null || contract.getTypeParameters().length < 2
                || !InheritanceUtil.isInheritor(entity, interfaceFqn)) {
            return AttachmentSupport.none();
        }
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(
                contract, entity, PsiSubstitutor.EMPTY);
        PsiType attachmentType = substitutor.substitute(contract.getTypeParameters()[1]);
        if (attachmentType == null) {
            return AttachmentSupport.none();
        }
        PsiClass attachmentClass = PsiUtil.resolveClassInClassTypeOnly(attachmentType);
        if (attachmentClass == null || attachmentClass.getName() == null
                || attachmentClass.getQualifiedName() == null
                || !InheritanceUtil.isInheritor(attachmentClass, ABSTRACT_ENTITY)) {
            return AttachmentSupport.none();
        }
        return new AttachmentSupport(kind, attachmentClass.getName(),
                attachmentClass.getQualifiedName());
    }

    public enum AttachmentKind {
        NONE,
        DATABASE,
        FILE_SYSTEM
    }

    public record AttachmentSupport(AttachmentKind kind, String simpleName, String qualifiedName) {
        public static AttachmentSupport none() {
            return new AttachmentSupport(AttachmentKind.NONE, "", "");
        }

        public boolean isAvailable() {
            return kind != AttachmentKind.NONE && !qualifiedName.isBlank();
        }
    }

    /**
     * Chooses the String property to project for an association's DTO/grid column: prefers
     * {@code naziv}, then {@code username}, {@code name}, {@code oznaka}, then the first remaining
     * {@code String} field. Inherited fields are included.
     */
    private static String pickDisplayProperty(PsiClass target) {
        if (target == null) {
            return "naziv";
        }
        for (String preferred : List.of("naziv", "username", "name", "oznaka")) {
            PsiField field = target.findFieldByName(preferred, true);
            if (isStringField(field)) {
                return preferred;
            }
        }
        for (PsiField f : target.getAllFields()) {
            if (f.hasModifierProperty(PsiModifier.STATIC) || "serialVersionUID".equals(f.getName())) {
                continue;
            }
            if (isStringField(f)) {
                return f.getName();
            }
        }
        return "naziv";
    }

    private static boolean isStringField(PsiField field) {
        if (field == null) {
            return false;
        }
        PsiType type = field.getType();
        PsiClass typeClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (typeClass != null) {
            return "java.lang.String".equals(typeClass.getQualifiedName());
        }
        String canonical = type.getCanonicalText();
        return "String".equals(canonical) || "java.lang.String".equals(canonical);
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

    private static boolean isAbstractEntityType(PsiClass typeClass) {
        return typeClass != null && InheritanceUtil.isInheritor(typeClass, ABSTRACT_ENTITY);
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
