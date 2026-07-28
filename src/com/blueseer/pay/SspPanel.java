package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.SspDtos.SspClaimDTO;
import com.blueseer.pay.SspDtos.SspPreviewDTO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
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
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import javax.swing.text.DateFormatter;
import javax.swing.text.NumberFormatter;

/** S-56 Statutory Sick Pay Setup/Operation. */
public class SspPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final ISspController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField dcIllnessStartDate;
    private JFormattedTextField tbDaysClaimedThisInstance;
    private JLabel lblQualifyingServiceMet;
    private JLabel lblDaysRemainingThisYear;
    private JCheckBox cbHasMedicalCertificate;
    private JLabel lblDailyRateBase;
    private JLabel lblSspPayThisInstance;
    private JButton btRecordSickLeave;

    public SspPanel() {
        this(new InMemorySspController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public SspPanel(ISspController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        recompute();
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
        content.setBorder(BorderFactory.createTitledBorder("Statutory Sick Pay"));
        content.setPreferredSize(new Dimension(560, 420));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> recompute());

        JLabel lblStartDate = new JLabel("Illness Start Date:");
        DateFormatter dateFormatter = new DateFormatter(DATE_FMT);
        dateFormatter.setAllowsInvalid(true);
        dcIllnessStartDate = new JFormattedTextField(dateFormatter);
        dcIllnessStartDate.setValue(new Date());
        dcIllnessStartDate.setColumns(10);
        addRecomputeListener(dcIllnessStartDate);

        JLabel lblDaysClaimed = new JLabel("Days Claimed This Instance:");
        NumberFormatter intFormatter = new NumberFormatter(java.text.NumberFormat.getIntegerInstance());
        intFormatter.setAllowsInvalid(true);
        tbDaysClaimedThisInstance = new JFormattedTextField(intFormatter);
        tbDaysClaimedThisInstance.setValue(1);
        tbDaysClaimedThisInstance.setColumns(6);
        addRecomputeListener(tbDaysClaimedThisInstance);

        JLabel lblQualifyingLabel = new JLabel("Qualifying Service Met (13 weeks):");
        lblQualifyingServiceMet = new JLabel("-");

        JLabel lblDaysRemainingLabel = new JLabel("Days Remaining This Year:");
        lblDaysRemainingThisYear = new JLabel("-");

        JLabel lblCert = new JLabel("Medical Certificate Provided:");
        cbHasMedicalCertificate = new JCheckBox();
        cbHasMedicalCertificate.addActionListener(e -> {
            recompute();
            btRecordSickLeave.setEnabled(cbHasMedicalCertificate.isSelected());
        });

        JLabel lblDailyRateLabel = new JLabel("Daily Rate Base:");
        lblDailyRateBase = new JLabel("-");

        JLabel lblSspPayLabel = new JLabel("SSP Pay This Instance:");
        lblSspPayThisInstance = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblStartDate).addComponent(lblDaysClaimed)
                                .addComponent(lblQualifyingLabel).addComponent(lblDaysRemainingLabel).addComponent(lblCert)
                                .addComponent(lblDailyRateLabel).addComponent(lblSspPayLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(dcIllnessStartDate).addComponent(tbDaysClaimedThisInstance)
                                .addComponent(lblQualifyingServiceMet).addComponent(lblDaysRemainingThisYear).addComponent(cbHasMedicalCertificate)
                                .addComponent(lblDailyRateBase).addComponent(lblSspPayThisInstance))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStartDate).addComponent(dcIllnessStartDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDaysClaimed).addComponent(tbDaysClaimedThisInstance))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblQualifyingLabel).addComponent(lblQualifyingServiceMet))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDaysRemainingLabel).addComponent(lblDaysRemainingThisYear))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCert).addComponent(cbHasMedicalCertificate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDailyRateLabel).addComponent(lblDailyRateBase))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSspPayLabel).addComponent(lblSspPayThisInstance)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btRecordSickLeave = new JButton("Record Sick Leave");
        btRecordSickLeave.setEnabled(false);
        btRecordSickLeave.addActionListener(e -> onRecord());
        south.add(btRecordSickLeave);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void addRecomputeListener(JFormattedTextField field) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }
        };
        field.getDocument().addDocumentListener(listener);
    }

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            cbEmployeeSelector.addItem(summary);
        }
        if (previous != null) {
            cbEmployeeSelector.setSelectedItem(previous);
        }
    }

    private SspClaimDTO currentDraft(EmployeeSummaryDTO selected) {
        return new SspClaimDTO(selected.id(), asDate(dcIllnessStartDate), asInt(tbDaysClaimedThisInstance), cbHasMedicalCertificate.isSelected());
    }

    private void recompute() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || dcIllnessStartDate == null) {
            return;
        }
        SspPreviewDTO preview = controller.previewSspEntitlement(currentDraft(selected));
        lblQualifyingServiceMet.setText(preview.qualifyingServiceMet() ? "Yes" : "No");
        lblDaysRemainingThisYear.setText(String.valueOf(preview.daysRemainingThisYear()));
        lblDailyRateBase.setText(preview.dailyRateBase().toPlainString());
        lblSspPayThisInstance.setText(preview.sslPayThisInstance().toPlainString());
    }

    private void onRecord() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        SaveResult result = controller.recordSickLeave(currentDraft(selected));
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? (result.wasCreate() ? "Sick leave recorded." : "Could not record sick leave.") : result.detailMessageOrNull(),
                "Statutory Sick Pay", JOptionPane.INFORMATION_MESSAGE);
        recompute();
    }

    private static java.time.LocalDate asDate(JFormattedTextField field) {
        Object value = field.getValue();
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private static int asInt(JFormattedTextField field) {
        Object value = field.getValue();
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
