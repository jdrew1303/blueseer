/* Irish Payroll module (com.blueseer.pay) - new tables for 2026 tax year support.
   Grounded in docs/architecture/irish-payroll-2026-roadmap.md and
   irish-payroll-2026-screen-specs.md (S-04..S-16, S-31..S-33).
   emp_pay_ie/emp_rpn_ie/psr_batch_ie are each a table family: the RPN and
   PSR "families" each need a companion history/line table alongside the
   current-state table, matching how the screen specs describe RPN logging
   (S-16) and PSR line-item detail (S-31) as distinct persistence concerns. */

/* add tables */

CREATE TABLE IF NOT EXISTS `emp_pay_ie` (
  `payie_nbr` int(8) NOT NULL DEFAULT '0',
  `payie_pps` varchar(9) NOT NULL DEFAULT '',
  `payie_employment_id` varchar(20) NOT NULL DEFAULT '',
  `payie_director` tinyint(1) NOT NULL DEFAULT '0',
  `payie_paymethod` varchar(20) NOT NULL DEFAULT '',
  `payie_bank` varchar(50) NOT NULL DEFAULT '',
  `payie_branch` varchar(50) NOT NULL DEFAULT '',
  `payie_sortcode` varchar(8) NOT NULL DEFAULT '',
  `payie_acctnum` varchar(10) NOT NULL DEFAULT '',
  `payie_creditunionref` varchar(30) NOT NULL DEFAULT '',
  `payie_iban` varchar(34) NOT NULL DEFAULT '',
  `payie_bic` varchar(11) NOT NULL DEFAULT '',
  `payie_payslip_password` varchar(60) NOT NULL DEFAULT '',
  `payie_contract_type` varchar(20) NOT NULL DEFAULT '',
  `payie_cso_occupation_code` varchar(20) NOT NULL DEFAULT '',
  `payie_cso_hours_category` varchar(20) NOT NULL DEFAULT '',
  PRIMARY KEY (`payie_nbr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* current-year Revenue Details + RPN-derived credits/cut-off + mid-year
   cumulatives (S-06, S-10) - one row per employee per tax year, since
   credits/cut-off/cumulatives are all year-scoped. Never written to except
   via the RPN import path (S-14/S-15) for emprpn_credit/emprpn_cutoff. */
CREATE TABLE IF NOT EXISTS `emp_rpn_ie` (
  `emprpn_nbr` int(8) NOT NULL DEFAULT '0',
  `emprpn_taxyear` int(4) NOT NULL DEFAULT '0',
  `emprpn_startdate` date DEFAULT NULL,
  `emprpn_startweek` int(2) NOT NULL DEFAULT '0',
  `emprpn_prsiclass` varchar(4) NOT NULL DEFAULT '',
  `emprpn_exemptions` varchar(200) NOT NULL DEFAULT '',
  `emprpn_basis` varchar(20) NOT NULL DEFAULT 'EMERGENCY',
  `emprpn_credit` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpn_cutoff` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpn_prior_gross` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpn_prior_tax` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpn_prior_prsi` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpn_prior_usc` decimal(14,2) NOT NULL DEFAULT '0',
  PRIMARY KEY (`emprpn_nbr`, `emprpn_taxyear`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* RPN request/retrieval audit log (S-16 tblRpnLog, S-15 diff rows) */
CREATE TABLE IF NOT EXISTS `emp_rpn_ie_log` (
  `emprpnl_id` int(12) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `emprpnl_nbr` int(8) NOT NULL DEFAULT '0',
  `emprpnl_date` date DEFAULT NULL,
  `emprpnl_reqtype` varchar(30) NOT NULL DEFAULT '',
  `emprpnl_result` varchar(30) NOT NULL DEFAULT '',
  `emprpnl_oldcredit` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpnl_newcredit` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpnl_oldcutoff` decimal(14,2) NOT NULL DEFAULT '0',
  `emprpnl_newcutoff` decimal(14,2) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* PSR submission batches (S-31 summary, S-32 control panel) */
CREATE TABLE IF NOT EXISTS `psr_batch_ie` (
  `psrb_id` int(12) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `psrb_paydate` date DEFAULT NULL,
  `psrb_status` varchar(20) NOT NULL DEFAULT '',
  `psrb_refnbr` varchar(40) NOT NULL DEFAULT '',
  `psrb_payslipcount` int(6) NOT NULL DEFAULT '0',
  `psrb_countreturned` int(6) NOT NULL DEFAULT '0',
  `psrb_payetotal` decimal(14,2) NOT NULL DEFAULT '0',
  `psrb_prsitotal` decimal(14,2) NOT NULL DEFAULT '0',
  `psrb_usctotal` decimal(14,2) NOT NULL DEFAULT '0',
  `psrb_lpttotal` decimal(14,2) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* per-employee PSR line items within a batch (S-31 tblPsrDetail) */
CREATE TABLE IF NOT EXISTS `psr_line_ie` (
  `psrl_id` int(12) NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `psrl_batchid` int(12) NOT NULL DEFAULT '0',
  `psrl_empnbr` int(8) NOT NULL DEFAULT '0',
  `psrl_paye` decimal(14,2) NOT NULL DEFAULT '0',
  `psrl_prsi` decimal(14,2) NOT NULL DEFAULT '0',
  `psrl_usc` decimal(14,2) NOT NULL DEFAULT '0',
  `psrl_lpt` decimal(14,2) NOT NULL DEFAULT '0',
  index psrl_batchid_idx (`psrl_batchid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* add Payroll menu (own top-level entry under root, not a copy of the
   exemplar's separate top-nav - integrates into BlueSeer's existing
   menu_mstr/menu_tree system exactly like HR/Finance/etc. do).
   Grouped into submenus by roadmap §3.x section rather than one flat list -
   a flat list grew to 54 items over the course of this build, which does
   not fit a 1080px-tall screen as a single JPopupMenu (roughly 40 items is
   the practical ceiling at default row height): items past that point
   render off-screen, so a click computed from their nominal position can
   land on the desktop/taskbar underneath instead of the menu, not just an
   inconvenience for a human user but something that broke automated
   UI-driving entirely. Only the two most frequently used screens
   (Employee Maintenance, Payroll Calendar) stay directly under Payroll;
   everything else moves into one of twelve submenus, none of which exceeds
   eight items. */
INSERT INTO menu_mstr VALUES ('Payroll','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('root','Payroll',14,'JMenu','Payroll','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollEmployeeMaster','', 'com.blueseer.pay.EmployeeMaintIE','JMenuItem','payempm');
INSERT INTO menu_tree VALUES ('Payroll','PayrollEmployeeMaster',1,'JMenuItem','Employee Maintenance','','','', 1, 1);

/* §3.4 Payroll Calendar & Pay Processing (S-17...S-22). S-23 Payslip
   Workings and S-24 Computational Anomaly are deliberately not menu items -
   per the screen spec both are only ever opened contextually (a payslip
   drill-in / the S-22 finalisation flow), never from a menu on their own. */
INSERT INTO menu_mstr VALUES ('PayrollCalendar','', 'com.blueseer.pay.PayrollCalendarPanel','JMenuItem','paycal');
INSERT INTO menu_tree VALUES ('Payroll','PayrollCalendar',2,'JMenuItem','Payroll Calendar','','','', 1, 1);

/* §3.1 Company Setup (S-01...S-03). */
INSERT INTO menu_mstr VALUES ('PayrollCompanySetupMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollCompanySetupMenu',3,'JMenu','Company Setup','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollCompanySetup','', 'com.blueseer.pay.CompanySetupLauncherPanel','JMenuItem','paycosetup');
INSERT INTO menu_tree VALUES ('PayrollCompanySetupMenu','PayrollCompanySetup',1,'JMenuItem','New Company Setup','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollAdditionalRegistrations','', 'com.blueseer.pay.AdditionalRegistrationsPanel','JMenuItem','payaddreg');
INSERT INTO menu_tree VALUES ('PayrollCompanySetupMenu','PayrollAdditionalRegistrations',2,'JMenuItem','Additional PAYE Registrations','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollDigitalCertificate','', 'com.blueseer.pay.DigitalCertificatePanel','JMenuItem','paycert');
INSERT INTO menu_tree VALUES ('PayrollCompanySetupMenu','PayrollDigitalCertificate',3,'JMenuItem','Digital Certificate Manager','','','', 1, 1);

/* §3.3 Revenue Payroll Notifications (S-14...S-16). S-14 has no menu item of
   its own - it's triggered contextually from S-06's post-save "Send an RPN
   request now?" prompt, per the screen spec. */
INSERT INTO menu_mstr VALUES ('PayrollRpnMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollRpnMenu',4,'JMenu','RPN','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollRpnBulkRetrieval','', 'com.blueseer.pay.RpnBulkRetrievalLauncherPanel','JMenuItem','payrpnbulk');
INSERT INTO menu_tree VALUES ('PayrollRpnMenu','PayrollRpnBulkRetrieval',1,'JMenuItem','RPN Retrieval (Bulk)','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollRpnLog','', 'com.blueseer.pay.RpnLogPanel','JMenuItem','payrpnlog');
INSERT INTO menu_tree VALUES ('PayrollRpnMenu','PayrollRpnLog',2,'JMenuItem','RPN Logs & Reminders','','','', 1, 1);

/* §3.4 Pay Processing (S-18...S-22). */
INSERT INTO menu_mstr VALUES ('PayrollPayProcessingMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollPayProcessingMenu',5,'JMenu','Pay Processing','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPayEntry','', 'com.blueseer.pay.PayEntryPanel','JMenuItem','payentry');
INSERT INTO menu_tree VALUES ('PayrollPayProcessingMenu','PayrollPayEntry',1,'JMenuItem','Pay Entry','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollQuickEdit','', 'com.blueseer.pay.QuickEditPanel','JMenuItem','payqedit');
INSERT INTO menu_tree VALUES ('PayrollPayProcessingMenu','PayrollQuickEdit',2,'JMenuItem','Quick Edit Entry','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollNetToGross','', 'com.blueseer.pay.NetToGrossPanel','JMenuItem','payn2g');
INSERT INTO menu_tree VALUES ('PayrollPayProcessingMenu','PayrollNetToGross',3,'JMenuItem','Net to Gross Payments','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPreview','', 'com.blueseer.pay.PayrollPreviewPanel','JMenuItem','paypreview');
INSERT INTO menu_tree VALUES ('PayrollPayProcessingMenu','PayrollPreview',4,'JMenuItem','Payroll Preview','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollFinalise','', 'com.blueseer.pay.FinalisePanel','JMenuItem','payfinal');
INSERT INTO menu_tree VALUES ('PayrollPayProcessingMenu','PayrollFinalise',5,'JMenuItem','Finalise Pay Period','','','', 1, 1);

/* S-25...S-27 - director/share/cycle additions, still §3.4. S-30 Week 53
   Handling has no menu item of its own (it's the Week53Banner embedded in
   Pay Entry/Quick Edit/Finalise). */
INSERT INTO menu_mstr VALUES ('PayrollDirectorsBenefitsMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollDirectorsBenefitsMenu',6,'JMenu','Directors & Share Schemes','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollDirectorsFees','', 'com.blueseer.pay.DirectorsFeesPanel','JMenuItem','paydirfee');
INSERT INTO menu_tree VALUES ('PayrollDirectorsBenefitsMenu','PayrollDirectorsFees',1,'JMenuItem','Directors Fees','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollShareRemuneration','', 'com.blueseer.pay.ShareRemunerationPanel','JMenuItem','payshare');
INSERT INTO menu_tree VALUES ('PayrollDirectorsBenefitsMenu','PayrollShareRemuneration',2,'JMenuItem','Share-Based Remuneration','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollCycleToWork','', 'com.blueseer.pay.CycleToWorkPanel','JMenuItem','paycycle');
INSERT INTO menu_tree VALUES ('PayrollDirectorsBenefitsMenu','PayrollCycleToWork',3,'JMenuItem','Cycle to Work Scheme','','','', 1, 1);

/* §3.4 CSV Import (S-28/S-29). */
INSERT INTO menu_mstr VALUES ('PayrollImportMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollImportMenu',7,'JMenu','Import','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollImportHours','', 'com.blueseer.pay.ImportHoursLauncherPanel','JMenuItem','payimphrs');
INSERT INTO menu_tree VALUES ('PayrollImportMenu','PayrollImportHours',1,'JMenuItem','Import Hours from CSV','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollImportFullPeriod','', 'com.blueseer.pay.FullPeriodImportLauncherPanel','JMenuItem','payimpfull');
INSERT INTO menu_tree VALUES ('PayrollImportMenu','PayrollImportFullPeriod',2,'JMenuItem','Full Periodic CSV Import','','','', 1, 1);

/* §3.6 Payroll Submission Requests (S-31...S-35). */
INSERT INTO menu_mstr VALUES ('PayrollPsrMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollPsrMenu',8,'JMenu','PSR Submissions','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPsrPrepare','', 'com.blueseer.pay.PsrPrepareLauncherPanel','JMenuItem','paypsrprep');
INSERT INTO menu_tree VALUES ('PayrollPsrMenu','PayrollPsrPrepare',1,'JMenuItem','PSR Prepare and Submit','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPsrControl','', 'com.blueseer.pay.PsrControlPanel','JMenuItem','paypsrctrl');
INSERT INTO menu_tree VALUES ('PayrollPsrMenu','PayrollPsrControl',2,'JMenuItem','PSR Control Panel','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPsrCorrection','', 'com.blueseer.pay.CorrectionWizardLauncherPanel','JMenuItem','paypsrcorr');
INSERT INTO menu_tree VALUES ('PayrollPsrMenu','PayrollPsrCorrection',3,'JMenuItem','Correction PSR Wizard','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollRecheck','', 'com.blueseer.pay.RecheckPanel','JMenuItem','payrecheck');
INSERT INTO menu_tree VALUES ('PayrollPsrMenu','PayrollRecheck',4,'JMenuItem','Recheck Payroll Submissions','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollCheckRevenue','', 'com.blueseer.pay.CheckRevenueRecordPanel','JMenuItem','paychkrev');
INSERT INTO menu_tree VALUES ('PayrollPsrMenu','PayrollCheckRevenue',5,'JMenuItem','Check Revenue Record','','','', 1, 1);

/* §3.7 Distribution & Payment (S-36...S-39). S-37 Emailing Payslips has no
   menu item of its own - per the screen spec it is only ever opened from
   S-36's btEmail, pre-scoped to the current period/selection. */
INSERT INTO menu_mstr VALUES ('PayrollDistributionMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollDistributionMenu',9,'JMenu','Distribution & Payment','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPrintPayslips','', 'com.blueseer.pay.PrintPayslipsPanel','JMenuItem','payprnslip');
INSERT INTO menu_tree VALUES ('PayrollDistributionMenu','PayrollPrintPayslips',1,'JMenuItem','Print/Email Payslips','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollBankFile','', 'com.blueseer.pay.BankFileWizardLauncherPanel','JMenuItem','paybankfile');
INSERT INTO menu_tree VALUES ('PayrollDistributionMenu','PayrollBankFile',2,'JMenuItem','Pay Employees - Bank File','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPayMethodReporting','', 'com.blueseer.pay.PayMethodReportingPanel','JMenuItem','paypmrpt');
INSERT INTO menu_tree VALUES ('PayrollDistributionMenu','PayrollPayMethodReporting',3,'JMenuItem','Pay Method Reporting','','','', 1, 1);

/* §3.8 Reports Hub (S-40...S-47). */
INSERT INTO menu_mstr VALUES ('PayrollReportsMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollReportsMenu',10,'JMenu','Reports','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollAuditTrail','', 'com.blueseer.pay.AuditTrailReportPanel','JMenuItem','payaudit');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollAuditTrail',1,'JMenuItem','Payroll Summary/Audit Trail','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollTaxDetails','', 'com.blueseer.pay.TaxDetailsReportPanel','JMenuItem','paytaxdet');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollTaxDetails',2,'JMenuItem','Tax Details Report','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollRegister','', 'com.blueseer.pay.RegisterOfEmployeesPanel','JMenuItem','payregister');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollRegister',3,'JMenuItem','Register of Employees','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollAddDedReport','', 'com.blueseer.pay.AddDedReportPanel','JMenuItem','payaddded');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollAddDedReport',4,'JMenuItem','Additions/Deductions Report','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPensionReports','', 'com.blueseer.pay.PensionReportsPanel','JMenuItem','paypension');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollPensionReports',5,'JMenuItem','Pension Reports','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollOtherReports','', 'com.blueseer.pay.OtherReportsPanel','JMenuItem','payother');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollOtherReports',6,'JMenuItem','Other Reports','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollYearEndSummary','', 'com.blueseer.pay.YearEndSummaryPanel','JMenuItem','payyrend');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollYearEndSummary',7,'JMenuItem','Year End Summary','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollEmploymentDetailsSummary','', 'com.blueseer.pay.EmploymentDetailsSummaryPanel','JMenuItem','payeds');
INSERT INTO menu_tree VALUES ('PayrollReportsMenu','PayrollEmploymentDetailsSummary',8,'JMenuItem','Employment Details Summary','','','', 1, 1);

/* §3.9 Leavers (S-48...S-52). S-48 Leaver (in current pay run) has no menu
   item of its own - per the screen spec it's just cbLeaving/dcLeaveDate on
   S-18, already covered by Pay Entry. */
INSERT INTO menu_mstr VALUES ('PayrollLeaversMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollLeaversMenu',11,'JMenu','Leavers','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollMidPeriodLeaver','', 'com.blueseer.pay.MidPeriodLeaverPanel','JMenuItem','payleaver');
INSERT INTO menu_tree VALUES ('PayrollLeaversMenu','PayrollMidPeriodLeaver',1,'JMenuItem','Leaver (Mid Pay Period)','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollTerminationLumpSum','', 'com.blueseer.pay.TerminationLumpSumPanel','JMenuItem','paylumpsum');
INSERT INTO menu_tree VALUES ('PayrollLeaversMenu','PayrollTerminationLumpSum',2,'JMenuItem','Termination Lump Sum','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPostCessation','', 'com.blueseer.pay.PostCessationPaymentPanel','JMenuItem','paypostcess');
INSERT INTO menu_tree VALUES ('PayrollLeaversMenu','PayrollPostCessation',3,'JMenuItem','Post-Cessation Payment (Current Year)','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPostCessationOOY','', 'com.blueseer.pay.PostCessationOutOfYearPaymentPanel','JMenuItem','paypostcessooy');
INSERT INTO menu_tree VALUES ('PayrollLeaversMenu','PayrollPostCessationOOY',4,'JMenuItem','Post-Cessation Payment (Out of Year)','','','', 1, 1);

/* §3.10 Benefit in Kind, Sick Pay, Parenting Benefits, Pensions
   (S-53...S-61). S-60 Pension Tracing Number Entry has no menu item of its
   own - per the screen spec it's a small field embedded in S-59's panel. */
INSERT INTO menu_mstr VALUES ('PayrollBikPensionsMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollBikPensionsMenu',12,'JMenu','BIK / Sick Pay & Pensions','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollBikVehicle','', 'com.blueseer.pay.BikVehiclePanel','JMenuItem','paybikveh');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollBikVehicle',1,'JMenuItem','BIK - Cars & Vans','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollBikLoan','', 'com.blueseer.pay.BikLoanPanel','JMenuItem','paybikloan');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollBikLoan',2,'JMenuItem','BIK - Preferential Loans','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollBikBenefit','', 'com.blueseer.pay.BikBenefitPanel','JMenuItem','paybikben');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollBikBenefit',3,'JMenuItem','BIK - Annual/One-Off Benefits','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollSsp','', 'com.blueseer.pay.SspPanel','JMenuItem','paysspsetup');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollSsp',4,'JMenuItem','Statutory Sick Pay','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollIllnessBenefit','', 'com.blueseer.pay.IllnessBenefitPanel','JMenuItem','payillben');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollIllnessBenefit',5,'JMenuItem','Illness Benefit Handling','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollParentingBenefit','', 'com.blueseer.pay.ParentingBenefitPanel','JMenuItem','payparben');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollParentingBenefit',6,'JMenuItem','Parenting Benefits','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPensionDeduction','', 'com.blueseer.pay.PensionDeductionPanel','JMenuItem','paypension2');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollPensionDeduction',7,'JMenuItem','Pension Deduction Setup','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollAutoEnrolment','', 'com.blueseer.pay.AutoEnrolmentPanel','JMenuItem','payautoenrol');
INSERT INTO menu_tree VALUES ('PayrollBikPensionsMenu','PayrollAutoEnrolment',8,'JMenuItem','Auto-Enrolment (MyFuture Fund)','','','', 1, 1);

/* §3.11 Journals, Pay Frequency, Year Transition (S-62...S-65). S-64/S-65
   are modal JDialog wizards, so each gets a thin launcher panel as the
   actual menu target, matching S-38/S-28's established pattern. */
INSERT INTO menu_mstr VALUES ('PayrollJournalsMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollJournalsMenu',13,'JMenu','Journals & Year Transition','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollJournalMapping','', 'com.blueseer.pay.JournalMappingPanel','JMenuItem','payjrnlmap');
INSERT INTO menu_tree VALUES ('PayrollJournalsMenu','PayrollJournalMapping',1,'JMenuItem','Payroll Journal Mapping','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollJournalExport','', 'com.blueseer.pay.JournalExportPanel','JMenuItem','payjrnlexp');
INSERT INTO menu_tree VALUES ('PayrollJournalsMenu','PayrollJournalExport',2,'JMenuItem','Journal Export','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollFrequencyChange','', 'com.blueseer.pay.PayFrequencyChangeLauncherPanel','JMenuItem','payfreqchg');
INSERT INTO menu_tree VALUES ('PayrollJournalsMenu','PayrollFrequencyChange',3,'JMenuItem','Change Pay Frequency','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollStartNewTaxYear','', 'com.blueseer.pay.StartNewTaxYearLauncherPanel','JMenuItem','paynewyear');
INSERT INTO menu_tree VALUES ('PayrollJournalsMenu','PayrollStartNewTaxYear',4,'JMenuItem','Start New Tax Year','','','', 1, 1);

/* §3.12 Revenue Payments / Remittance (S-66...S-70). S-70 reuses S-35's
   CheckRevenueRecordPanel unmodified, per the screen spec's own framing -
   a second menu entry pointing at the same class, not a new file. */
INSERT INTO menu_mstr VALUES ('PayrollRemittanceMenu','', '', 'JMenu', '');
INSERT INTO menu_tree VALUES ('Payroll','PayrollRemittanceMenu',14,'JMenu','Revenue Payments','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollRemittanceOverview','', 'com.blueseer.pay.RemittanceOverviewPanel','JMenuItem','payremitover');
INSERT INTO menu_tree VALUES ('PayrollRemittanceMenu','PayrollRemittanceOverview',1,'JMenuItem','Remittance to Revenue - Overview','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPaymentDueDates','', 'com.blueseer.pay.PaymentDueDatesPanel','JMenuItem','paydueddates');
INSERT INTO menu_tree VALUES ('PayrollRemittanceMenu','PayrollPaymentDueDates',2,'JMenuItem','Payment Due Dates','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollPaymentsRecord','', 'com.blueseer.pay.RevenuePaymentsRecordPanel','JMenuItem','paypmtrecord');
INSERT INTO menu_tree VALUES ('PayrollRemittanceMenu','PayrollPaymentsRecord',3,'JMenuItem','Revenue Payments Record','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollReturnsLookup','', 'com.blueseer.pay.ReturnsLookupPanel','JMenuItem','payretlookup');
INSERT INTO menu_tree VALUES ('PayrollRemittanceMenu','PayrollReturnsLookup',4,'JMenuItem','Returns Look-Up','','','', 1, 1);

INSERT INTO menu_mstr VALUES ('PayrollQueryRevenueRecord','', 'com.blueseer.pay.CheckRevenueRecordPanel','JMenuItem','payqueryrev');
INSERT INTO menu_tree VALUES ('PayrollRemittanceMenu','PayrollQueryRevenueRecord',5,'JMenuItem','Query Revenue Record','','','', 1, 1);
