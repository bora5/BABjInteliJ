package rs.co.bora5.plugins.babj.gen;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import rs.co.bora5.plugins.babj.model.BABjField;
import rs.co.bora5.plugins.babj.model.BABjNaming;
import rs.co.bora5.plugins.babj.model.EntityModel;
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
        if (ctx.isSettingsAdministration()) {
            return settingsDto(ctx);
        }
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
        if (ctx.isSettingsAdministration()) {
            return settingsHome(ctx);
        }
        String entity = ctx.getEntityName();
        boolean hasJoins = ctx.getFields().stream().anyMatch(BABjField::isAssociation);

        Set<String> imports = new TreeSet<>();
        imports.add("jakarta.ejb.LocalBean");
        imports.add("jakarta.ejb.Stateless");
        imports.add("rs.co.bora5.programs.bab.session.AbstractHome");
        if (ctx.isGenerateRest()) {
            imports.add("rs.co.bora5.programs.bab.session.interfaceCheck.RestPublicIdHomeInterface");
        }
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add("java.io.Serial");

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.homePackage(), imports);
        sb.append("@Stateless\n@LocalBean\n@Primary\n");
        sb.append("public class ").append(entity).append("Home extends AbstractHome<")
                .append(entity).append(", ").append(entity).append("DTO>");
        if (ctx.isGenerateRest()) {
            sb.append(" implements RestPublicIdHomeInterface<").append(entity).append('>');
        }
        sb.append(" {\n\n");
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
        imports.add(ctx.isSettingsAdministration()
                ? "rs.co.bora5.programs.bab.front.views.GenericSettingsView"
                : "rs.co.bora5.programs.bab.front.views.GenericView");
        imports.add("rs.co.bora5.programs.bab.front.views.interfaceCheck.*");
        imports.add(ctx.getBasePackage() + ".front.MainView");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());
        imports.add(ctx.homePackage() + "." + entity + "Home");
        if (!ctx.getRolesTypeFqn().isBlank()) {
            imports.add(ctx.getRolesTypeFqn());
        }
        if (ctx.isGenerateCsvImport() || ctx.isGenerateXlsImport()) {
            imports.add("jakarta.inject.Inject");
            imports.add(ctx.windowPackage() + ".Import" + entity
                    + (ctx.isGenerateCsvImport() ? "CsvWindow" : "XlsWindow"));
        }
        if (ctx.isEnableAttachments()) {
            addAttachmentImports(ctx, imports);
        }
        imports.add("java.io.Serial");

        StringBuilder cols = new StringBuilder();
        for (BABjField f : ctx.isSettingsAdministration() ? settingsFields() : ctx.getFields()) {
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
        if (ctx.isEnableExport()) {
            sb.append("@EnableExport\n");
        }
        if (ctx.isGenerateCsvImport() || ctx.isGenerateXlsImport()) {
            sb.append("@EnableImport\n");
        }
        if (ctx.isSettingsAdministration()) {
            sb.append("@EnableCSSButton1(adminsOnly = true, alvaysOn = true)\n");
        }
        if (!ctx.getRoles().isEmpty()) {
            sb.append("@AdminTypes(roles = ");
            if (ctx.getRoles().size() > 1) {
                sb.append('{');
            }
            for (int i = 0; i < ctx.getRoles().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(ctx.getRolesType()).append('.').append(ctx.getRoles().get(i));
            }
            if (ctx.getRoles().size() > 1) {
                sb.append('}');
            }
            sb.append(")\n");
        }
        sb.append("@ColumnNames(\"").append(cols).append("\")\n");
        sb.append("@PageTitle(\"").append(ctx.getPageTitle()).append("\")\n");
        sb.append("@Route(value = \"").append(ctx.getRoute()).append("\", layout = MainView.class)\n");
        sb.append("@RouteScoped\n");
        sb.append("public class ").append(ctx.getViewName()).append(" extends ")
                .append(ctx.isSettingsAdministration() ? "GenericSettingsView<" : "GenericView<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append('>');
        appendAttachmentInterface(ctx, sb);
        sb.append(" {\n\n");
        serial(sb);
        appendImportField(ctx, sb);
        appendAttachmentMethod(ctx, sb);
        sb.append("}\n");
        return sb.toString();
    }

    // --------------------------------------------------------------- REST API

    public static String restEndpoint(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("jakarta.ws.rs.Path");
        imports.add("rs.co.bora5.programs.bab.session.rest.AbstractEndpoint");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.homePackage() + "." + entity + "Home");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.restPackage(), imports);
        sb.append("@Path(\"").append(ctx.getRestPath()).append("\")\n");
        sb.append("public class ").append(entity).append("Endpoint extends AbstractEndpoint<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n")
                .append("}\n");
        return sb.toString();
    }

    // ---------------------------------------------------------- Import/report

    public static String csvImport(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = moduleImports(ctx);
        imports.add("java.util.List");
        imports.add("jakarta.enterprise.context.Dependent");
        imports.add("rs.co.bora5.programs.bab.front.windowses.GenericCSVUploadFileWindow");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.windowPackage(), imports);
        sb.append("@Dependent\n@Primary\n")
                .append("public class Import").append(entity)
                .append("CsvWindow extends GenericCSVUploadFileWindow<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n\n")
                .append("\t@Override\n\tpublic void doWork(List<List<String>> rows) {\n")
                .append("\t\t// TODO Map each input row and persist it through getServiceEJB().\n")
                .append("\t}\n}\n");
        return sb.toString();
    }

    public static String xlsImport(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = moduleImports(ctx);
        imports.add("jakarta.enterprise.context.Dependent");
        imports.add("org.apache.poi.hssf.usermodel.HSSFWorkbook");
        imports.add("rs.co.bora5.programs.bab.front.windowses.GenericXLSUploadFileWindow");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.windowPackage(), imports);
        sb.append("@Dependent\n@Primary\n")
                .append("public class Import").append(entity)
                .append("XlsWindow extends GenericXLSUploadFileWindow<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n\n")
                .append("\t@Override\n\tpublic void doWork(HSSFWorkbook workbook) {\n")
                .append("\t\t// TODO Read workbook rows and persist them through getServiceEJB().\n")
                .append("\t}\n}\n");
        return sb.toString();
    }

    public static String report(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("com.vaadin.flow.component.orderedlayout.VerticalLayout");
        imports.add("jakarta.enterprise.context.Dependent");
        imports.add("rs.co.bora5.programs.bab.front.windowses.GenericReportWindow");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.windowPackage(), imports);
        sb.append("@Dependent\n@Primary\n")
                .append("public class ").append(entity)
                .append("ReportWindow extends GenericReportWindow<")
                .append(entity).append(", ").append(ctx.getKType()).append("> {\n\n")
                .append("\tpublic ").append(entity).append("ReportWindow() {\n")
                .append("\t\tsuper(").append(entity).append(".class);\n\t}\n\n")
                .append("\t@Override\n\tpublic void createContent(VerticalLayout layout) {\n")
                .append("\t\t// TODO Add report parameters and bind them to selekcija.\n")
                .append("\t}\n\n")
                .append("\t@Override\n\tprotected void doMain() {\n")
                .append("\t\t// TODO Generate the report using printUtils, emailUtils, or excelUtils.\n")
                .append("\t}\n}\n");
        return sb.toString();
    }

    public static String messagingAgent(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("jakarta.enterprise.context.ApplicationScoped");
        imports.add("rs.co.bora5.programs.bab.agent.AbstractAgent");
        imports.add("rs.co.bora5.programs.bab.agent.events.AgentEvent");
        imports.add("rs.co.bora5.programs.bab.agent.events.EntityEvent");
        imports.add(ctx.modelPackage() + "." + entity);

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.agentPackage(), imports);
        sb.append("@ApplicationScoped\n")
                .append("public class ").append(entity).append("MessagingAgent extends AbstractAgent {\n\n")
                .append("\tpublic ").append(entity).append("MessagingAgent() {\n")
                .append("\t\tsuper(\"").append(entity).append(" Messaging Agent\");\n\t}\n\n")
                .append("\t@Override\n\tpublic boolean supports(AgentEvent event) {\n")
                .append("\t\treturn event instanceof EntityEvent entityEvent\n")
                .append("\t\t\t\t&& entityEvent.getEntity() instanceof ").append(entity).append(";\n")
                .append("\t}\n\n")
                .append("\t@Override\n\tpublic void handle(AgentEvent event) {\n")
                .append("\t\tif (event instanceof EntityEvent entityEvent\n")
                .append("\t\t\t\t&& entityEvent.getEntity() instanceof ").append(entity).append(" entity) {\n")
                .append("\t\t\tgetContext().broadcast(getName(),\n")
                .append("\t\t\t\t\tnew EntityLifecycleMessage(entityEvent.getType(), entity.getId()));\n")
                .append("\t\t}\n\t}\n\n")
                .append("\tpublic record EntityLifecycleMessage(String eventType, Long entityId) {\n\t}\n")
                .append("}\n");
        return sb.toString();
    }

    private static String settingsDto(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("java.io.Serial");
        imports.add("rs.co.bora5.programs.bab.front.views.projections.AbstractSettingsDTO");
        imports.add(ctx.modelPackage() + "." + entity);

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.dtoPackage(), imports);
        sb.append("public class ").append(entity).append("DTO extends AbstractSettingsDTO<")
                .append(entity).append("> {\n\n");
        serial(sb);
        sb.append("\tpublic ").append(entity)
                .append("DTO(Long id, String backupLocation, String filesAttachmentLocation, ")
                .append("String pluginsLocation, boolean aktivan) {\n")
                .append("\t\tsuper(id, backupLocation, filesAttachmentLocation, pluginsLocation, aktivan);\n")
                .append("\t}\n}\n");
        return sb.toString();
    }

    private static String settingsHome(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("java.io.Serial");
        imports.add("jakarta.ejb.LocalBean");
        imports.add("jakarta.ejb.Stateless");
        imports.add("rs.co.bora5.programs.bab.session.AbstractSettingsHome");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.modelPackage() + "." + entity);

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.homePackage(), imports);
        sb.append("@Stateless\n@LocalBean\n@Primary\n")
                .append("public class ").append(entity).append("Home extends AbstractSettingsHome<")
                .append(entity).append(", ").append(entity).append("DTO> {\n\n");
        serial(sb);
        sb.append("\tpublic ").append(entity).append("Home() {\n")
                .append("\t\tsuper(").append(entity).append(".class, ").append(entity)
                .append("DTO.class);\n\t}\n\n")
                .append("\t@Override\n\tpublic String getSelect() {\n")
                .append("\t\treturn super.getSelect() + \"(x.id, x.backupLocation, ")
                .append("x.filesAttachmentLocation, x.pluginsLocation, x.aktivan)\";\n")
                .append("\t}\n}\n");
        return sb.toString();
    }

    private static String settingsWindow(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add("java.io.Serial");
        imports.add("jakarta.enterprise.context.Dependent");
        imports.add("rs.co.bora5.programs.bab.front.windowses.GenericSettingsWindow");
        imports.add("rs.co.bora5.programs.bab.utils.Primary");
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.homePackage() + "." + entity + "Home");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());

        StringBuilder sb = new StringBuilder();
        header(sb, ctx.windowPackage(), imports);
        sb.append("@Dependent\n@Primary\n")
                .append("public class Edit").append(entity)
                .append("Window extends GenericSettingsWindow<")
                .append(entity).append(", ").append(entity).append("Home, ")
                .append(entity).append("DTO, ").append(ctx.getKType()).append("> {\n\n");
        serial(sb);
        sb.append("}\n");
        return sb.toString();
    }

    private static java.util.List<BABjField> settingsFields() {
        return java.util.List.of(
                new BABjField("backupLocation", BABjField.Kind.SIMPLE, "String", null, null),
                new BABjField("filesAttachmentLocation", BABjField.Kind.SIMPLE, "String", null, null),
                new BABjField("pluginsLocation", BABjField.Kind.SIMPLE, "String", null, null),
                new BABjField("aktivan", BABjField.Kind.SIMPLE, "boolean", null, null));
    }

    private static void appendImportField(GenerationContext ctx, StringBuilder sb) {
        if (!ctx.isGenerateCsvImport() && !ctx.isGenerateXlsImport()) {
            return;
        }
        String entity = ctx.getEntityName();
        sb.append("\t@Inject\n\t@ImportWindow\n\tprivate Import").append(entity)
                .append(ctx.isGenerateCsvImport() ? "CsvWindow" : "XlsWindow")
                .append(" importWindow;\n\n");
    }

    private static void addAttachmentImports(GenerationContext ctx, Set<String> imports) {
        EntityModel.AttachmentSupport support = ctx.getAttachmentSupport();
        imports.add("java.util.function.Consumer");
        imports.add("com.vaadin.flow.component.orderedlayout.VerticalLayout");
        imports.add(support.qualifiedName());
        if (support.kind() == EntityModel.AttachmentKind.FILE_SYSTEM) {
            imports.add("rs.co.bora5.programs.bab.front.views.interfaceCheck.MultiFileSystemAttachmentViewInterface");
            imports.add("rs.co.bora5.programs.bab.utils.CustomMultiFileSystemInputField");
        } else {
            imports.add("rs.co.bora5.programs.bab.front.views.interfaceCheck.MultiAttachmentViewInterface");
            imports.add("rs.co.bora5.programs.bab.utils.CustomMultiByteArrayInputField");
        }
    }

    private static void appendAttachmentInterface(GenerationContext ctx, StringBuilder sb) {
        if (!ctx.isEnableAttachments()) {
            return;
        }
        EntityModel.AttachmentSupport support = ctx.getAttachmentSupport();
        if (support.kind() == EntityModel.AttachmentKind.FILE_SYSTEM) {
            sb.append(" implements MultiFileSystemAttachmentViewInterface<")
                    .append(ctx.getEntityName()).append(", ").append(support.simpleName())
                    .append(", ").append(ctx.getKType()).append('>');
        } else {
            sb.append(" implements MultiAttachmentViewInterface<")
                    .append(ctx.getEntityName()).append(", ").append(support.simpleName()).append('>');
        }
    }

    private static void appendAttachmentMethod(GenerationContext ctx, StringBuilder sb) {
        if (!ctx.isEnableAttachments()) {
            return;
        }
        String entity = ctx.getEntityName();
        EntityModel.AttachmentSupport support = ctx.getAttachmentSupport();
        sb.append("\t@Override\n\tpublic Consumer<VerticalLayout> generateComponents(")
                .append(entity).append(" entity) {\n")
                .append("\t\treturn layout -> {\n");
        if (support.kind() == EntityModel.AttachmentKind.FILE_SYSTEM) {
            sb.append("\t\t\tCustomMultiFileSystemInputField<").append(entity).append(", ")
                    .append(support.simpleName()).append(", ").append(ctx.getKType())
                    .append("> attachments = new CustomMultiFileSystemInputField<>(")
                    .append(support.simpleName()).append(".class, entity);\n");
        } else {
            sb.append("\t\t\tCustomMultiByteArrayInputField<").append(entity).append(", ")
                    .append(support.simpleName())
                    .append("> attachments = new CustomMultiByteArrayInputField<>(")
                    .append(support.simpleName()).append(".class, entity);\n");
        }
        sb.append("\t\t\tattachments.setValue(entity.getAttachments());\n")
                .append("\t\t\tlayout.add(attachments);\n")
                .append("\t\t};\n\t}\n\n");
    }

    private static Set<String> moduleImports(GenerationContext ctx) {
        String entity = ctx.getEntityName();
        Set<String> imports = new TreeSet<>();
        imports.add(ctx.dtoPackage() + "." + entity + "DTO");
        imports.add(ctx.homePackage() + "." + entity + "Home");
        imports.add(ctx.modelPackage() + "." + entity);
        imports.add(ctx.modelPackage() + "." + ctx.getKType());
        return imports;
    }

    // ----------------------------------------------------------------- Window

    public static String window(GenerationContext ctx) {
        if (ctx.isSettingsAdministration()) {
            return settingsWindow(ctx);
        }
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
            if (f.typeFqn() != null) {
                imports.add(f.typeFqn());
            }

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

        BABjField.EditorKind editor = resolvedEditorKind(f);
        return switch (editor) {
            case CHECKBOX -> {
                imp.add("com.vaadin.flow.component.checkbox.Checkbox");
                String var = "cb" + cap;
                yield new Component("Checkbox", var, var + " = new Checkbox(\"" + label + "\");", imp);
            }
            case INTEGER -> {
                imp.add("com.vaadin.flow.component.textfield.IntegerField");
                String var = "if" + cap;
                yield new Component("IntegerField", var, var + " = new IntegerField(\"" + label + ":\");", imp);
            }
            case NUMBER -> {
                imp.add("com.vaadin.flow.component.textfield.NumberField");
                String var = "nf" + cap;
                yield new Component("NumberField", var, var + " = new NumberField(\"" + label + ":\");", imp);
            }
            case DATE -> {
                imp.add("com.vaadin.flow.component.datepicker.DatePicker");
                String var = "dp" + cap;
                yield new Component("DatePicker", var, var + " = new DatePicker(\"" + label + ":\");", imp);
            }
            case DATE_TIME -> {
                imp.add("com.vaadin.flow.component.datetimepicker.DateTimePicker");
                String var = "dtp" + cap;
                yield new Component("DateTimePicker", var, var + " = new DateTimePicker(\"" + label + ":\");", imp);
            }
            case TIME -> {
                imp.add("com.vaadin.flow.component.timepicker.TimePicker");
                String var = "tp" + cap;
                yield new Component("TimePicker", var, var + " = new TimePicker(\"" + label + ":\");", imp);
            }
            case COMBO -> {
                imp.add("com.vaadin.flow.component.combobox.ComboBox");
                String var = "cb" + cap;
                yield new Component("ComboBox<" + boxedType(f.typeSimpleName()) + ">", var,
                        var + " = new ComboBox<>(\"" + label + ":\");", imp);
            }
            case AUTO, TEXT -> {
                imp.add("com.vaadin.flow.component.textfield.TextField");
                String var = "tf" + cap;
                yield new Component("TextField", var, var + " = new TextField(\"" + label + ":\");", imp);
            }
        };
    }

    public static String editorName(BABjField field) {
        return resolvedEditorKind(field).toString();
    }

    private static BABjField.EditorKind resolvedEditorKind(BABjField field) {
        if (field.isAssociation() || field.kind() == BABjField.Kind.ENUM) {
            return BABjField.EditorKind.COMBO;
        }
        if (field.editorKind() != BABjField.EditorKind.AUTO) {
            return field.editorKind();
        }
        return switch (field.typeSimpleName()) {
            case "boolean", "Boolean" -> BABjField.EditorKind.CHECKBOX;
            case "int", "Integer", "long", "Long", "short", "Short" ->
                    BABjField.EditorKind.INTEGER;
            case "double", "Double", "float", "Float", "BigDecimal" ->
                    BABjField.EditorKind.NUMBER;
            case "LocalDate" -> BABjField.EditorKind.DATE;
            case "LocalDateTime" -> BABjField.EditorKind.DATE_TIME;
            case "LocalTime" -> BABjField.EditorKind.TIME;
            default -> BABjField.EditorKind.TEXT;
        };
    }

    private static String boxedType(String type) {
        return switch (type) {
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "char" -> "Character";
            default -> type;
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
