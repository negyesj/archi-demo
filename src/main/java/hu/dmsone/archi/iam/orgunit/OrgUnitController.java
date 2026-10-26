package hu.dmsone.archi.iam.orgunit;

import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/t/{tenant}/iam/org-units")
public class OrgUnitController {

    private final OrgUnitService service;

    public OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("iam.read")
    public List<OrgUnitService.OrgUnit> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @RequiresPermission("iam.read")
    public OrgUnitService.OrgUnit get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("iam.write")
    public OrgUnitService.OrgUnit create(@Valid @RequestBody OrgUnitService.SaveOrgUnit req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @RequiresPermission("iam.write")
    public OrgUnitService.OrgUnit update(@PathVariable UUID id, @Valid @RequestBody OrgUnitService.SaveOrgUnit req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("iam.write")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/{id}/members")
    @RequiresPermission("iam.read")
    public List<UserDtos.UserSummary> members(@PathVariable UUID id) {
        return service.members(id);
    }

    @PutMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("iam.write")
    public void addMember(@PathVariable UUID id, @PathVariable UUID userId) {
        service.addMember(id, userId);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("iam.write")
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(id, userId);
    }
}
