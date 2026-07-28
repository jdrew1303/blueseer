package com.blueseer.pay;

import com.blueseer.pay.CompanySetupDtos.CompanySetupDTO;
import com.blueseer.pay.CompanySetupDtos.PayeRegistrationDTO;
import com.blueseer.pay.CompanySetupDtos.ValidationResult;
import com.blueseer.pay.CompanySetupDtos.WizardStep;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.RegistrationId;
import com.blueseer.pay.PayrollStubStore.PayeRegistrationRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Track A stub for {@link ICompanySetupController}, backed by {@link PayrollStubStore} for the S-02 registration list. */
final class InMemoryCompanySetupController implements ICompanySetupController {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,}$");

    private final PayrollStubStore store;

    InMemoryCompanySetupController() {
        this(PayrollStubStore.shared());
    }

    InMemoryCompanySetupController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public ValidationResult validateStep(WizardStep step, CompanySetupDTO partial) {
        Map<String, String> errors = new HashMap<>();
        switch (step) {
            case COMPANY_DETAILS -> {
                if (partial.companyName() == null || partial.companyName().isBlank()) {
                    errors.put("companyName", "Company Name is required");
                } else if (partial.companyName().length() > 100) {
                    errors.put("companyName", "Company Name must be 100 characters or fewer");
                }
            }
            case REGISTRATION -> {
                // Soft-validated only (mask warning icon on focus-lost, per S-01) - the
                // exemplar's own "normally 7 digits and 1 or 2 letters" wording implies
                // exceptions exist, so this step never blocks btNext.
            }
            case PAY_FREQUENCY -> {
                if (partial.payFrequencyCombo() == null) {
                    errors.put("payFrequencyCombo", "Select a pay frequency combination");
                }
            }
            case PASSWORD -> {
                if (partial.password() == null || !PASSWORD_PATTERN.matcher(partial.password()).matches()) {
                    errors.put("password", "Minimum 4 alphanumeric characters");
                }
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : new ValidationResult(false, errors);
    }

    @Override
    public CompanyId createCompany(CompanySetupDTO data) {
        return new CompanyId(System.currentTimeMillis());
    }

    @Override
    public List<PayeRegistrationDTO> listRegistrations(CompanyId id) {
        List<PayeRegistrationDTO> result = new ArrayList<>();
        for (PayeRegistrationRecord r : store.registrations) {
            String status = store.subCertStatusByRegistration.getOrDefault(r.id(), "Not Configured");
            result.add(new PayeRegistrationDTO(r.id(), r.registrationNumber(), r.description(), status));
        }
        return result;
    }

    @Override
    public PayeRegistrationDTO addRegistration(CompanyId id, String registrationNumber, String description) {
        RegistrationId regId = new RegistrationId(store.nextRegistrationId());
        store.registrations.add(new PayeRegistrationRecord(regId, registrationNumber, description));
        return new PayeRegistrationDTO(regId, registrationNumber, description, "Not Configured");
    }

    @Override
    public void removeRegistration(CompanyId id, RegistrationId regId) {
        store.registrations.removeIf(r -> r.id().equals(regId));
        store.subCertStatusByRegistration.remove(regId);
    }
}
