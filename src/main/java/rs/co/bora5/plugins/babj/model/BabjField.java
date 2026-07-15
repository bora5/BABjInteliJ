package rs.co.bora5.plugins.babj.model;

/**
 * A single generatable property extracted from a JPA entity: either a scalar column or a
 * single-valued association ({@code @ManyToOne}/{@code @OneToOne}). Collections are excluded by the
 * parser, since they are not rendered as grid columns or simple edit fields.
 */
public final class BabjField {

    /** How the property is rendered across the generated artifacts. */
    public enum Kind {
        /** Scalar value (String, primitive/boxed, temporal, {@link java.math.BigDecimal}). */
        SIMPLE,
        /** {@code enum} scalar — rendered as a {@code ComboBox} populated from {@code values()}. */
        ENUM,
        /** Single-valued association — projected as {@code alias.display} and edited via a combo box. */
        ASSOCIATION
    }

    private final String name;
    private final Kind kind;
    /** Simple (unqualified) Java type name, e.g. {@code String}, {@code LocalDate}, {@code Firma}. */
    private final String typeSimpleName;
    /** Fully-qualified name to import for {@link #typeSimpleName}, or {@code null} when none is needed. */
    private final String typeFqn;
    /** For {@link Kind#ASSOCIATION}: the display property projected in the grid (default {@code naziv}). */
    private final String displayProperty;

    public BabjField(String name, Kind kind, String typeSimpleName, String typeFqn, String displayProperty) {
        this.name = name;
        this.kind = kind;
        this.typeSimpleName = typeSimpleName;
        this.typeFqn = typeFqn;
        this.displayProperty = displayProperty;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTypeSimpleName() {
        return typeSimpleName;
    }

    public String getTypeFqn() {
        return typeFqn;
    }

    public String getDisplayProperty() {
        return displayProperty;
    }

    public boolean isAssociation() {
        return kind == Kind.ASSOCIATION;
    }

    /** The alias used for this association in the JPQL {@code getJoin()} (its own field name). */
    public String getAlias() {
        return name;
    }

    /** The Java type used for this property in the DTO — associations flatten to {@code String}. */
    public String getDtoType() {
        return kind == Kind.ASSOCIATION ? "String" : typeSimpleName;
    }
}
