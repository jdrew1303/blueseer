package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.AddDedReportRequestDTO;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * S-43 Additions/Deductions Reports. The screen spec calls for
 * {@code cbAdditionDeductionType} to be "populated from the same
 * description-suggestion source C-19/S-07/S-08 use" - no such shared
 * suggestion source exists anywhere in this codebase (S-07/S-08's
 * description fields are free-text {@code JTextField}s), so this is an
 * editable combo defaulting to "All Types" rather than a fabricated
 * suggestion list.
 */
public class AddDedReportPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;
    private final IPayslipDistributionController distributionController;

    private JComboBox<PeriodDTO> cbPeriodFrom;
    private JComboBox<PeriodDTO> cbPeriodTo;
    private JComboBox<String> cbAdditionDeductionType;
    private ReportRunnerPanel runner;

    public AddDedReportPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public AddDedReportPanel(IReportController controller, IPayslipDistributionController distributionController) {
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
        content.setBorder(BorderFactory.createTitledBorder("Additions/Deductions Report"));
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

        filters.add(new JLabel("Type:"));
        cbAdditionDeductionType = new JComboBox<>(new String[] {"All Types"});
        cbAdditionDeductionType.setEditable(true);
        filters.add(cbAdditionDeductionType);
        content.add(filters, BorderLayout.NORTH);

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
        Object selected = cbAdditionDeductionType.getSelectedItem();
        String type = selected == null ? "All Types" : selected.toString();
        return controller.getAdditionsDeductionsReport(new AddDedReportRequestDTO(DEMO_COMPANY, periodFrom, periodTo, type, sortAlphabetically));
    }
}
