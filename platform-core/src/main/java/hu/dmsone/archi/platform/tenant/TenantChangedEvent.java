package hu.dmsone.archi.platform.tenant;

/** Spring alkalmazásesemény: egy tenant állapota megváltozott (vagy induláskor beolvastuk). */
public record TenantChangedEvent(String code, TenantInfo tenant) {
    public boolean removed() {
        return tenant == null;
    }
}
