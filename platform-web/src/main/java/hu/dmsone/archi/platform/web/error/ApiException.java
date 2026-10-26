package hu.dmsone.archi.platform.web.error;

import org.springframework.http.HttpStatus;

/** Üzleti hiba, amely RFC 9457 Problem Details válasszá alakul. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "Nem található", what + " nem található");
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Hibás kérés", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Ütközés", detail);
    }

    public static ApiException preconditionFailed(String detail) {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "Elavult verzió", detail);
    }

    public static ApiException unprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nem feldolgozható", detail);
    }
}
