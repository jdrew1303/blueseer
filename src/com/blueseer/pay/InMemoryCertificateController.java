package com.blueseer.pay;

import com.blueseer.pay.CertificateDtos.CertificateErrorType;
import com.blueseer.pay.CertificateDtos.CertificateScope;
import com.blueseer.pay.CertificateDtos.CertificateStatusDTO;
import com.blueseer.pay.CertificateDtos.CertificateValidationResult;
import com.blueseer.pay.PayrollIds.RegistrationId;
import com.blueseer.pay.PayrollStubStore.CertificateRecord;

import java.time.LocalDate;

/**
 * Track A/B/C stub for {@link ICertificateController}, backed by {@link
 * PayrollStubStore}. There is no real ROS certificate-authority validation
 * in this build (that needs Revenue's own test-environment credentials, per
 * roadmap §3.3's deferral note for RPN retrieval) - "validation" here is a
 * structural sanity check only: a PKCS12 (.p12/.pfx) file is DER-encoded
 * ASN.1, whose first byte is always {@code 0x30} (a SEQUENCE tag), so a file
 * that doesn't start with that byte is rejected as malformed; a genuinely
 * malformed or wrong-format file will fail this check, but this is not a
 * cryptographic validation of the certificate's actual contents or chain of
 * trust.
 */
final class InMemoryCertificateController implements ICertificateController {

    private final PayrollStubStore store;

    InMemoryCertificateController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public CertificateValidationResult saveCertificate(CertificateScope scope, byte[] p12Bytes, char[] password) {
        CertificateValidationResult result = validate(p12Bytes, password);
        CertificateRecord record = result.success() ? new CertificateRecord(true, "Valid, expires " + result.expiryDateOrNull()) : null;
        if (scope == CertificateScope.EMPLOYER) {
            if (record != null) {
                store.employerCertificate = record;
            }
        } else if (record != null) {
            store.agentCertificate = record;
        }
        return result;
    }

    @Override
    public CertificateStatusDTO getCertificateStatus(CertificateScope scope) {
        CertificateRecord record = scope == CertificateScope.EMPLOYER ? store.employerCertificate : store.agentCertificate;
        if (record == null || !record.present()) {
            return new CertificateStatusDTO(false, false, null, null);
        }
        return new CertificateStatusDTO(true, true, LocalDate.now().plusYears(1), CertificateErrorType.NONE);
    }

    @Override
    public CertificateValidationResult saveSubCertificate(RegistrationId regId, byte[] p12Bytes, char[] password) {
        CertificateValidationResult result = validate(p12Bytes, password);
        if (result.success()) {
            store.subCertStatusByRegistration.put(regId, "Valid");
        } else {
            store.subCertStatusByRegistration.put(regId, "Invalid");
        }
        return result;
    }

    private static CertificateValidationResult validate(byte[] p12Bytes, char[] password) {
        if (password == null || password.length == 0) {
            return new CertificateValidationResult(false, CertificateErrorType.INVALID_PASSWORD, null);
        }
        if (p12Bytes == null || p12Bytes.length < 4 || p12Bytes[0] != 0x30) {
            return new CertificateValidationResult(false, CertificateErrorType.MALFORMED, null);
        }
        return new CertificateValidationResult(true, CertificateErrorType.NONE, LocalDate.now().plusYears(1));
    }
}
