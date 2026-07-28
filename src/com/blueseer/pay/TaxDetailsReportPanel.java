package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.TaxDetailsBasis;
import com.blueseer.pay.ReportDtos.TaxDetailsRequestDTO;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** S-41 Tax Details Report (Monthly/Quarterly). */
public class TaxDetailsReportPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;
    private final IPayslipDistributionController distributionController;

    private JComboBox<PeriodDTO> cbPeriodFrom;
    private JComboBox<PeriodDTO> cbPeriodTo;
    private JComboBox<String> cbBasis;
    private ReportRunnerPanel runner;

    public TaxDetailsReportPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public TaxDetailsReportPanel(IReportController controller, IPayslipDistributionController distributionController) {
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
        content.setBorder(BorderFactory.createTitledBorder("Tax Details Report"));
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

        filters.add(new JLabel("Basis:"));
        cbBasis = new JComboBox<>(new String[] {"Monthly", "Quarterly"});
        filters.add(cbBasis);
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
        TaxDetailsBasis basis = cbBasis.getSelectedIndex() == 1 ? TaxDetailsBasis.QUARTERLY : TaxDetailsBasis.MONTHLY;
        return controller.getTaxDetailsReport(new TaxDetailsRequestDTO(DEMO_COMPANY, periodFrom, periodTo, basis, sortAlphabetically));
    }
}
