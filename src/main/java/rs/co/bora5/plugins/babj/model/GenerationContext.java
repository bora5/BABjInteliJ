package rs.co.bora5.plugins.babj.model;

import java.util.List;

/**
 * All parameters needed to render the BABj quartet for one entity: the resolved base package,
 * entity name, the operator ({@code K}) type, the view's declarative metadata (roles registry,
 * selected roles, route, title, class name) and which artifacts to emit.
 */
public final class GenerationContext {

    private final String basePackage;
    private final String entityName;
    private final String kType;
    private final String rolesType;
    private final String rolesTypeFqn;
    private final List<String> roles;
    private final String viewName;
    private final String route;
    private final String pageTitle;
    private final List<BABjField> fields;

    private final boolean generateDto;
    private final boolean generateHome;
    private final boolean generateView;
    private final boolean generateWindow;
    private final boolean enableExport;
    private final boolean generateRest;
    private final String restPath;
    private final boolean generateCsvImport;
    private final boolean generateXlsImport;
    private final boolean generateReport;
    private final boolean enableAttachments;
    private final EntityModel.AttachmentSupport attachmentSupport;
    private final boolean generateMessagingAgent;
    private final boolean settingsAdministration;

    public GenerationContext(String basePackage, String entityName, String kType,
                             String rolesType, String rolesTypeFqn, List<String> roles,
                             String viewName, String route, String pageTitle, List<BABjField> fields,
                             boolean generateDto, boolean generateHome, boolean generateView,
                             boolean generateWindow, boolean enableExport, boolean generateRest,
                             String restPath, boolean generateCsvImport, boolean generateXlsImport,
                             boolean generateReport, boolean enableAttachments,
                             EntityModel.AttachmentSupport attachmentSupport,
                             boolean generateMessagingAgent, boolean settingsAdministration) {
        this.basePackage = basePackage;
        this.entityName = entityName;
        this.kType = kType;
        this.rolesType = rolesType;
        this.rolesTypeFqn = rolesTypeFqn;
        this.roles = List.copyOf(roles);
        this.viewName = viewName;
        this.route = route;
        this.pageTitle = pageTitle;
        this.fields = fields;
        this.generateDto = generateDto;
        this.generateHome = generateHome;
        this.generateView = generateView;
        this.generateWindow = generateWindow;
        this.enableExport = enableExport;
        this.generateRest = generateRest;
        this.restPath = restPath;
        this.generateCsvImport = generateCsvImport;
        this.generateXlsImport = generateXlsImport;
        this.generateReport = generateReport;
        this.enableAttachments = enableAttachments;
        this.attachmentSupport = attachmentSupport;
        this.generateMessagingAgent = generateMessagingAgent;
        this.settingsAdministration = settingsAdministration;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getKType() {
        return kType;
    }

    public String getRolesType() {
        return rolesType;
    }

    public String getRolesTypeFqn() {
        return rolesTypeFqn;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getViewName() {
        return viewName;
    }

    public String getRoute() {
        return route;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public List<BABjField> getFields() {
        return fields;
    }

    public boolean isGenerateDto() {
        return generateDto;
    }

    public boolean isGenerateHome() {
        return generateHome;
    }

    public boolean isGenerateView() {
        return generateView;
    }

    public boolean isGenerateWindow() {
        return generateWindow;
    }

    public boolean isEnableExport() {
        return enableExport;
    }

    public boolean isGenerateRest() {
        return generateRest;
    }

    public String getRestPath() {
        return restPath;
    }

    public boolean isGenerateCsvImport() {
        return generateCsvImport;
    }

    public boolean isGenerateXlsImport() {
        return generateXlsImport;
    }

    public boolean isGenerateReport() {
        return generateReport;
    }

    public boolean isEnableAttachments() {
        return enableAttachments;
    }

    public EntityModel.AttachmentSupport getAttachmentSupport() {
        return attachmentSupport;
    }

    public boolean isGenerateMessagingAgent() {
        return generateMessagingAgent;
    }

    public boolean isSettingsAdministration() {
        return settingsAdministration;
    }

    // Package helpers -------------------------------------------------------

    public String dtoPackage() {
        return basePackage + ".front.views.projections";
    }

    public String homePackage() {
        return basePackage + ".sesion";
    }

    public String viewPackage() {
        return basePackage + ".front.views";
    }

    public String windowPackage() {
        return basePackage + ".front.windowses";
    }

    public String modelPackage() {
        return basePackage + ".model";
    }

    public String restPackage() {
        return basePackage + ".sesion.rest";
    }

    public String agentPackage() {
        return basePackage + ".agent";
    }
}
