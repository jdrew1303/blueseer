package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.PensionReportRequestDTO;
import com.blueseer.pay.ReportDtos.PensionSchemeType;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** S-44 Pension Reports - Normal/CWPS/NECI tabs, each an independent report-picker instance. */
public class PensionReportsPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;
    private final IPayslipDistributionController distributionController;
    private final JComboBox<PeriodDTO>[] periodFromByTab;
    private final JComboBox<PeriodDTO>[] periodToByTab;

    public PensionReportsPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    @SuppressWarnings("unchecked")
    public PensionReportsPanel(IReportController controller, IPayslipDistributionController distributionController) {
        this.controller = controller;
        this.distributionController = distributionController;
        this.periodFromByTab = new JComboBox[PensionSchemeType.values().length];
        this.periodToByTab = new JComboBox[PensionSchemeType.values().length];
        initComponents();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            for (int i = 0; i < PensionSchemeType.values().length; i++) {
                refreshPeriods(periodFromByTab[i], periodToByTab[i]);
            }
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Pension Reports"));
        content.setPreferredSize(new java.awt.Dimension(650, 440));

        JTabbedPane tabs = new JTabbedPane();
        PensionSchemeType[] types = PensionSchemeType.values();
        for (int i = 0; i < types.length; i++) {
            tabs.addTab(tabLabel(types[i]), buildTab(types[i], i));
        }
        content.add(tabs, BorderLayout.CENTER);

        add(content);
    }

    private static String tabLabel(PensionSchemeType type) {
        return switch (type) {
            case NORMAL -> "Normal";
            case CWPS -> "CWPS";
            case NECI -> "NECI";
        };
    }

    private JPanel buildTab(PensionSchemeType type, int index) {
        JPanel tab = new JPanel(new BorderLayout());

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Period From:"));
        JComboBox<PeriodDTO> cbFrom = new JComboBox<>();
        cbFrom.setRenderer((list, value, idx, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        filters.add(cbFrom);

        filters.add(new JLabel("To:"));
        JComboBox<PeriodDTO> cbTo = new JComboBox<>();
        cbTo.setRenderer((list, value, idx, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : "Period " + value.periodNumber()));
        filters.add(cbTo);
        tab.add(filters, BorderLayout.NORTH);

        periodFromByTab[index] = cbFrom;
        periodToByTab[index] = cbTo;

        ReportRunnerPanel runner = new ReportRunnerPanel(controller, sortAlphabetically -> {
            PeriodDTO from = (PeriodDTO) cbFrom.getSelectedItem();
            PeriodDTO to = (PeriodDTO) cbTo.getSelectedItem();
            int periodFrom = from == null ? 1 : from.periodNumber();
            int periodTo = to == null ? periodFrom : to.periodNumber();
            return controller.getPensionReport(type, new PensionReportRequestDTO(DEMO_COMPANY, periodFrom, periodTo, sortAlphabetically));
        });
        tab.add(runner, BorderLayout.CENTER);
        return tab;
    }

    private void refreshPeriods(JComboBox<PeriodDTO> cbFrom, JComboBox<PeriodDTO> cbTo) {
        PeriodDTO previousFrom = (PeriodDTO) cbFrom.getSelectedItem();
        PeriodDTO previousTo = (PeriodDTO) cbTo.getSelectedItem();
        cbFrom.removeAllItems();
        cbTo.removeAllItems();
        for (PeriodDTO period : distributionController.getProcessedPeriods(DEMO_COMPANY)) {
            cbFrom.addItem(period);
            cbTo.addItem(period);
        }
        if (previousFrom != null) {
            cbFrom.setSelectedItem(previousFrom);
        }
        if (cbTo.getItemCount() > 0) {
            cbTo.setSelectedItem(previousTo != null ? previousTo : cbTo.getItemAt(cbTo.getItemCount() - 1));
        }
    }
}
