package rs.co.bora5.plugins.babj.model;

import java.math.BigDecimal;

/**
 * A single generatable property extracted from a JPA entity: either a scalar column or a
 * single-valued association ({@code @ManyToOne}/{@code @OneToOne}). Collections are excluded by the
 * parser, since they are not rendered as grid columns or simple edit fields.
 * @param typeSimpleName  Simple (unqualified) Java type name, e.g. {@code String}, {@code LocalDate}, {@code Firma}.
 * @param typeFqn  Fully-qualified name to import for {@link #typeSimpleName}, or {@code null} when none is needed.
 * @param displayProperty  For {@link Kind#ASSOCIATION}: the display property projected in the grid (default {@code naziv}).
 */
public record BABjField(String name, Kind kind, String typeSimpleName, String typeFqn,
                        String displayProperty, EditorKind editorKind) {

    public BABjField(String name, Kind kind, String typeSimpleName, String typeFqn,
                     String displayProperty) {
        this(name, kind, typeSimpleName, typeFqn, displayProperty, EditorKind.AUTO);
    }

    /** How the property is rendered across the generated artifacts. */
    public enum Kind {
        /** Scalar value (String, primitive/boxed, temporal, {@link BigDecimal}). */
        SIMPLE,
        /** {@code enum} scalar — rendered as a {@code ComboBox} populated from {@code values()}. */
        ENUM,
        /** Single-valued association — projected as {@code alias.display} and edited via a combo box. */
        ASSOCIATION
    }

    /** Optional edit-window control override selected in CRUD Designer. */
    public enum EditorKind {
        AUTO("Automatic"),
        TEXT("Text field"),
        INTEGER("Integer field"),
        NUMBER("Number field"),
        BIG_DECIMAL("BigDecimal field"),
        CHECKBOX("Checkbox"),
        DATE("Date picker"),
        DATE_TIME("Date/time picker"),
        TIME("Time picker"),
        COMBO("Combo box");

        private final String displayName;

        EditorKind(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public BABjField withEditor(EditorKind editor) {
        return new BABjField(name, kind, typeSimpleName, typeFqn, displayProperty, editor);
    }

    public boolean isAssociation() {
        return kind == Kind.ASSOCIATION;
    }

    /** The alias used for this association in the JPQL {@code getJoin()} (its own field name). */
    public String getAlias() {
        return name;
    }

    /**
     * The Java type used for this property in the DTO. Entity associations flatten to
     * {@code String}; enums retain their concrete type and are treated like scalar values.
     */
    public String getDtoType() {
        return kind == Kind.ASSOCIATION ? "String" : typeSimpleName;
    }
}
