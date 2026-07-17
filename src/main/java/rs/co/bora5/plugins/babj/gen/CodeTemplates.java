package rs.co.bora5.plugins.babj.gen;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import rs.co.bora5.plugins.babj.model.BABjField;
import rs.co.bora5.plugins.babj.model.BABjNaming;
import rs.co.bora5.plugins.babj.model.GenerationContext;

/**
 * Renders the four BABj artifacts (DTO, Home, View, EditWindow) as Java source text, following the
 * conventions observed in the wastex codebase: {@code AbstractHome} projection via
 * {@code getSelect()}/{@code getJoin()}, declarative {@code GenericView} annotations, and a
 * {@code GenericWindow} with {@code @PropertyId} fields and combo-box factory calls.
 *
 * <p>Output is tab-indented to match the target project's code style, with fully-written import
 * lists so it compiles without an extra "optimize imports" pass.
 */
public final class CodeTemplates {

    private static final Random RANDOM = new Random();

    private CodeTemplates() {
    }

    // -------------------------------------------------------------------- DTO

    public static String dto(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("java.io.Serial");
        imports.add("rs.co.bora5.programs.bab.front.views.projections.AbstractDTO");
        imports.add(ctx.modelPackage() + "." + entity);
        for (BABjField f : ctx.getFields()) {
            if (!f.isAssociation() && f.typeFqn() != null) {
                imports.add(f.typeFqn());
            }
        }

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.dtoPackage(), imports);
        sb.append("public class ").append(entity).append("DTO extends AbstractDTO<").append(entity).append("> {\n\n");
        serial(sb);

        for (BABjField f : ctx.getFields()) {
            sb.append("\tprivate ").append(f.getDtoType()).append(' ').append(f.name()).append(";\n");
        }
        sb.append('\n');

        // constructor
        sb.append("\tpublic ").append(entity).append("DTO(Long id");
        for (BABjField f : ctx.getFields()) {
            sb.append(", ").append(f.getDtoType()).append(' ').append(f.name());
        }
        sb.append(") {\n");
        sb.append("\t\tthis.setId(id);\n");
        for (BABjField f : ctx.getFields()) {
            sb.append("\t\tthis.").append(f.name()).append(" = ").append(f.name()).append(";\n");
        }
        sb.append("\t}\n\n");

