package com.blueseer.pay;

import com.blueseer.pay.ImportDtos.ColumnMappingDTO;
import com.blueseer.pay.ImportDtos.ImportCommitResultDTO;
import com.blueseer.pay.ImportDtos.ImportProfile;
import com.blueseer.pay.ImportDtos.ImportValidationResultDTO;

import java.io.File;
import java.util.List;

/**
 * Controller backing S-28 (Importing Hours from Text/CSV) and S-29 (Full
 * Periodic CSV Import) - one interface for both, per the roadmap's "same
 * wizard shape, only the schema being mapped differs" note; {@link
 * ImportProfile} is what tells {@code HoursImportParser}/{@code
 * FullPeriodCsvImporter} apart behind it.
 */
public interface IImportController {

    List<String> parseFileHeaders(File f, ImportProfile profile);

    ImportValidationResultDTO validateImport(File f, ColumnMappingDTO mapping, ImportProfile profile);

    /** Commits only the valid rows; excluded rows are reported, not silently dropped. */
    ImportCommitResultDTO commitImport(File f, ColumnMappingDTO mapping, ImportProfile profile);
}
