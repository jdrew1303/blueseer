package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayslipDistributionDtos.PayslipType;
import com.blueseer.pay.PayslipDistributionDtos.PrintPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.PrintResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * S-36 Print/Email Payslips, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class PrintPayslipsPanel extends JPanel {

    private static final String JASPER_DIR = "sf/jasper/";
    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPayslipDistributionController controller;
    private final IEmployeeRepository employeeRepository;
    private final IDepartmentController departmentController;
    private final PayslipReportDataProvider dataProvider;

    private JComboBox<PeriodDTO> cbPeriod;
    private JComboBox<DepartmentDTO> cbDepartmentQuickSelect;
    private DefaultListModel<EmployeeSummaryDTO> employeeListModel;
    private JList<EmployeeSummaryDTO> listEmployees;
    private JSpinner spnCopies;
    private JCheckBox cbIncludeZeroPayment;
    private JComboBox<PayslipType> cbPayslipType;

    public PrintPayslipsPanel() {
        this(new InMemoryPayslipDistributionController(PayrollStubStore.shared(), new PayslipReportDataProvider(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()), new InMemoryDepartmentController(PayrollStubStore.shared()),
                new PayslipReportDataProvider(PayrollStubStore.shared()));
    }

    public PrintPayslipsPanel(IPayslipDistributionController controller, IEmployeeRepository employeeRepository,
            IDepartmentController departmentController, PayslipReportDataProvider dataProvider) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        this.departmentController = departmentController;
        this.dataProvider = dataProvider;
        initComponents();
        refreshPeriods();
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
        content.setBorder(BorderFactory.createTitledBorder("Print/Email Payslips"));
        content.setPreferredSize(new java.awt.Dimension(650, 500));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Period:"));
        cbPeriod = new JComboBox<>();
        cbPeriod.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber() + " (" + value.payDate() + ")"));
        cbPeriod.addActionListener(e -> refreshEmployeeList());
        north.add(cbPeriod);
        content.add(north, BorderLayout.NORTH);

        JPanel west = new JPanel(new BorderLayout());
        JPanel deptPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        deptPanel.add(new JLabel("Department:"));
        cbDepartmentQuickSelect = new JComboBox<>();
        cbDepartmentQuickSelect.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.id() == null ? value.name() : value.code() + " - " + value.name()));
        for (DepartmentDTO dept : departmentController.listDepartments(DEMO_COMPANY)) {
            cbDepartmentQuickSelect.addItem(dept);
        }
        cbDepartmentQuickSelect.addActionListener(e -> onDepartmentQuickSelect());
        deptPanel.add(cbDepartmentQuickSelect);
        west.add(deptPanel, BorderLayout.NORTH);

        employeeListModel = new DefaultListModel<>();
        listEmployees = new JList<>(employeeListModel);
        listEmployees.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        listEmployees.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")");
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        west.add(new JScrollPane(listEmployees), BorderLayout.CENTER);

        JButton btSelectAll = new JButton("Select All");
        btSelectAll.addActionListener(e -> listEmployees.setSelectionInterval(0, employeeListModel.size() - 1));
        west.add(btSelectAll, BorderLayout.SOUTH);
        content.add(west, BorderLayout.WEST);

        JPanel center = new JPanel();
        GroupLayout layout = new GroupLayout(center);
        center.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblCopies = new JLabel("Copies:");
        spnCopies = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        cbIncludeZeroPayment = new JCheckBox("Include zero-payment payslips");
        JLabel lblType = new JLabel("Payslip Type:");
        cbPayslipType = new JComboBox<>(PayslipType.values());
        cbPayslipType.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value == null ? "" : value.label()));

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(lblCopies).addComponent(lblType))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(spnCopies).addComponent(cbPayslipType)))
                .addComponent(cbIncludeZeroPayment));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCopies).addComponent(spnCopies))
                .addComponent(cbIncludeZeroPayment)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblType).addComponent(cbPayslipType)));
        content.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPreview = new JButton("Preview");
        btPreview.addActionListener(e -> onPreview());
        JButton btPrint = new JButton("Print");
        btPrint.addActionListener(e -> onPrint());
        JButton btEmail = new JButton("Email");
        btEmail.addActionListener(e -> onEmail());
        south.add(btPreview);
        south.add(btPrint);
        south.add(btEmail);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    /** Used by S-49's off-cycle leaver flow to open this screen pre-scoped to one just-generated payslip. */
    void preselect(int periodNumber, com.blueseer.pay.PayrollIds.EmployeeId employeeId) {
        for (int i = 0; i < cbPeriod.getItemCount(); i++) {
            if (cbPeriod.getItemAt(i).periodNumber() == periodNumber) {
                cbPeriod.setSelectedIndex(i);
                break;
            }
        }
        for (int i = 0; i < employeeListModel.size(); i++) {
            if (employeeListModel.get(i).id().equals(employeeId)) {
                listEmployees.setSelectionInterval(i, i);
                break;
            }
        }
    }

    private void refreshPeriods() {
        cbPeriod.removeAllItems();
        for (PeriodDTO period : controller.getProcessedPeriods(DEMO_COMPANY)) {
            cbPeriod.addItem(period);
        }
    }

    private void refreshEmployeeList() {
        employeeListModel.clear();
        PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
        if (period == null) {
            return;
        }
        for (EmployeeSummaryDTO summary : controller.getEmployeesForPeriod(DEMO_COMPANY, period.periodNumber())) {
            employeeListModel.addElement(summary);
        }
    }

    private void onDepartmentQuickSelect() {
        DepartmentDTO dept = (DepartmentDTO) cbDepartmentQuickSelect.getSelectedItem();
        if (dept == null || dept.id() == null) {
            return;
        }
        for (int i = 0; i < employeeListModel.size(); i++) {
            EmployeeSummaryDTO summary = employeeListModel.get(i);
            EmployeeRecordDTO rec = employeeRepository.loadEmployeeRecord(summary.id());
            if (rec != null && dept.id().equals(rec.personalDetails().departmentId())) {
                listEmployees.addSelectionInterval(i, i);
            }
        }
    }

    private PrintPayslipsRequestDTO buildRequest() {
        PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
        if (period == null) {
            return null;
        }
        List<EmployeeId> selectedIds = new ArrayList<>();
        for (EmployeeSummaryDTO summary : listEmployees.getSelectedValuesList()) {
            selectedIds.add(summary.id());
        }
        if (selectedIds.isEmpty()) {
            for (int i = 0; i < employeeListModel.size(); i++) {
                selectedIds.add(employeeListModel.get(i).id());
            }
        }
        return new PrintPayslipsRequestDTO(DEMO_COMPANY, period.periodNumber(), selectedIds,
                (Integer) spnCopies.getValue(), cbIncludeZeroPayment.isSelected(), (PayslipType) cbPayslipType.getSelectedItem());
    }

    private void onPrint() {
        PrintPayslipsRequestDTO req = buildRequest();
        if (req == null) {
            return;
        }
        PrintResult result = controller.printPayslips(req);
        JOptionPane.showMessageDialog(this, result.detailMessageOrNull(), "Print Payslips",
                result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void onEmail() {
        PrintPayslipsRequestDTO req = buildRequest();
        if (req == null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        PayrollStubStore store = PayrollStubStore.shared();
        IPayslipDistributionController emailController = new InMemoryPayslipDistributionController(store, new PayslipReportDataProvider(store));
        new EmailPayslipsDialog(owner, emailController, new InMemoryEmployeeRepository(store), req).setVisible(true);
    }

    private void onPreview() {
        PrintPayslipsRequestDTO req = buildRequest();
        if (req == null) {
            return;
        }
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("periodNumber", req.periodNumber());
            filters.put("employeeIds", new LinkedHashSet<>(req.employeeIds()));
            filters.put("includeZeroPayment", req.includeZeroPayment());
            List<PayslipReportDTO> beans = dataProvider.fetchReportData(
                    new IReportDataProvider.ReportCriteria(req.companyId(), null, null, filters));
            if (beans.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No payslips matched the selection.", "Preview", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JasperReport report = JasperCompileManager.compileReport(JASPER_DIR + req.type().templateName() + ".jrxml");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JRBeanCollectionDataSource(beans));
            Window owner = SwingUtilities.getWindowAncestor(this);
            new PayslipPreviewDialog(owner, print).setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Preview failed: " + e.getMessage(), "Preview", JOptionPane.ERROR_MESSAGE);
        }
    }
}
