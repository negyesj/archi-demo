package hu.dmsone.archi.tenantadmin.loglevel;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/loglevels")
public class LogLevelController {

    private final LogLevelService service;

    public LogLevelController(LogLevelService service) {
        this.service = service;
    }

    @GetMapping
    public List<LogLevelService.Setting> list() {
        return service.list();
    }

    @PutMapping
    public LogLevelService.Setting set(@Valid @RequestBody LogLevelService.SetRequest req) {
        return service.set(req);
    }

    @DeleteMapping
    public ResponseEntity<Void> reset(@RequestParam String key) {
        service.reset(key);
        return ResponseEntity.noContent().build();
    }
}
