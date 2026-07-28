package com.blueseer.pay;

import com.blueseer.pay.JournalDtos.AccountingTarget;
import com.blueseer.pay.JournalDtos.JournalMappingRowDTO;
import com.blueseer.pay.JournalDtos.MappingCompletenessDTO;
import com.blueseer.pay.JournalDtos.NativeGlPostResult;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.util.List;

/** Controller backing S-62 Payroll Journal Mapping and S-63 Journal Export. */
public interface IJournalController {

    List<JournalMappingRowDTO> getMapping(CompanyId id, AccountingTarget target);

    /** Backs {@code JournalMappingService}. */
    SaveResult saveMapping(CompanyId id, AccountingTarget target, List<JournalMappingRowDTO> rows);

    MappingCompletenessDTO checkMappingCompleteness(CompanyId id, AccountingTarget target);

    /** Backs {@code JournalExportBuilder}. Not used for {@code NATIVE_BLUESEER_GL} - see {@link #postToNativeGl}. */
    byte[] createExportFile(CompanyId id, int periodNumber, AccountingTarget target);

    /** Backs {@code NativeGlPoster}. */
    NativeGlPostResult postToNativeGl(CompanyId id, int periodNumber);
}
