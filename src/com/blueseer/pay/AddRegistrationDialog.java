package com.blueseer.pay;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** S-02's "Add Registration" modal - PPS-style 7-digit + 1-2 letter registration number mask, plus a description. */
final class AddRegistrationDialog extends JDialog {

    private static final Pattern REG_NUMBER_PATTERN = Pattern.compile("^\\d{7}[A-Za-z]{1,2}$");

    private JTextField tbRegistrationNumber;
    private JTextField tbDescription;
    private JLabel lblMaskWarning;
    private boolean saved;

    AddRegistrationDialog(Window owner) {
        super(owner, "Add PAYE Registration", ModalityType.APPLICATION_MODAL);
        initComponents();
        setSize(380, 220);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblRegNumber = new JLabel("Registration Number:");
        tbRegistrationNumber = new JTextField(12);
        lblMaskWarning = new JLabel(" ");
        lblMaskWarning.setForeground(Color.RED);
        tbRegistrationNumber.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateMask();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateMask();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateMask();
            }
        });

        JLabel lblDescription = new JLabel("Description:");
        tbDescription = new JTextField(20);

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblRegNumber).addComponent(lblDescription))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbRegistrationNumber).addComponent(tbDescription).addComponent(lblMaskWarning))));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblRegNumber).addComponent(tbRegistrationNumber))
                .addComponent(lblMaskWarning)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDescription).addComponent(tbDescription)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton btOk = new JButton("OK");
        btOk.addActionListener(e -> {
            saved = true;
            dispose();
        });
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());
        south.add(btOk);
        south.add(btCancel);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void validateMask() {
        boolean valid = REG_NUMBER_PATTERN.matcher(tbRegistrationNumber.getText().trim()).matches();
        lblMaskWarning.setText(valid || tbRegistrationNumber.getText().isEmpty() ? " " : "Expected format: 7 digits + 1-2 letters");
    }

    boolean wasSaved() {
        return saved;
    }

    String getRegistrationNumber() {
        return tbRegistrationNumber.getText().trim();
    }

    String getDescription() {
        return tbDescription.getText().trim();
    }
}
