package hu.dmsone.archi.iam.user;

import hu.dmsone.archi.iam.common.Page;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/t/{tenant}/iam/users")
public class UserController {

    private final UserService service;
    private final UserRepository repository;

    public UserController(UserService service, UserRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission("iam.read")
    public Page<UserDtos.User> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.search(q, active, type, page, size);
    }

    /** Választó (pl. ügyintéző) más szolgáltatásoknak: bejelentkezett felhasználó hívhatja, csak aktív emberek. */
    @GetMapping("/lookup")
    public List<UserDtos.UserSummary> lookup(@RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "20") int limit) {
        return repository.search(q, true, UserService.HUMAN, Math.min(Math.max(limit, 1), 50), 0).stream()
                .map(u -> new UserDtos.UserSummary(u.id(), u.username(), u.fullName(), u.email(), u.active()))
                .toList();
    }

    /** Egy felhasználó rövid adata más szolgáltatásoknak (ellenőrzéshez, megjelenítéshez). */
    @GetMapping("/{id}/summary")
    public UserDtos.UserSummary summary(@PathVariable UUID id) {
        UserDtos.User u = service.get(id);
        return new UserDtos.UserSummary(u.id(), u.username(), u.fullName(), u.email(), u.active());
    }

    @GetMapping("/{id}")
    @RequiresPermission("iam.read")
    public UserDtos.User get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/memberships")
    @RequiresPermission("iam.read")
    public UserDtos.Memberships memberships(@PathVariable UUID id) {
        return service.memberships(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("iam.write")
    public UserDtos.CreatedUser create(@Valid @RequestBody UserDtos.CreateUser req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @RequiresPermission("iam.write")
    public UserDtos.User update(@PathVariable UUID id, @Valid @RequestBody UserDtos.UpdateUser req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("iam.write")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/reset-password")
    @RequiresPermission("iam.write")
    public Map<String, String> resetPassword(@PathVariable UUID id) {
        return Map.of("temporaryPassword", service.resetPassword(id));
    }
}
