package hu.dmsone.archi.platform.web.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Egységes hibaválasz: RFC 9457 Problem Details, mindig {@code traceId}-val a support számára. */
@RestControllerAdvice
public class PlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> api(ApiException e) {
        return respond(e.getStatus(), e.getTitle(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "Nincs jogosultság", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
        ResponseEntity<ProblemDetail> r = respond(HttpStatus.BAD_REQUEST, "Érvénytelen adatok", "A kérés mezői hibásak");
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
        r.getBody().setProperty("errors", errors);
        return r;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> optimistic(OptimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT, "Párhuzamos módosítás", "Az adatot közben más módosította, töltse újra.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e) {
        log.warn("Adatintegritási hiba", e);
        return respond(HttpStatus.CONFLICT, "Adatintegritási hiba", "A művelet ütközik a meglévő adatokkal.");
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ProblemDetail> downstream(RestClientResponseException e) {
        log.warn("Függő szolgáltatás hibát adott: {}", e.getStatusCode());
        HttpStatus status = e.getStatusCode().is4xxClientError() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_GATEWAY;
        return respond(status, "Függő szolgáltatás hiba", "Egy kapcsolódó szolgáltatás hibát adott (" + e.getStatusCode().value() + ").");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Nem található", "Ismeretlen végpont");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> badInput(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, "Hibás kérés", "A kérés formátuma vagy egy paramétere érvénytelen.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e) {
        if (e instanceof ErrorResponse er) {
            // A keretrendszer 4xx/5xx hibái (rossz metódus, hiányzó paraméter, ResponseStatusException, ...)
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return respond(status, status.getReasonPhrase(), er.getBody().getDetail());
        }
        log.error("Váratlan hiba", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Váratlan hiba",
                "Váratlan hiba történt. Kérjük, adja meg a supportnak a hibakódot.");
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("traceId", MDC.get("traceId"));
        return ResponseEntity.status(status).body(pd);
    }
}
