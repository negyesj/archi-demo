package hu.dmsone.archi.iam.apiclient;

import hu.dmsone.archi.platform.web.PlatformWebProperties;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/t/{tenant}/iam/api-clients")
public class ApiClientController {

    private final ApiClientService service;
    private final PlatformWebProperties web;

    public ApiClientController(ApiClientService service, PlatformWebProperties web) {
        this.service = service;
        this.web = web;
    }

    @GetMapping
    @RequiresPermission("integration.manage")
    public List<ApiClientService.ApiClient> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("integration.manage")
    public ApiClientService.Credentials create(@Valid @RequestBody ApiClientService.CreateRequest req) {
        return service.create(req, web.getIssuerBase());
    }

    @PostMapping("/{id}/rotate-secret")
    @RequiresPermission("integration.manage")
    public ApiClientService.Credentials rotate(@PathVariable UUID id) {
        return service.rotateSecret(id, web.getIssuerBase());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("integration.manage")
    public void revoke(@PathVariable UUID id) {
        service.revoke(id);
    }
}
