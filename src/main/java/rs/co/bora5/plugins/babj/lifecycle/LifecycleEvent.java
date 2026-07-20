package rs.co.bora5.plugins.babj.lifecycle;

/** BAB framework events whose execution order can be visualized statically. */
public enum LifecycleEvent {
    VALIDATE("Validate"),
    MULTI_VALIDATE("Multi validate"),
    STORNO("Storno"),
    ARRIVED("Arrived"),
    INVALIDATE("Invalidate"),
    PAYOUT("Payout"),
    UNPAYOUT("Unpayout"),
    DELETE("Delete"),
    CREATE("Create / persist"),
    EDIT("Edit / save"),
    REPORT_EXECUTE("Report execution");

    private final String displayName;

    LifecycleEvent(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
