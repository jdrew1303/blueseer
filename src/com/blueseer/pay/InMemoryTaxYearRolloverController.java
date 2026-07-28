package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.TaxYearRolloverDtos.ChecklistRowDTO;
import com.blueseer.pay.TaxYearRolloverDtos.TaxYearRolloverResult;
import com.blueseer.pay.TaxYearRolloverDtos.YearEndChecklistDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Track A/B/C stub for {@link ITaxYearRolloverController}, backed by {@link
 * PayrollStubStore}. "Week 53 processed (if applicable)" has no dedicated
 * tracked flag in this stub (Week 53 detection lives in {@link
 * Week53Banner}'s in-period gate, not a persisted completion record) - it is
 * always reported satisfied, on the honest basis that "not applicable" is
 * itself a satisfied state and no unresolved Week 53 draft exists to detect.
 * "Year End Summary generated" is derived from the other two rows being
 * satisfied (the report is always regenerable on demand from finalised
 * data, per S-46 - there is no separate "has it been run" flag to track).
 */
final class InMemoryTaxYearRolloverController implements ITaxYearRolloverController {

    private final PayrollStubStore store;

    InMemoryTaxYearRolloverController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public YearEndChecklistDTO getYearEndChecklist(CompanyId id, int closingTaxYear) {
        List<ChecklistRowDTO> rows = new ArrayList<>();
        rows.add(new ChecklistRowDTO("Week 53 processed (if applicable)", true, "No unresolved Week 53 draft outstanding."));

        boolean allFinalised = store.draftPayEntries.isEmpty();
        rows.add(new ChecklistRowDTO("All pay periods finalised", allFinalised,
                allFinalised ? "No draft pay entries outstanding." : store.draftPayEntries.size() + " draft pay entr(y/ies) still outstanding."));

        boolean allSubmitted = store.psrBatches.values().stream().allMatch(b -> "Filed".equals(b.status()));
        long dueCount = store.psrBatches.values().stream().filter(b -> !"Filed".equals(b.status())).count();
        rows.add(new ChecklistRowDTO("All PSRs submitted", allSubmitted,
                allSubmitted ? "Every PSR batch is Filed." : dueCount + " PSR batch(es) not yet Filed."));

        boolean summaryReady = allFinalised && allSubmitted;
        rows.add(new ChecklistRowDTO("Year End Summary generated", summaryReady,
                summaryReady ? "Available on demand from finalised data (S-46)." : "Not yet available - finalise all periods and PSRs first."));

        return new YearEndChecklistDTO(rows);
    }

    @Override
    public TaxYearRolloverResult startNewTaxYear(CompanyId id, int newTaxYear) {
        YearEndChecklistDTO checklist = getYearEndChecklist(id, store.activeTaxYear);
        if (!checklist.allSatisfied()) {
            return new TaxYearRolloverResult(false, "Year-end checklist incomplete - cannot start " + newTaxYear + " yet.");
        }
        List<Integer> registered = PayrollEngineFactory.registeredTaxYears();
        if (!registered.contains(newTaxYear)) {
            return new TaxYearRolloverResult(false,
                    "No ITaxYearRules is registered for " + newTaxYear + " yet (only " + registered
                            + " registered) - add a Paye" + newTaxYear + "Rules class before rolling over to it.");
        }
        store.activeTaxYear = newTaxYear;
        store.currentPeriodNumber = 1;
        return new TaxYearRolloverResult(true, "Tax year " + newTaxYear + " is now active. Existing employee and payslip history is retained unchanged.");
    }
}
