package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.OtherReportRequestDTO;
import com.blueseer.pay.ReportDtos.OtherReportType;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** S-45 Other Reports - Hours Worked/Overtime, Holiday Pay, Notional Pay, ASC. */
public class OtherReportsPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String[] SUB_TYPE_LABELS = {"Hours Worked/Overtime", "Holiday Pay", "Notional Pay", "ASC"};
    private static final OtherReportType[] SUB_TYPES = {
            OtherReportType.HOURS_OVERTIME, OtherReportType.HOLIDAY_PAY, OtherReportType.NOTIONAL_PAY, OtherReportType.ASC};

    private final IReportController controller;
    private final IPayslipDistributionController distributionController;

    private JComboBox<String> cbReportSubType;
    private JComboBox<PeriodDTO> cbPeriodFrom;
    private JComboBox<PeriodDTO> cbPeriodTo;
    private ReportRunnerPanel runner;

    public OtherReportsPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public OtherReportsPanel(IReportController controller, IPayslipDistributionController distributionController) {
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
        content.setBorder(BorderFactory.createTitledBorder("Other Reports"));
        content.setPreferredSize(new java.awt.Dimension(650, 420));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Report:"));
        cbReportSubType = new JComboBox<>(SUB_TYPE_LABELS);
        cbReportSubType.addActionListener(e -> runner.clearPreview());
        north.add(cbReportSubType);

        north.add(new JLabel("Period From:"));
        cbPeriodFrom = new JComboBox<>();
        cbPeriodFrom.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        north.add(cbPeriodFrom);

        north.add(new JLabel("To:"));
        cbPeriodTo = new JComboBox<>();
        cbPeriodTo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        north.add(cbPeriodTo);
        content.add(north, BorderLayout.NORTH);

        runner = new ReportRunnerPanel(controller, this::runReport);
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
        OtherReportType type = SUB_TYPES[cbReportSubType.getSelectedIndex()];
        return controller.getOtherReport(type, new OtherReportRequestDTO(DEMO_COMPANY, periodFrom, periodTo, sortAlphabetically));
    }
}