        // accessors
        for (BABjField f : ctx.getFields()) {
            String cap = BABjNaming.capitalize(f.name());
            sb.append("\tpublic ").append(f.getDtoType()).append(" get").append(cap).append("() {\n");
            sb.append("\t\treturn ").append(f.name()).append(";\n");
            sb.append("\t}\n\n");
            sb.append("\tpublic void set").append(cap).append('(').append(f.getDtoType()).append(' ')
                    .append(f.name()).append(") {\n");
            sb.append("\t\tthis.").append(f.name()).append(" = ").append(f.name()).append(";\n");
            sb.append("\t}\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------- Home

    public static String home(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        boolean hasJoins = ctx.getFields().stream().anyMatch(BABjField::isAssociation);

        Set<String> imports = new TreeSet<>();
        imports.add("jakarta.ejb.LocalBean");
        imports.add("jakarta.ejb.Stateless");
        imports.add("rs.co.bora5.programs.bab.session.AbstractHome");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add("java.io.Serial");

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.homePackage(), imports);
        sb.append("@Stateless\n@LocalBean\n@Primary\n");
        sb.append("public class ").append(entity).append("Home extends AbstractHome<")
                .append(entity).append(", ").append(entity).append("DTO> {\n\n");
        serial(sb);

        sb.append("\tpublic ").append(entity).append("Home() {\n");
        sb.append("\t\tsuper(").append(entity).append(".class, ").append(entity).append("DTO.class);\n");
        sb.append("\t}\n\n");

        // getSelect
        StringBuilder select = new StringBuilder("x.id");
        for (BABjField f : ctx.getFields()) {
            select.append(", ");
            if (f.isAssociation()) {
                select.append(f.getAlias()).append('.').append(f.displayProperty());
            } else {
                select.append("x.").append(f.name());
            }
        }
        sb.append("\t@Override\n");
        sb.append("\tpublic String getSelect() {\n");
        sb.append("\t\treturn super.getSelect() + \"(").append(select).append(")\";\n");
        sb.append("\t}\n");

        // getJoin
        if (hasJoins) {
            StringBuilder join = new StringBuilder();
            for (BABjField f : ctx.getFields()) {
                if (f.isAssociation()) {
                    join.append(" LEFT JOIN x.").append(f.name()).append(' ').append(f.getAlias());
                }
            }
            sb.append('\n');
            sb.append("\t@Override\n");
            sb.append("\tpublic String getJoin() {\n");
            sb.append("\t\treturn \"").append(join).append("\";\n");
            sb.append("\t}\n");
        }

        sb.append("\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------- View

    public static String view(GenerationContext ctx) {
        String entity = ctx.getEntityName();

        Set<String> imports = new TreeSet<>();
        imports.add("com.vaadin.flow.router.PageTitle");
        imports.add("com.vaadin.flow.router.Route");
        imports.add("com.vaadin.cdi.annotation.RouteScoped");
        imports.add("rs.co.bora5.programs.bab.front.views.GenericView");
        imports.add("rs.co.bora5.programs.bab.front.views.interfaceCheck.*");
        imports.add(ctx.getBasePackage() + ".front.MainView");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());
        imports.add(ctx.homePackage() + "." + entity + "Home");
        imports.add(ctx.getBasePackage() + ".utils.Roles");
        imports.add("java.io.Serial");

        StringBuilder cols = new StringBuilder();
        for (BABjField f : ctx.getFields()) {
            if (!cols.isEmpty()) {
                cols.append(',');
            }
            String label = BABjNaming.label(f.name());
            if (f.isAssociation()) {
                cols.append('*').append(f.name()).append('.').append(f.displayProperty())
                        .append('~').append(f.name()).append('~').append(label);
            } else {
                cols.append(f.name()).append('~').append(f.name()).append('~').append(label);
            }
        }

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.viewPackage(), imports);
        sb.append("@EnableNew\n@EnableEdit\n@EnableDelete\n");
        sb.append("@AdminTypes(roles = Roles.").append(ctx.getRole()).append(")\n");
        sb.append("@ColumnNames(\"").append(cols).append("\")\n");
        sb.append("@PageTitle(\"").append(ctx.getPageTitle()).append("\")\n");
        sb.append("@Route(value = \"").append(ctx.getRoute()).append("\", layout = MainView.class)\n");
        sb.append("@RouteScoped\n");
        sb.append("public class ").append(ctx.getViewName()).append(" extends GenericView<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n\n");
        serial(sb);
        sb.append("}\n");
        return sb.toString();
    }

    // ----------------------------------------------------------------- Window

    public static String window(GenerationContext ctx) {
        String entity = ctx.getEntityName();

        Set<String> imports = new TreeSet<>();
        imports.add("java.io.Serial");
        imports.add("com.vaadin.flow.component.orderedlayout.VerticalLayout");
        imports.add("com.vaadin.flow.data.binder.PropertyId");
        imports.add("jakarta.enterprise.context.Dependent");
        imports.add("rs.co.bora5.programs.bab.front.windowses.GenericWindow");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());
        imports.add(ctx.homePackage() + "." + entity + "Home");

        Set<String> injectedTypes = new LinkedHashSet<>();
        StringBuilder injects = new StringBuilder();
        StringBuilder decls = new StringBuilder();
        StringBuilder body = new StringBuilder();
        StringBuilder adds = new StringBuilder();

        for (BABjField f : ctx.getFields()) {
            Component c = componentFor(f);
            imports.addAll(c.imports());

            if (f.isAssociation()) {
                String assoc = f.typeSimpleName();
                imports.add(ctx.modelPackage() + "." + assoc);
                imports.add(ctx.homePackage() + "." + assoc + "Home");
                // One injected EJB per associated type, even if several fields reference it.
                if (injectedTypes.add(assoc)) {
                    injects.append("\t@Inject\n");
                    injects.append("\tprivate @Primary ").append(assoc).append("Home ")
                            .append(BABjNaming.decapitalize(assoc)).append("EJB;\n\n");
                }
            }
            if (f.kind() == BABjField.Kind.ENUM && f.typeFqn() != null) {
                imports.add(f.typeFqn());
            }

            decls.append("\t@PropertyId(value = \"").append(f.name()).append("\")\n");
            decls.append("\tprivate ").append(c.declaredType()).append(' ').append(c.var()).append(";\n\n");

            body.append("\t\t").append(c.init()).append('\n');
            adds.append("\t\tvl.add(").append(c.var()).append(");\n");
        }

        boolean hasInjects = !injectedTypes.isEmpty();
        if (hasInjects) {
            imports.add("jakarta.inject.Inject");
        }

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.windowPackage(), imports);
        sb.append("@Dependent\n@Primary\n");
        sb.append("public class Edit").append(entity).append("Window extends GenericWindow<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n\n");
        serial(sb);
        if (hasInjects) {
            sb.append(injects);
        }
        sb.append(decls);
        sb.append("\t@Override\n");
        sb.append("\tpublic void createContent(VerticalLayout vl) {\n");
        sb.append(body);
        sb.append('\n');
        sb.append(adds);
        sb.append("\t}\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    // -------------------------------------------------------------- internals

    /** A resolved Vaadin input component for one field. */
    private record Component(String declaredType, String var, String init, Set<String> imports) {
    }

    private static Component componentFor(BABjField f) {
        String cap = BABjNaming.capitalize(f.name());
        String label = BABjNaming.label(f.name());
        Set<String> imp = new LinkedHashSet<>();

        if (f.isAssociation()) {
            imp.add("com.vaadin.flow.component.combobox.ComboBox");
            String assocDecap = BABjNaming.decapitalize(f.typeSimpleName());
            String var = "cb" + cap;
            String init = var + " = createSimpleComboBox(\"" + label + ":\", " + assocDecap
                          + "EJB, \"" + f.displayProperty() + "\");";
            return new Component("ComboBox<" + f.typeSimpleName() + ">", var, init, imp);
        }
        if (f.kind() == BABjField.Kind.ENUM) {
            imp.add("com.vaadin.flow.component.combobox.ComboBox");
            String var = "cb" + cap;
            String init = var + " = new ComboBox<>(\"" + label + ":\");\n\t\t"
                          + var + ".setItems(" + f.typeSimpleName() + ".values());";
            return new Component("ComboBox<" + f.typeSimpleName() + ">", var, init, imp);
        }

        return switch (f.typeSimpleName()) {
            case "boolean", "Boolean" -> {
                imp.add("com.vaadin.flow.component.checkbox.Checkbox");
                String var = "cb" + cap;
                yield new Component("Checkbox", var, var + " = new Checkbox(\"" + label + "\");", imp);
            }
            case "int", "Integer", "long", "Long", "short", "Short" -> {
                imp.add("com.vaadin.flow.component.textfield.IntegerField");
                String var = "if" + cap;
                yield new Component("IntegerField", var, var + " = new IntegerField(\"" + label + ":\");", imp);
            }
            case "double", "Double", "float", "Float", "BigDecimal" -> {
                imp.add("com.vaadin.flow.component.textfield.NumberField");
                String var = "nf" + cap;
                yield new Component("NumberField", var, var + " = new NumberField(\"" + label + ":\");", imp);
            }
            case "LocalDate" -> {
                imp.add("com.vaadin.flow.component.datepicker.DatePicker");
                String var = "dp" + cap;
                yield new Component("DatePicker", var, var + " = new DatePicker(\"" + label + ":\");", imp);
            }
            case "LocalDateTime" -> {
                imp.add("com.vaadin.flow.component.datetimepicker.DateTimePicker");
                String var = "dtp" + cap;
                yield new Component("DateTimePicker", var, var + " = new DateTimePicker(\"" + label + ":\");", imp);
            }
            case "LocalTime" -> {
                imp.add("com.vaadin.flow.component.timepicker.TimePicker");
                String var = "tp" + cap;
                yield new Component("TimePicker", var, var + " = new TimePicker(\"" + label + ":\");", imp);
            }
            default -> {
                imp.add("com.vaadin.flow.component.textfield.TextField");
                String var = "tf" + cap;
                yield new Component("TextField", var, var + " = new TextField(\"" + label + ":\");", imp);
            }
        };
    }

    private static void header(StringBuilder sb, String pkg, Set<String> imports) {
        sb.append("package ").append(pkg).append(";\n\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append('\n');
    }

    private static void serial(StringBuilder sb) {
        sb.append("\t@Serial\n");
        sb.append("\tprivate static final long serialVersionUID = ").append(RANDOM.nextLong()).append("L;\n\n");
    }
}
