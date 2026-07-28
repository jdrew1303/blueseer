package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * S-09 Departments Maintenance, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Opened either standalone or in-context from S-05's department combo; the
 * {@code onClose} callback lets the caller refresh its own combo model on
 * close, per the spec's explicit "so a department added mid-entry appears
 * immediately" requirement.
 */
public class DepartmentMaintDialog extends JDialog {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IDepartmentController controller;
    private final Runnable onClose;

    private final DefaultListModel<DepartmentDTO> listModel = new DefaultListModel<>();
    private JList<DepartmentDTO> listDepartments;
    private JTextField tbDeptCode;
    private JTextField tbDeptName;
    private JButton btDeleteDept;

    public DepartmentMaintDialog(java.awt.Frame owner, IDepartmentController controller, Runnable onClose) {
        super(owner, "Departments Maintenance", true);
        this.controller = controller;
        this.onClose = onClose;
        initComponents();
        refreshList();
        setSize(420, 320);
        setLocation(40, 60);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        listDepartments = new JList<>(listModel);
        listDepartments.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listDepartments.setCellRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value.code() + " - " + value.name()));
        listDepartments.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });
        add(new JScrollPane(listDepartments), BorderLayout.CENTER);

        JPanel east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.add(new JLabel("Code"));
        tbDeptCode = new JTextField(10);
        tbDeptCode.setMaximumSize(tbDeptCode.getPreferredSize());
        east.add(tbDeptCode);
        east.add(new JLabel("Name"));
        tbDeptName = new JTextField(15);
        tbDeptName.setMaximumSize(tbDeptName.getPreferredSize());
        east.add(tbDeptName);

        JButton btSaveDept = new JButton("Save");
        btSaveDept.addActionListener(e -> onSave());
        east.add(btSaveDept);

        btDeleteDept = new JButton("Delete");
        btDeleteDept.setEnabled(false);
        btDeleteDept.addActionListener(e -> onDelete());
        east.add(btDeleteDept);

        add(east, BorderLayout.EAST);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> {
            dispose();
            if (onClose != null) {
                onClose.run();
            }
        });
        south.add(btClose);
        add(south, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (onClose != null) {
                    onClose.run();
                }
            }
        });
    }

    private void refreshList() {
        listModel.clear();
        for (DepartmentDTO dept : controller.listDepartments(DEMO_COMPANY)) {
            listModel.addElement(dept);
        }
    }

    private void onSelectionChanged() {
        DepartmentDTO selected = listDepartments.getSelectedValue();
        if (selected != null) {
            tbDeptCode.setText(selected.code());
            tbDeptName.setText(selected.name());
            btDeleteDept.setEnabled(controller.canDeleteDepartment(selected.id()));
        } else {
            btDeleteDept.setEnabled(false);
        }
    }

    private void onSave() {
        DepartmentDTO selected = listDepartments.getSelectedValue();
        DepartmentDTO toSave = new DepartmentDTO(selected != null ? selected.id() : null, tbDeptCode.getText(), tbDeptName.getText());
        controller.saveDepartment(toSave);
        refreshList();
    }

    private void onDelete() {
        DepartmentDTO selected = listDepartments.getSelectedValue();
        if (selected == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Delete department '" + selected.name() + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            controller.deleteDepartment(selected.id());
            tbDeptCode.setText("");
            tbDeptName.setText("");
            refreshList();
        }
    }
}
