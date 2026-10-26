package hu.dmsone.archi.tenantadmin.adminuser;

import hu.dmsone.archi.tenantadmin.security.AdminGuard;
import hu.dmsone.archi.tenantadmin.security.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AdminUser me(HttpServletRequest request) {
        return AdminGuard.current(request);
    }

    @GetMapping("/admin-users")
    public List<AdminUser> list() {
        return service.list();
    }

    @PostMapping("/admin-users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.Created create(@Valid @RequestBody AdminUserService.CreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/admin-users/{id}")
    public AdminUser update(@PathVariable UUID id, @Valid @RequestBody AdminUserService.UpdateRequest req) {
        return service.update(id, req);
    }
}
