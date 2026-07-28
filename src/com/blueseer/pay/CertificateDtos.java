package com.blueseer.pay;

import java.time.LocalDate;

/** DTOs backing S-03 Digital Certificate Manager. */
public final class CertificateDtos {

    private CertificateDtos() {
    }

    public enum CertificateScope { EMPLOYER, AGENT }

    /** {@code NONE} means no error - the save succeeded. */
    public enum CertificateErrorType { NONE, INVALID_PASSWORD, EXPIRED, MALFORMED, SERVICE_UNAVAILABLE }

    public record CertificateValidationResult(boolean success, CertificateErrorType errorType, LocalDate expiryDateOrNull) {
    }

    public record CertificateStatusDTO(boolean present, boolean valid, LocalDate expiryDateOrNull, CertificateErrorType errorTypeOrNull) {
    }
}
