package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.AuditTrailRequestDTO;
import com.blueseer.pay.ReportDtos.DirectorsFilter;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** S-40 Payroll Summary/Audit Trail. */
public class AuditTrailReportPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String[] DIRECTORS_FILTER_LABELS = {"All Employees", "Exclude Directors' Pay", "Directors' Pay Only"};

    private final IReportController controller;
    private final IPayslipDistributionController distributionController;

    private JComboBox<PeriodDTO> cbPeriodFrom;
    private JComboBox<PeriodDTO> cbPeriodTo;
    private JComboBox<String> cbDirectorsFilter;
    private JCheckBox cbSummaryOnly;
    private ReportRunnerPanel runner;

    public AuditTrailReportPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public AuditTrailReportPanel(IReportController controller, IPayslipDistributionController distributionController) {
        this.controller = controller;
        this.distributionController = distributionController;
        initComponents();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshPeriods();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Payroll Summary / Audit Trail"));
        content.setPreferredSize(new java.awt.Dimension(650, 420));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Period From:"));
        cbPeriodFrom = new JComboBox<>();
        cbPeriodFrom.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        filters.add(cbPeriodFrom);

        filters.add(new JLabel("To:"));
        cbPeriodTo = new JComboBox<>();
        cbPeriodTo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        filters.add(cbPeriodTo);

        filters.add(new JLabel("Directors:"));
        cbDirectorsFilter = new JComboBox<>(DIRECTORS_FILTER_LABELS);
        filters.add(cbDirectorsFilter);

        cbSummaryOnly = new JCheckBox("Tick for summary");
        filters.add(cbSummaryOnly);
        content.add(filters, BorderLayout.NORTH);

        runner = new ReportRunnerPanel(controller, this::runReport, cbSummaryOnly::isSelected);
        content.add(runner, BorderLayout.CENTER);

        add(content);
    }

    private void refreshPeriods() {
        PeriodDTO previousFrom = (PeriodDTO) cbPeriodFrom.getSelectedItem();
        PeriodDTO previousTo = (PeriodDTO) cbPeriodTo.getSelectedItem();
        cbPeriodFrom.removeAllItems();
        cbPeriodTo.removeAllItems();
        for (PeriodDTO period : distributionController.getProcessedPeriods(DEMO_COMPANY)) {
            cbPeriodFrom.addItem(period);
            cbPeriodTo.addItem(period);
        }
        if (previousFrom != null) {
            cbPeriodFrom.setSelectedItem(previousFrom);
        }
        if (cbPeriodTo.getItemCount() > 0) {
            cbPeriodTo.setSelectedItem(previousTo != null ? previousTo : cbPeriodTo.getItemAt(cbPeriodTo.getItemCount() - 1));
        }
    }

    private ReportDtos.ReportHandle runReport(boolean sortAlphabetically) {
        PeriodDTO from = (PeriodDTO) cbPeriodFrom.getSelectedItem();
        PeriodDTO to = (PeriodDTO) cbPeriodTo.getSelectedItem();
        int periodFrom = from == null ? 1 : from.periodNumber();
        int periodTo = to == null ? periodFrom : to.periodNumber();
        DirectorsFilter filter = switch (cbDirectorsFilter.getSelectedIndex()) {
            case 1 -> DirectorsFilter.EXCLUDE_DIRECTORS;
            case 2 -> DirectorsFilter.DIRECTORS_ONLY;
            default -> DirectorsFilter.ALL_EMPLOYEES;
        };
        return controller.getAuditTrailReport(new AuditTrailRequestDTO(DEMO_COMPANY, periodFrom, periodTo, filter, cbSummaryOnly.isSelected(), sortAlphabetically));
    }
}
