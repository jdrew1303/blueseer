package com.blueseer.pay;

import com.blueseer.pay.CertificateDtos.CertificateScope;
import com.blueseer.pay.CertificateDtos.CertificateStatusDTO;
import com.blueseer.pay.CertificateDtos.CertificateValidationResult;
import com.blueseer.pay.PayrollIds.RegistrationId;

/** Controller backing S-03 Digital Certificate Manager. */
public interface ICertificateController {

    /** {@code CertificateValidationResult} carries a typed error enum, not a boolean, so the view can show the specific reason. */
    CertificateValidationResult saveCertificate(CertificateScope scope, byte[] p12Bytes, char[] password);

    /** Called on panel load and after every save. */
    CertificateStatusDTO getCertificateStatus(CertificateScope scope);

    CertificateValidationResult saveSubCertificate(RegistrationId regId, byte[] p12Bytes, char[] password);
}
