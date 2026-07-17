package rs.co.bora5.plugins.babj.model;

import java.util.List;

/**
 * All parameters needed to render the babj quartet for one entity: the resolved base package,
 * entity name, the operator ({@code K}) type, the view's declarative metadata (role, route, title,
 * class name) and which artifacts to emit.
 */
public final class GenerationContext {

    private final String basePackage;
    private final String entityName;
    private final String kType;
    private final String role;
    private final String viewName;
    private final String route;
    private final String pageTitle;
    private final List<BABjField> fields;

    private final boolean generateDto;
    private final boolean generateHome;
    private final boolean generateView;
    private final boolean generateWindow;

    public GenerationContext(String basePackage, String entityName, String kType, String role,
                             String viewName, String route, String pageTitle, List<BABjField> fields,
                             boolean generateDto, boolean generateHome, boolean generateView,
                             boolean generateWindow) {
        this.basePackage = basePackage;
        this.entityName = entityName;
        this.kType = kType;
        this.role = role;
        this.viewName = viewName;
        this.route = route;
        this.pageTitle = pageTitle;
        this.fields = fields;
        this.generateDto = generateDto;
        this.generateHome = generateHome;
        this.generateView = generateView;
        this.generateWindow = generateWindow;
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

    public String getRole() {
        return role;
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
}
