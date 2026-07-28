package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** S-46 Year End Summary - whole-tax-year, period-independent. */
public class YearEndSummaryPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;

    private JComboBox<Integer> cbTaxYear;

    public YearEndSummaryPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()));
    }

    public YearEndSummaryPanel(IReportController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Year End Summary"));
        content.setPreferredSize(new java.awt.Dimension(650, 420));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Tax Year:"));
        cbTaxYear = new JComboBox<>();
        for (Integer year : PayrollEngineFactory.registeredTaxYears()) {
            cbTaxYear.addItem(year);
        }
        north.add(cbTaxYear);
        content.add(north, BorderLayout.NORTH);

        // No sort-alphabetically prompt is meaningful here (the aggregator already
        // sorts by employee), but ReportRunnerPanel's shape is still reused for the
        // shared btRun/preview/action-bar behaviour, per S-46's own spec.
        ReportRunnerPanel runner = new ReportRunnerPanel(controller, sortAlphabetically -> {
            Integer year = (Integer) cbTaxYear.getSelectedItem();
            return controller.getYearEndSummary(DEMO_COMPANY, year == null ? 2026 : year);
        });
        content.add(runner, BorderLayout.CENTER);

        add(content);
    }
}
