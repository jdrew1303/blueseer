package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.RegistrationId;

import java.util.Map;

/** DTOs backing S-01 New Company Wizard and S-02 Additional PAYE Registrations. */
public final class CompanySetupDtos {

    private CompanySetupDtos() {
    }

    /** {@code subCertStatus}: "Not Configured" / "Valid" / "Invalid" - driven by S-03's per-registration sub-certificate setup. */
    public record PayeRegistrationDTO(RegistrationId id, String registrationNumber, String description, String subCertStatus) {
    }

    public enum WizardStep { COMPANY_DETAILS, REGISTRATION, PAY_FREQUENCY, PASSWORD }

    /** Weekly+Monthly or Fortnightly+Monthly only - the exemplar is explicit that Weekly+Fortnightly is never valid. */
    public enum PayFrequencyCombo { WEEKLY_AND_MONTHLY, FORTNIGHTLY_AND_MONTHLY }

    public record CompanySetupDTO(
            String companyName,
            String address,
            String registrationNumber,
            PayFrequencyCombo payFrequencyCombo,
            String password) {
    }

    public record ValidationResult(boolean valid, Map<String, String> fieldErrors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, Map.of());
        }
    }
}
