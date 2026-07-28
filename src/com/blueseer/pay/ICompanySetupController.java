package com.blueseer.pay;

import com.blueseer.pay.CompanySetupDtos.CompanySetupDTO;
import com.blueseer.pay.CompanySetupDtos.PayeRegistrationDTO;
import com.blueseer.pay.CompanySetupDtos.ValidationResult;
import com.blueseer.pay.CompanySetupDtos.WizardStep;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.RegistrationId;

import java.util.List;

/** Controller backing S-01 New Company Wizard and S-02 Additional PAYE Registrations. */
public interface ICompanySetupController {

    /** Called on every btNext click - field-level errors the view renders inline. */
    ValidationResult validateStep(WizardStep step, CompanySetupDTO partial);

    /** Called once, on btFinish. */
    CompanyId createCompany(CompanySetupDTO data);

    List<PayeRegistrationDTO> listRegistrations(CompanyId id);

    PayeRegistrationDTO addRegistration(CompanyId id, String registrationNumber, String description);

    /** Also invalidates any ROS sub-certificate bound to that registration (S-03). */
    void removeRegistration(CompanyId id, RegistrationId regId);
}
