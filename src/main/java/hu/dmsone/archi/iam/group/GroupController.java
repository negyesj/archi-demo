package hu.dmsone.archi.iam.group;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/t/{tenant}/iam/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("iam.read")
    public List<GroupService.Group> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    @GetMapping("/{id}")
    @RequiresPermission("iam.read")
    public GroupService.Group get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("iam.write")
    public GroupService.Group create(@Valid @RequestBody GroupService.SaveGroup req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @RequiresPermission("iam.write")
    public GroupService.Group update(@PathVariable UUID id, @Valid @RequestBody GroupService.SaveGroup req) {
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
