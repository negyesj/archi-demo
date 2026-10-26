package hu.dmsone.archi.iam.publicapi;

import hu.dmsone.archi.iam.group.GroupService;
import hu.dmsone.archi.iam.orgunit.OrgUnitService;
import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.iam.user.UserService;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Publikus integrációs API v1 (csak olvasás). Saját DTO-k, hogy a belső modell változása ne törje el a
 * külső szerződést. Elérés: {@code https://api.<domain>/v1/t/{tenant}/iam/...}
 */
@RestController
@RequestMapping("/public/v1/t/{tenant}/iam")
public class IamPublicApiV1 {

    public record PublicUser(UUID id, String username, String fullName, String email, boolean active) { }

    public record PublicGroup(UUID id, String name, String description) { }

    public record PublicOrgUnit(UUID id, String code, String name, UUID parentId) { }

    public record PageV1<T>(List<T> items, long total, int page, int size) { }

    private final UserService users;
    private final GroupService groups;
    private final OrgUnitService orgUnits;

    public IamPublicApiV1(UserService users, GroupService groups, OrgUnitService orgUnits) {
        this.users = users;
        this.groups = groups;
        this.orgUnits = orgUnits;
    }

    @GetMapping("/users")
    @RequiresPermission("iam.read")
    public PageV1<PublicUser> users(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        var p = users.search(q, null, UserService.HUMAN, page, size);
        return new PageV1<>(p.items().stream().map(IamPublicApiV1::toPublic).toList(), p.total(), p.page(), p.size());
    }

    @GetMapping("/users/{id}")
    @RequiresPermission("iam.read")
    public PublicUser user(@PathVariable UUID id) {
        return toPublic(users.get(id));
    }

    @GetMapping("/groups")
    @RequiresPermission("iam.read")
    public List<PublicGroup> groups() {
        return groups.list(null).stream().map(g -> new PublicGroup(g.id(), g.name(), g.description())).toList();
    }

    @GetMapping("/org-units")
    @RequiresPermission("iam.read")
    public List<PublicOrgUnit> orgUnits() {
        return orgUnits.list().stream().map(o -> new PublicOrgUnit(o.id(), o.code(), o.name(), o.parentId())).toList();
    }

    private static PublicUser toPublic(UserDtos.User u) {
        return new PublicUser(u.id(), u.username(), u.fullName(), u.email(), u.active());
    }
}
