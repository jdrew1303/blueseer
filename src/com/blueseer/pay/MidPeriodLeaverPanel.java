package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.LeaverDtos.OffCycleFinalisationResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DateFormatter;

/** S-49 Leaver (mid pay period) - a standalone single-employee final-payslip screen, off the normal S-18/S-22 cycle. */
public class MidPeriodLeaverPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final ILeaverController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField dcLeaveDate;
    private JTextField tbHourlyRate;
    private JTextField tbStandardHours;
    private JTextField tbBasicPay;
    private JLabel lblGrossPayPreview;
    private DefaultTableModel addDedModel;
    private JButton btFinaliseFinalPayslip;

    public MidPeriodLeaverPanel() {
        this(new InMemoryLeaverController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public MidPeriodLeaverPanel(ILeaverController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployees();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Leaver (Mid Pay Period)"));
        content.setPreferredSize(new java.awt.Dimension(560, 460));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> onEmployeeChanged());

        JLabel lblLeaveDate = new JLabel("Leave Date:");
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        dcLeaveDate = new JFormattedTextField(formatter);
        dcLeaveDate.setValue(new Date());
        dcLeaveDate.setColumns(10);

        JLabel lblHourlyRate = new JLabel("Hourly Rate:");
        tbHourlyRate = new JTextField(10);
        addRecomputeListener(tbHourlyRate);

        JLabel lblStandardHours = new JLabel("Standard Hours:");
        tbStandardHours = new JTextField(10);
        addRecomputeListener(tbStandardHours);

        JLabel lblBasicPay = new JLabel("Basic Pay:");
        tbBasicPay = new JTextField(10);
        addRecomputeListener(tbBasicPay);

        JLabel lblGrossLabel = new JLabel("Gross Pay Preview:");
        lblGrossPayPreview = new JLabel("0.00");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblLeaveDate).addComponent(lblHourlyRate)
                                .addComponent(lblStandardHours).addComponent(lblBasicPay).addComponent(lblGrossLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(dcLeaveDate).addComponent(tbHourlyRate)
                                .addComponent(tbStandardHours).addComponent(tbBasicPay).addComponent(lblGrossPayPreview))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLeaveDate).addComponent(dcLeaveDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblHourlyRate).addComponent(tbHourlyRate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStandardHours).addComponent(tbStandardHours))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblBasicPay).addComponent(tbBasicPay))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblGrossLabel).addComponent(lblGrossPayPreview)));

        content.add(form, BorderLayout.NORTH);

        JPanel addDedPanel = new JPanel(new BorderLayout());
        addDedPanel.add(new JLabel("Additions / Deductions (view-only):"), BorderLayout.NORTH);
        addDedModel = new DefaultTableModel(new Object[] {"Description", "Type", "Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        addDedPanel.add(new JScrollPane(new JTable(addDedModel)), BorderLayout.CENTER);
        content.add(addDedPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btFinaliseFinalPayslip = new JButton("Finalise Final Payslip");
        btFinaliseFinalPayslip.addActionListener(e -> onFinalise());
        south.add(btFinaliseFinalPayslip);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void addRecomputeListener(JTextField field) {
        javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                recomputeGross();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                recomputeGross();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                recomputeGross();
            }
        };
        field.getDocument().addDocumentListener(listener);
    }

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                cbEmployeeSelector.addItem(summary);
            }
        }
        if (previous != null) {
            cbEmployeeSelector.setSelectedItem(previous);
        }
        onEmployeeChanged();
    }

    private void onEmployeeChanged() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        addDedModel.setRowCount(0);
        if (selected == null) {
            return;
        }
        EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(selected.id());
        if (rec == null) {
            return;
        }
        PayEntryDTO draft = controller.loadMidPeriodLeaverEntry(selected.id());
        tbHourlyRate.setText(draft.hourlyRate() == null ? "" : draft.hourlyRate().toPlainString());
        tbStandardHours.setText(draft.standardHours() == null ? "" : draft.standardHours().toPlainString());
        tbBasicPay.setText(draft.basicPay() == null ? "" : draft.basicPay().toPlainString());

        for (AdditionLineDTO a : rec.additions()) {
            addDedModel.addRow(new Object[] {a.description(), "Addition", a.amount() == null ? "" : a.amount().toPlainString()});
        }
        for (DeductionLineDTO d : rec.deductions()) {
            addDedModel.addRow(new Object[] {d.description(), "Deduction", d.amount() == null ? "Auto-calculated" : d.amount().toPlainString()});
        }
        recomputeGross();
    }

    private void recomputeGross() {
        BigDecimal rate = parse(tbHourlyRate);
        BigDecimal hours = parse(tbStandardHours);
        BigDecimal basic = parse(tbBasicPay);
        BigDecimal gross = rate.multiply(hours).add(basic);
        lblGrossPayPreview.setText(gross.toPlainString());
    }

    private void onFinalise() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "This will generate a final payslip outside the normal pay period. Continue?",
                "Finalise Final Payslip", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Object leaveDateValue = dcLeaveDate.getValue();
        java.time.LocalDate leaveDate = leaveDateValue instanceof Date d
                ? d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : java.time.LocalDate.now();

        PayEntryDTO finalPay = new PayEntryDTO(selected.id(), 0, parseOrNull(tbHourlyRate), parseOrNull(tbStandardHours),
                null, null, null, parseOrNull(tbBasicPay), null, 0, true, leaveDate, null);

        btFinaliseFinalPayslip.setEnabled(false);
        SwingWorker<OffCycleFinalisationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected OffCycleFinalisationResult doInBackground() {
                return controller.finaliseMidPeriodLeaver(selected.id(), leaveDate, finalPay);
            }

            @Override
            protected void done() {
                btFinaliseFinalPayslip.setEnabled(true);
                try {
                    OffCycleFinalisationResult result = get();
                    if (result.success()) {
                        openPrintPayslipsFor(result.periodNumber(), selected.id());
                        refreshEmployees();
                    } else {
                        JOptionPane.showMessageDialog(MidPeriodLeaverPanel.this, result.message(),
                                "Finalise Final Payslip", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MidPeriodLeaverPanel.this, "Finalisation failed: " + ex.getMessage(),
                            "Finalise Final Payslip", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Matches the exemplar's own description that the final payslip is
     * "displayed on screen and finalized" and made "available for printing
     * or emailing" immediately, not deferred to the next Process-Icon-7 run.
     */
    private void openPrintPayslipsFor(int periodNumber, EmployeeId employeeId) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        PayrollStubStore store = PayrollStubStore.shared();
        PrintPayslipsPanel panel = new PrintPayslipsPanel(
                new InMemoryPayslipDistributionController(store, new PayslipReportDataProvider(store)),
                new InMemoryEmployeeRepository(store), new InMemoryDepartmentController(store),
                new PayslipReportDataProvider(store));
        panel.preselect(periodNumber, employeeId);

        JDialog dialog = new JDialog(owner, "Print/Email Payslips - Final Payslip", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.setSize(720, 560);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal parseOrNull(JTextField field) {
        try {
            return field.getText().isBlank() ? null : new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
