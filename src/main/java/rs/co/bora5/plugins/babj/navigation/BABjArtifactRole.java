package rs.co.bora5.plugins.babj.navigation;

/** The conventional roles that form one BABj CRUD module. */
public enum BABjArtifactRole {
    ENTITY("Entity"),
    DTO("DTO"),
    HOME("Home"),
    VIEW("View"),
    WINDOW("Edit window");

    private final String displayName;

    BABjArtifactRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
