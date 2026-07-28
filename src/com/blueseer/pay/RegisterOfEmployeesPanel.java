package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.ReportDtos.RegisterFilterDTO;
import com.blueseer.pay.ReportDtos.RegisterRowDTO;
import com.blueseer.pay.ReportDtos.ReportHandle;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** S-42 Register of Employees - a live filterable list, not a run-a-report picker. */
public class RegisterOfEmployeesPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IReportController controller;
    private final IDepartmentController departmentController;

    private JComboBox<DepartmentDTO> cbDepartmentFilter;
    private JCheckBox cbActiveOnly;
    private JCheckBox cbIncludeLeavers;
    private DefaultTableModel tableModel;
    private boolean loading;

    public RegisterOfEmployeesPanel() {
        this(new InMemoryReportController(PayrollStubStore.shared()), new InMemoryDepartmentController(PayrollStubStore.shared()));
    }

    public RegisterOfEmployeesPanel(IReportController controller, IDepartmentController departmentController) {
        this.controller = controller;
        this.departmentController = departmentController;
        initComponents();
        refreshDepartments();
        refreshTable();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshDepartments();
            refreshTable();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Register of Employees"));
        content.setPreferredSize(new java.awt.Dimension(650, 420));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Department:"));
        cbDepartmentFilter = new JComboBox<>();
        cbDepartmentFilter.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.name()));
        cbDepartmentFilter.addActionListener(e -> {
            if (!loading) {
                refreshTable();
            }
        });
        north.add(cbDepartmentFilter);

        cbActiveOnly = new JCheckBox("Active Only", true);
        cbActiveOnly.addActionListener(e -> refreshTable());
        north.add(cbActiveOnly);

        cbIncludeLeavers = new JCheckBox("Include Leavers", false);
        cbIncludeLeavers.addActionListener(e -> refreshTable());
        north.add(cbIncludeLeavers);
        content.add(north, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[] {"Works No.", "Surname", "First Name", "Department", "PPS Number", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPrint = new JButton("Print");
        btPrint.addActionListener(e -> {
            controller.printReport(buildHandle(), false);
            JOptionPane.showMessageDialog(this, "Report sent to the system print dialog.", "Print", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton btCopy = new JButton("Copy");
        btCopy.addActionListener(e -> {
            controller.copyReportToClipboard(buildHandle());
            JOptionPane.showMessageDialog(this, "Report copied to the clipboard.", "Copy", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton btHtml = new JButton("HTML");
        btHtml.addActionListener(e -> {
            File file = controller.exportReportHtml(buildHandle());
            JOptionPane.showMessageDialog(this, "HTML export written to " + file.getAbsolutePath(), "HTML", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton btEmail = new JButton("Email");
        btEmail.addActionListener(e -> {
            EmailSendResult result = controller.emailReport(buildHandle());
            JOptionPane.showMessageDialog(this, result.sentCount() + " sent, " + result.failedCount() + " failed.", "Email", JOptionPane.INFORMATION_MESSAGE);
        });
        south.add(btPrint);
        south.add(btCopy);
        south.add(btHtml);
        south.add(btEmail);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void refreshDepartments() {
        loading = true;
        DepartmentDTO previous = (DepartmentDTO) cbDepartmentFilter.getSelectedItem();
        cbDepartmentFilter.removeAllItems();
        cbDepartmentFilter.addItem(new DepartmentDTO(null, "", "All Departments"));
        for (DepartmentDTO dept : departmentController.listDepartments(DEMO_COMPANY)) {
            cbDepartmentFilter.addItem(dept);
        }
        if (previous != null) {
            cbDepartmentFilter.setSelectedItem(previous);
        }
        loading = false;
    }

    private RegisterFilterDTO currentFilter() {
        DepartmentDTO selected = (DepartmentDTO) cbDepartmentFilter.getSelectedItem();
        return new RegisterFilterDTO(DEMO_COMPANY, selected == null ? null : selected.id(), cbActiveOnly.isSelected(), cbIncludeLeavers.isSelected());
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (RegisterRowDTO row : controller.getEmployeeRegister(currentFilter())) {
            tableModel.addRow(new Object[] {row.worksNumber(), row.surname(), row.firstName(), row.departmentName(), row.ppsNumber(),
                    row.isFormerEmployee() ? "Former" : "Active"});
        }
    }

    private ReportHandle buildHandle() {
        return controller.getEmployeeRegisterReportHandle(currentFilter());
    }
}
