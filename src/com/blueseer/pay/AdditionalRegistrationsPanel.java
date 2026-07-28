package com.blueseer.pay;

import com.blueseer.pay.CompanySetupDtos.PayeRegistrationDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.RegistrationId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** S-02 Additional PAYE Registrations. */
public class AdditionalRegistrationsPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final ICompanySetupController controller;

    private DefaultTableModel model;
    private JTable table;
    private JButton btRemoveRegistration;

    public AdditionalRegistrationsPanel() {
        this(new InMemoryCompanySetupController(PayrollStubStore.shared()));
    }

    public AdditionalRegistrationsPanel(ICompanySetupController controller) {
        this.controller = controller;
        initComponents();
        reload();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            reload();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Additional PAYE Registrations"));
        content.setPreferredSize(new Dimension(520, 320));

        model = new DefaultTableModel(new Object[] {"Registration Number", "Description", "ROS Sub-Cert Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btRemoveRegistration.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btAddRegistration = new JButton("Add Registration");
        btAddRegistration.addActionListener(e -> onAdd());
        btRemoveRegistration = new JButton("Remove Registration");
        btRemoveRegistration.setEnabled(false);
        btRemoveRegistration.addActionListener(e -> onRemove());
        south.add(btAddRegistration);
        south.add(btRemoveRegistration);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void reload() {
        if (model == null) {
            return;
        }
        model.setRowCount(0);
        for (PayeRegistrationDTO row : controller.listRegistrations(DEMO_COMPANY)) {
            model.addRow(new Object[] {row.registrationNumber(), row.description(), row.subCertStatus()});
        }
    }

    private void onAdd() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        AddRegistrationDialog dialog = new AddRegistrationDialog(owner);
        dialog.setVisible(true);
        if (dialog.wasSaved() && !dialog.getRegistrationNumber().isEmpty()) {
            controller.addRegistration(DEMO_COMPANY, dialog.getRegistrationNumber(), dialog.getDescription());
            reload();
        }
    }

    private void onRemove() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove this PAYE registration? Any ROS sub-certificate for it will also be removed.",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        List<PayeRegistrationDTO> current = controller.listRegistrations(DEMO_COMPANY);
        RegistrationId id = current.get(row).id();
        controller.removeRegistration(DEMO_COMPANY, id);
        reload();
    }
}
