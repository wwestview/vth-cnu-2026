package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception;

import java.util.Objects;

public class MatchDomainException extends RuntimeException {

    private final String code;

    public MatchDomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "Error code is required");
    }

    public MatchDomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "Error code is required");
    }

    public String getCode() {
        return code;
    }
}
