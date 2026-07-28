package com.blueseer.pay;

import com.blueseer.pay.PaymentDtos.PayMethodSummaryDTO;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.text.DateFormatter;

/** S-39 Paying Employees - Reporting. */
public class PayMethodReportingPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IPaymentController controller;
    private final IPayslipDistributionController distributionController;

    private JFormattedTextField dcPayDateSummary;
    private JFormattedTextField dcPayDateCash;
    private JLabel[] summaryValues;
    private JLabel[] cashValues;

    public PayMethodReportingPanel() {
        this(new InMemoryPaymentController(PayrollStubStore.shared()),
                new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())));
    }

    public PayMethodReportingPanel(IPaymentController controller, IPayslipDistributionController distributionController) {
        this.controller = controller;
        this.distributionController = distributionController;
        initComponents();
        applyMostRecentPayDate();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            applyMostRecentPayDate();
        }
    }

    private LocalDate mostRecentPayDate() {
        List<PeriodDTO> periods = distributionController.getProcessedPeriods(DEMO_COMPANY);
        return periods.isEmpty() ? null : periods.get(periods.size() - 1).payDate();
    }

    private void applyMostRecentPayDate() {
        LocalDate defaultDate = mostRecentPayDate();
        if (defaultDate != null) {
            Date d = Date.from(defaultDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            dcPayDateSummary.setValue(d);
            dcPayDateCash.setValue(d);
            reloadSummary();
            reloadCash();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Paying Employees - Reporting"));
        content.setPreferredSize(new java.awt.Dimension(500, 350));

        JTabbedPane tabs = new JTabbedPane();

        String[] summaryLabels = {"Cash Total", "Cash Count", "Cheque Total", "Cheque Count", "Credit Transfer Total", "Credit Transfer Count"};
        summaryValues = new JLabel[summaryLabels.length];
        JPanel summaryTab = new JPanel(new BorderLayout());
        DateFormatter summaryFormatter = new DateFormatter(DATE_FMT);
        summaryFormatter.setAllowsInvalid(true);
        dcPayDateSummary = new JFormattedTextField(summaryFormatter);
        dcPayDateSummary.setColumns(10);
        dcPayDateSummary.addPropertyChangeListener("value", e -> reloadSummary());
        JPanel summaryNorth = new JPanel();
        summaryNorth.add(new JLabel("Pay Date:"));
        summaryNorth.add(dcPayDateSummary);
        summaryTab.add(summaryNorth, BorderLayout.NORTH);
        JPanel summaryGrid = new JPanel(new GridLayout(summaryLabels.length, 2, 5, 5));
        for (int i = 0; i < summaryLabels.length; i++) {
            summaryGrid.add(new JLabel(summaryLabels[i] + ":"));
            summaryValues[i] = new JLabel("-");
            summaryGrid.add(summaryValues[i]);
        }
        summaryTab.add(summaryGrid, BorderLayout.CENTER);
        JButton btPrintSummary = new JButton("Print");
        btPrintSummary.addActionListener(e -> onPrint("Pay Method Summary"));
        summaryTab.add(btPrintSummary, BorderLayout.SOUTH);
        tabs.addTab("Pay Method Summary", summaryTab);

        String[] cashLabels = {"Cash Total", "Cash Headcount"};
        cashValues = new JLabel[cashLabels.length];
        JPanel cashTab = new JPanel(new BorderLayout());
        DateFormatter cashFormatter = new DateFormatter(DATE_FMT);
        cashFormatter.setAllowsInvalid(true);
        dcPayDateCash = new JFormattedTextField(cashFormatter);
        dcPayDateCash.setColumns(10);
        dcPayDateCash.addPropertyChangeListener("value", e -> reloadCash());
        JPanel cashNorth = new JPanel();
        cashNorth.add(new JLabel("Pay Date:"));
        cashNorth.add(dcPayDateCash);
        cashTab.add(cashNorth, BorderLayout.NORTH);
        JPanel cashGrid = new JPanel(new GridLayout(cashLabels.length, 2, 5, 5));
        for (int i = 0; i < cashLabels.length; i++) {
            cashGrid.add(new JLabel(cashLabels[i] + ":"));
            cashValues[i] = new JLabel("-");
            cashGrid.add(cashValues[i]);
        }
        cashTab.add(cashGrid, BorderLayout.CENTER);
        JButton btPrintCash = new JButton("Print");
        btPrintCash.addActionListener(e -> onPrint("Cash Requirement Summary"));
        cashTab.add(btPrintCash, BorderLayout.SOUTH);
        tabs.addTab("Cash Requirement Summary", cashTab);

        content.add(tabs, BorderLayout.CENTER);
        add(content);
    }

    private LocalDate readDate(JFormattedTextField field) {
        Object value = field.getValue();
        if (!(value instanceof Date date)) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void reloadSummary() {
        LocalDate payDate = readDate(dcPayDateSummary);
        if (payDate == null) {
            return;
        }
        PayMethodSummaryDTO summary = controller.getPayMethodSummary(DEMO_COMPANY, payDate);
        summaryValues[0].setText(summary.cashTotal().toPlainString());
        summaryValues[1].setText(String.valueOf(summary.cashCount()));
        summaryValues[2].setText(summary.chequeTotal().toPlainString());
        summaryValues[3].setText(String.valueOf(summary.chequeCount()));
        summaryValues[4].setText(summary.creditTransferTotal().toPlainString());
        summaryValues[5].setText(String.valueOf(summary.creditTransferCount()));
    }

    private void reloadCash() {
        LocalDate payDate = readDate(dcPayDateCash);
        if (payDate == null) {
            return;
        }
        PayMethodSummaryDTO summary = controller.getPayMethodSummary(DEMO_COMPANY, payDate);
        cashValues[0].setText(summary.cashTotal().toPlainString());
        cashValues[1].setText(String.valueOf(summary.cashCount()));
    }

    private void onPrint(String tabName) {
        JOptionPane.showMessageDialog(this, tabName + " sent to the system print dialog.", "Print", JOptionPane.INFORMATION_MESSAGE);
    }
}
