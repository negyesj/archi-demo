package hu.dmsone.archi.tenantadmin.tenant;

import hu.dmsone.archi.platform.tenant.TenantStatus;
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

@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    public List<TenantRecord> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    public TenantRecord get(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TenantRecord create(@Valid @RequestBody TenantDtos.CreateTenantRequest req) {
        return service.create(req);
    }

    @PutMapping("/{code}")
    public TenantRecord update(@PathVariable String code, @Valid @RequestBody TenantDtos.UpdateTenantRequest req) {
        return service.update(code, req);
    }

    @PutMapping("/{code}/services")
    public TenantRecord services(@PathVariable String code, @RequestBody TenantDtos.ServicesRequest req) {
        return service.setServices(code, req.services());
    }

    @PostMapping("/{code}/suspend")
    public TenantRecord suspend(@PathVariable String code) {
        return service.changeStatus(code, TenantStatus.SUSPENDED);
    }

    @PostMapping("/{code}/activate")
    public TenantRecord activate(@PathVariable String code) {
        return service.changeStatus(code, TenantStatus.ACTIVE);
    }

    @PostMapping("/{code}/archive")
    public TenantRecord archive(@PathVariable String code) {
        return service.changeStatus(code, TenantStatus.ARCHIVED);
    }

    @PostMapping("/{code}/provision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TenantRecord retry(@PathVariable String code) {
        return service.retryProvisioning(code);
    }

    @PostMapping("/{code}/bootstrap-password")
    public TenantDtos.BootstrapPassword bootstrapPassword(@PathVariable String code) {
        return service.takeBootstrapPassword(code);
    }
}
