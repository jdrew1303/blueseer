package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DateFormatter;

/**
 * S-18 Weekly/Monthly/Fortnightly Input, per
 * docs/architecture/irish-payroll-2026-screen-specs.md. Single-employee pay
 * entry - the additions/deductions summary is a read-only echo of S-07/S-08's
 * standing configuration (edited there, not here).
 */
public class PayEntryPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPayEntryController payEntryController;
    private final IEmployeeRepository employeeRepository;
    private final IDepartmentController departmentController;
    private final IPayrollCalendarController calendarController;

    private Week53Banner week53Banner;
    private JComboBox<DepartmentDTO> cbDepartmentFilter;
    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField tbHourlyRate;
    private JFormattedTextField tbStandardHours;
    private JFormattedTextField tbTimeAndAThird;
    private JFormattedTextField tbTimeAndAHalf;
    private JFormattedTextField tbDoubleTime;
    private JLabel lblBasicPay;
    private JFormattedTextField tbBasicPay;
    private JFormattedTextField tbHolidayPayAmount;
    private JFormattedTextField tbAdditionalWeeksSpread;
    private JLabel lblHolidaySpreadWarning;
    private JCheckBox cbLeaving;
    private JFormattedTextField dcLeaveDate;
    private JTextArea taNote;
    private JLabel lblGrossPreview;
    private JTable tblAdditionsDeductions;

    private int currentPeriodNumber = 1;
    private boolean loading;

    public PayEntryPanel() {
        this(sharedControllers());
    }

    private PayEntryPanel(Object[] deps) {
        this((IPayEntryController) deps[0], (IEmployeeRepository) deps[1], (IDepartmentController) deps[2],
                (IPayrollCalendarController) deps[3]);
    }

    private static Object[] sharedControllers() {
        PayrollStubStore store = PayrollStubStore.shared();
        IPayrollCalculationService calcService = PayrollEngineFactory.defaultCalculationService();
        return new Object[] {
                new InMemoryPayEntryController(store, calcService),
                new InMemoryEmployeeRepository(store),
                new InMemoryDepartmentController(store),
                new InMemoryPayrollCalendarController(store)
        };
    }

    public PayEntryPanel(IPayEntryController payEntryController, IEmployeeRepository employeeRepository,
            IDepartmentController departmentController, IPayrollCalendarController calendarController) {
        this.payEntryController = payEntryController;
        this.employeeRepository = employeeRepository;
        this.departmentController = departmentController;
        this.calendarController = calendarController;
        initComponents();
        refreshAll();
    }

    /**
     * Re-reads the current period and reloads the employee list - called on
     * construction and every time MainFrame re-shows this cached panel
     * ({@link #setVisible}), since S-22's finalisation can have advanced the
     * period since this panel was last on screen.
     */
    private void refreshAll() {
        currentPeriodNumber = payEntryController.getCurrentPeriodNumber(DEMO_COMPANY);
        week53Banner.setWeek53(calendarController.isWeek53Period(DEMO_COMPANY, currentPeriodNumber));
        refreshDepartmentFilter();
        refreshEmployeeSelector();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshAll();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Pay Entry"));
        content.setPreferredSize(new java.awt.Dimension(700, 650));

        week53Banner = new Week53Banner();

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Department:"));
        cbDepartmentFilter = new JComboBox<>();
        cbDepartmentFilter.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.id() == null ? value.name() : value.code() + " - " + value.name()));
        cbDepartmentFilter.addActionListener(e -> refreshEmployeeSelector());
        north.add(cbDepartmentFilter);
        north.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> onEmployeeSelected());
        north.add(cbEmployeeSelector);

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(week53Banner, BorderLayout.NORTH);
        northWrapper.add(north, BorderLayout.SOUTH);
        content.add(northWrapper, BorderLayout.NORTH);

        JPanel center = new JPanel();
        GroupLayout layout = new GroupLayout(center);
        center.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblHourlyRate = new JLabel("Hourly Rate:");
        tbHourlyRate = currencyField();
        JLabel lblStandardHours = new JLabel("Standard Hours:");
        tbStandardHours = numericField();
        JLabel lblTimeAndAThird = new JLabel("Time-and-a-Third Hours:");
        tbTimeAndAThird = numericField();
        JLabel lblTimeAndAHalf = new JLabel("Time-and-a-Half Hours:");
        tbTimeAndAHalf = numericField();
        JLabel lblDoubleTime = new JLabel("Double-Time Hours:");
        tbDoubleTime = numericField();
        lblBasicPay = new JLabel("Weekly Basic:");
        tbBasicPay = currencyField();
        JLabel lblHolidayPay = new JLabel("Holiday Pay Amount:");
        tbHolidayPayAmount = currencyField();
        JLabel lblAdditionalWeeks = new JLabel("Additional Weeks Spread:");
        tbAdditionalWeeksSpread = numericField();
        lblHolidaySpreadWarning = new JLabel(" ");

        cbLeaving = new JCheckBox("Employee is leaving");
        cbLeaving.addActionListener(e -> onLeavingToggled());
        JLabel lblLeaveDate = new JLabel("Leave Date:");
        dcLeaveDate = dateField();
        dcLeaveDate.setEnabled(false);

        JLabel lblNote = new JLabel("Note:");
        taNote = new JTextArea(3, 30);
        JScrollPane notePane = new JScrollPane(taNote);

        JLabel lblAdditionsDeductions = new JLabel("Additions / Deductions (view-only - edit via Payroll > Employee Maintenance):");
        tblAdditionsDeductions = new JTable(new DefaultTableModel(new Object[] { "Description", "Type", "Amount" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        JScrollPane addDedPane = new JScrollPane(tblAdditionsDeductions);
        addDedPane.setPreferredSize(new java.awt.Dimension(400, 100));

        JLabel lblGross = new JLabel("Gross Pay Preview:");
        lblGrossPreview = new JLabel("0.00");
        lblGrossPreview.setFont(lblGrossPreview.getFont().deriveFont(java.awt.Font.BOLD));

        FocusAdapter recomputeOnBlur = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                recomputeGrossPreview();
            }
        };
        tbHourlyRate.addFocusListener(recomputeOnBlur);
        tbStandardHours.addFocusListener(recomputeOnBlur);
        tbTimeAndAThird.addFocusListener(recomputeOnBlur);
        tbTimeAndAHalf.addFocusListener(recomputeOnBlur);
        tbDoubleTime.addFocusListener(recomputeOnBlur);
        tbBasicPay.addFocusListener(recomputeOnBlur);
        tbHolidayPayAmount.addFocusListener(recomputeOnBlur);
        tbAdditionalWeeksSpread.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                recomputeHolidaySpreadWarning();
            }
        });

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblHourlyRate).addComponent(lblStandardHours).addComponent(lblTimeAndAThird)
                                .addComponent(lblTimeAndAHalf).addComponent(lblDoubleTime).addComponent(lblBasicPay)
                                .addComponent(lblHolidayPay).addComponent(lblAdditionalWeeks).addComponent(lblLeaveDate)
                                .addComponent(lblNote).addComponent(lblGross))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbHourlyRate).addComponent(tbStandardHours).addComponent(tbTimeAndAThird)
                                .addComponent(tbTimeAndAHalf).addComponent(tbDoubleTime).addComponent(tbBasicPay)
                                .addComponent(tbHolidayPayAmount).addComponent(tbAdditionalWeeksSpread)
                                .addComponent(dcLeaveDate).addComponent(notePane).addComponent(lblGrossPreview)))
                .addComponent(lblHolidaySpreadWarning)
                .addComponent(cbLeaving)
                .addComponent(lblAdditionsDeductions)
                .addComponent(addDedPane));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblHourlyRate).addComponent(tbHourlyRate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStandardHours).addComponent(tbStandardHours))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTimeAndAThird).addComponent(tbTimeAndAThird))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTimeAndAHalf).addComponent(tbTimeAndAHalf))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDoubleTime).addComponent(tbDoubleTime))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblBasicPay).addComponent(tbBasicPay))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblHolidayPay).addComponent(tbHolidayPayAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAdditionalWeeks).addComponent(tbAdditionalWeeksSpread))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(lblHolidaySpreadWarning)
                .addComponent(cbLeaving)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLeaveDate).addComponent(dcLeaveDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblNote).addComponent(notePane))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblGross).addComponent(lblGrossPreview))
                .addPreferredGap(ComponentPlacement.UNRELATED)
                .addComponent(lblAdditionsDeductions)
                .addComponent(addDedPane));

        content.add(new JScrollPane(center), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPrevious = new JButton("Previous");
        btPrevious.addActionListener(e -> navigate(-1));
        JButton btNext = new JButton("Next");
        btNext.addActionListener(e -> navigate(1));
        JButton btUpdateFile = new JButton("Update File");
        btUpdateFile.addActionListener(e -> onUpdateFile());
        south.add(btPrevious);
        south.add(btNext);
        south.add(btUpdateFile);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private JFormattedTextField currencyField() {
        JFormattedTextField field = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        field.setColumns(10);
        return field;
    }

    private JFormattedTextField numericField() {
        JFormattedTextField field = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        field.setColumns(6);
        return field;
    }

    private JFormattedTextField dateField() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
        fmt.setLenient(false);
        return new JFormattedTextField(new DateFormatter(fmt));
    }

    private void refreshDepartmentFilter() {
        loading = true;
        cbDepartmentFilter.removeAllItems();
        cbDepartmentFilter.addItem(new DepartmentDTO(null, "", "All Departments"));
        for (DepartmentDTO dept : departmentController.listDepartments(DEMO_COMPANY)) {
            cbDepartmentFilter.addItem(dept);
        }
        loading = false;
    }

    private void refreshEmployeeSelector() {
        if (loading) {
            return;
        }
        loading = true;
        cbEmployeeSelector.removeAllItems();
        DepartmentDTO filter = (DepartmentDTO) cbDepartmentFilter.getSelectedItem();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (summary.isFormerEmployee()) {
                continue;
            }
            EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(summary.id());
            if (filter != null && filter.id() != null && !filter.id().equals(rec.personalDetails().departmentId())) {
                continue;
            }
            cbEmployeeSelector.addItem(summary);
        }
        loading = false;
        onEmployeeSelected();
    }

    private void onEmployeeSelected() {
        if (loading) {
            return;
        }
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        loading = true;
        PayEntryDTO draft = payEntryController.loadPayEntry(selected.id(), currentPeriodNumber);
        boolean monthly = employeeRepository.loadEmployeeRecord(selected.id()).personalDetails().fixedPay() != null;
        lblBasicPay.setText(monthly ? "Monthly Basic:" : "Weekly Basic:");
        tbHourlyRate.setValue(draft.hourlyRate());
        tbStandardHours.setValue(draft.standardHours());
        tbTimeAndAThird.setValue(draft.timeAndAThirdHours());
        tbTimeAndAHalf.setValue(draft.timeAndAHalfHours());
        tbDoubleTime.setValue(draft.doubleTimeHours());
        tbBasicPay.setValue(draft.basicPay());
        tbHolidayPayAmount.setValue(draft.holidayPayAmount());
        tbAdditionalWeeksSpread.setValue(draft.additionalWeeksSpread());
        cbLeaving.setSelected(draft.leaving());
        dcLeaveDate.setEnabled(draft.leaving());
        dcLeaveDate.setValue(draft.leaveDateOrNull() == null ? null : Date.from(draft.leaveDateOrNull().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        taNote.setText(draft.noteOrNull() == null ? "" : draft.noteOrNull());
        refreshAdditionsDeductionsSummary(selected.id());
        loading = false;
        recomputeGrossPreview();
        recomputeHolidaySpreadWarning();
    }

    private void refreshAdditionsDeductionsSummary(EmployeeId id) {
        DefaultTableModel model = (DefaultTableModel) tblAdditionsDeductions.getModel();
        model.setRowCount(0);
        EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(id);
        if (rec == null) {
            return;
        }
        for (AdditionLineDTO a : rec.additions()) {
            model.addRow(new Object[] { a.description(), a.taxable() ? "Taxable" : "Non-Taxable",
                    a.amount() == null ? "" : a.amount().toPlainString() });
        }
        for (DeductionLineDTO d : rec.deductions()) {
            model.addRow(new Object[] { d.description(), d.preTax() ? "Pre-Tax" : "Post-Tax",
                    d.amount() == null ? "Auto-calculated" : d.amount().toPlainString() });
        }
    }

    private void onLeavingToggled() {
        if (cbLeaving.isSelected()) {
            int confirm = JOptionPane.showConfirmDialog(this, "Is this the employee's final pay period?",
                    "Confirm Leaver", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                cbLeaving.setSelected(false);
                dcLeaveDate.setEnabled(false);
                return;
            }
            dcLeaveDate.setEnabled(true);
        } else {
            dcLeaveDate.setEnabled(false);
        }
    }

    private void recomputeGrossPreview() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        BigDecimal gross = payEntryController.previewGrossPay(buildDraft(selected.id()));
        lblGrossPreview.setText(gross.toPlainString());
    }

    private void recomputeHolidaySpreadWarning() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        int weeks = asInt(tbAdditionalWeeksSpread.getValue());
        if (weeks <= 0) {
            lblHolidaySpreadWarning.setText(" ");
            return;
        }
        List<Integer> locked = payEntryController.previewHolidaySpreadLockedWeeks(selected.id(), weeks);
        StringBuilder sb = new StringBuilder("Periods ");
        for (int i = 0; i < locked.size(); i++) {
            sb.append(locked.get(i));
            if (i < locked.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(" will be locked.");
        lblHolidaySpreadWarning.setText(sb.toString());
    }

    private PayEntryDTO buildDraft(EmployeeId id) {
        LocalDate leaveDate = null;
        Object leaveDateValue = dcLeaveDate.getValue();
        if (leaveDateValue instanceof Date d) {
            leaveDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return new PayEntryDTO(id, currentPeriodNumber,
                asBigDecimal(tbHourlyRate.getValue()), asBigDecimal(tbStandardHours.getValue()),
                asBigDecimal(tbTimeAndAThird.getValue()), asBigDecimal(tbTimeAndAHalf.getValue()),
                asBigDecimal(tbDoubleTime.getValue()), asBigDecimal(tbBasicPay.getValue()),
                asBigDecimal(tbHolidayPayAmount.getValue()), asInt(tbAdditionalWeeksSpread.getValue()),
                cbLeaving.isSelected(), leaveDate, taNote.getText().isBlank() ? null : taNote.getText());
    }

    private void onUpdateFile() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        payEntryController.savePayEntry(buildDraft(selected.id()));
        JOptionPane.showMessageDialog(this, "Pay entry updated.", "Update File", JOptionPane.INFORMATION_MESSAGE);
        navigate(1);
    }

    private void navigate(int direction) {
        int index = cbEmployeeSelector.getSelectedIndex();
        int next = index + direction;
        if (next >= 0 && next < cbEmployeeSelector.getItemCount()) {
            cbEmployeeSelector.setSelectedIndex(next);
        }
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
