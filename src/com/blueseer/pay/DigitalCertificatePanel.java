package com.blueseer.pay;

import com.blueseer.pay.CertificateDtos.CertificateErrorType;
import com.blueseer.pay.CertificateDtos.CertificateScope;
import com.blueseer.pay.CertificateDtos.CertificateStatusDTO;
import com.blueseer.pay.CertificateDtos.CertificateValidationResult;
import com.blueseer.pay.CompanySetupDtos.PayeRegistrationDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.RegistrationId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** S-03 Digital Certificate Manager. */
public class DigitalCertificatePanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final ICertificateController certController;
    private final ICompanySetupController companyController;

    private DefaultTableModel subCertModel;
    private JTable tblSubCerts;
    private JButton btSetupSubCert;

    public DigitalCertificatePanel() {
        this(new InMemoryCertificateController(PayrollStubStore.shared()), new InMemoryCompanySetupController(PayrollStubStore.shared()));
    }

    public DigitalCertificatePanel(ICertificateController certController, ICompanySetupController companyController) {
        this.certController = certController;
        this.companyController = companyController;
        initComponents();
        reloadSubCerts();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            reloadSubCerts();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Digital Certificate Manager"));
        content.setPreferredSize(new Dimension(560, 460));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Employer Certificate", buildCertCard(CertificateScope.EMPLOYER));
        tabs.addTab("Agent Certificate", buildCertCard(CertificateScope.AGENT));
        content.add(tabs, BorderLayout.NORTH);

        subCertModel = new DefaultTableModel(new Object[] {"PAYE Registration Number", "Sub-Cert Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblSubCerts = new JTable(subCertModel);
        tblSubCerts.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btSetupSubCert.setEnabled(tblSubCerts.getSelectedRow() >= 0);
            }
        });
        content.add(new JScrollPane(tblSubCerts), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSetupSubCert = new JButton("Setup Sub-Cert");
        btSetupSubCert.setEnabled(false);
        btSetupSubCert.addActionListener(e -> onSetupSubCert());
        south.add(btSetupSubCert);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private JPanel buildCertCard(CertificateScope scope) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel("Certificate File:"));
        JTextField tbCertPath = new JTextField(24);
        tbCertPath.setEditable(false);
        fileRow.add(tbCertPath);
        JButton btBrowse = new JButton("Browse...");
        fileRow.add(btBrowse);
        panel.add(fileRow);

        JPanel passRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passRow.add(new JLabel("Certificate Password:"));
        JPasswordField pfCertPassword = new JPasswordField(16);
        passRow.add(pfCertPassword);
        panel.add(passRow);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblCertStatus = new JLabel();
        statusRow.add(lblCertStatus);
        panel.add(statusRow);

        btBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PKCS12 Certificate (*.p12, *.pfx)", "p12", "pfx"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                tbCertPath.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btSaveCertificate = new JButton("Save Certificate");
        btSaveCertificate.addActionListener(e -> {
            byte[] bytes = readFileOrEmpty(tbCertPath.getText());
            CertificateValidationResult result = certController.saveCertificate(scope, bytes, pfCertPassword.getPassword());
            renderStatus(lblCertStatus, result);
        });
        saveRow.add(btSaveCertificate);
        panel.add(saveRow);

        refreshStatusLabel(scope, lblCertStatus);
        return panel;
    }

    private void refreshStatusLabel(CertificateScope scope, JLabel label) {
        CertificateStatusDTO status = certController.getCertificateStatus(scope);
        if (!status.present()) {
            label.setText("No certificate on file.");
            label.setForeground(Color.GRAY);
        } else if (status.valid()) {
            label.setText("Valid, expires " + status.expiryDateOrNull());
            label.setForeground(new Color(0, 130, 0));
        } else {
            label.setText(errorMessage(status.errorTypeOrNull()));
            label.setForeground(new Color(180, 0, 0));
        }
    }

    private void renderStatus(JLabel label, CertificateValidationResult result) {
        if (result.success()) {
            label.setText("Valid, expires " + result.expiryDateOrNull());
            label.setForeground(new Color(0, 130, 0));
        } else {
            label.setText(errorMessage(result.errorType()));
            label.setForeground(new Color(180, 0, 0));
        }
    }

    private static String errorMessage(CertificateErrorType type) {
        if (type == null) {
            return "Invalid/Expired";
        }
        return switch (type) {
            case INVALID_PASSWORD -> "Error 1008 - Invalid Certificate Password";
            case EXPIRED -> "Error 1011 - Certificate Expired";
            case MALFORMED -> "Error 1014 - Invalid ROS Certificate";
            case SERVICE_UNAVAILABLE -> "Internal Error - Service Unavailable";
            case NONE -> "Valid";
        };
    }

    private void reloadSubCerts() {
        if (subCertModel == null) {
            return;
        }
        subCertModel.setRowCount(0);
        for (PayeRegistrationDTO row : companyController.listRegistrations(DEMO_COMPANY)) {
            subCertModel.addRow(new Object[] {row.registrationNumber(), row.subCertStatus()});
        }
    }

    private void onSetupSubCert() {
        int row = tblSubCerts.getSelectedRow();
        if (row < 0) {
            return;
        }
        List<PayeRegistrationDTO> registrations = companyController.listRegistrations(DEMO_COMPANY);
        RegistrationId regId = registrations.get(row).id();

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Setup Sub-Certificate - " + registrations.get(row).registrationNumber(), JDialog.ModalityType.APPLICATION_MODAL);
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel("Certificate File:"));
        JTextField tbCertPath = new JTextField(20);
        tbCertPath.setEditable(false);
        fileRow.add(tbCertPath);
        JButton btBrowse = new JButton("Browse...");
        btBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PKCS12 Certificate (*.p12, *.pfx)", "p12", "pfx"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                tbCertPath.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        fileRow.add(btBrowse);
        panel.add(fileRow);

        JPanel passRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passRow.add(new JLabel("Certificate Password:"));
        JPasswordField pfCertPassword = new JPasswordField(14);
        passRow.add(pfCertPassword);
        panel.add(passRow);

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btSave = new JButton("Save");
        btSave.addActionListener(e -> {
            byte[] bytes = readFileOrEmpty(tbCertPath.getText());
            CertificateValidationResult result = certController.saveSubCertificate(regId, bytes, pfCertPassword.getPassword());
            JOptionPane.showMessageDialog(dialog,
                    result.success() ? "Sub-certificate saved, expires " + result.expiryDateOrNull() : errorMessage(result.errorType()),
                    "Setup Sub-Certificate", result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            reloadSubCerts();
            dialog.dispose();
        });
        saveRow.add(btSave);
        panel.add(saveRow);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static byte[] readFileOrEmpty(String path) {
        if (path == null || path.isBlank()) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(new File(path).toPath());
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
