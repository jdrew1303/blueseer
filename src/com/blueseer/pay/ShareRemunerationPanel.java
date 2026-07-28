package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ShareRemunerationDtos.SettlementType;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingDTO;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingPreviewDTO;

import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
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
import javax.swing.text.DateFormatter;

/**
 * S-26 Share-Based Remuneration, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class ShareRemunerationPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IShareRemunerationController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField dcVestingDate;
    private JFormattedTextField tbNumberOfShares;
    private JFormattedTextField tbMarketValuePerShare;
    private JComboBox<SettlementType> cbSettlementType;
    private JCheckBox cbSharesInEmployingCompanyOrParent;
    private JFormattedTextField dcSettlementDate;
    private JLabel lblTaxableValue;
    private JLabel lblEmployerPrsiExempt;

    public ShareRemunerationPanel() {
        this(new InMemoryShareRemunerationController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public ShareRemunerationPanel(IShareRemunerationController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployeeSelector();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployeeSelector();
        }
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Share-Based Remuneration"));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));

        JLabel lblVestingDate = new JLabel("Vesting Date:");
        dcVestingDate = dateField();

        JLabel lblShares = new JLabel("Number of Shares:");
        tbNumberOfShares = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbNumberOfShares.setColumns(10);

        JLabel lblMarketValue = new JLabel("Market Value/Share:");
        tbMarketValuePerShare = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbMarketValuePerShare.setColumns(10);

        JLabel lblSettlement = new JLabel("Settlement Type:");
        cbSettlementType = new JComboBox<>(SettlementType.values());
        cbSettlementType.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == SettlementType.SHARE_SETTLED ? "Share-Settled" : "Cash-Settled"));

        JLabel lblSharesInCompany = new JLabel("Shares in Employing Co./Parent:");
        cbSharesInEmployingCompanyOrParent = new JCheckBox();

        JLabel lblSettlementDate = new JLabel("Settlement Date (if within 60 days):");
        dcSettlementDate = dateField();

        ActionListener recompute = e -> recomputePreview();
        cbSettlementType.addActionListener(e -> {
            boolean cashSettled = cbSettlementType.getSelectedItem() == SettlementType.CASH_SETTLED;
            cbSharesInEmployingCompanyOrParent.setEnabled(!cashSettled);
            if (cashSettled) {
                cbSharesInEmployingCompanyOrParent.setSelected(false);
            }
            recomputePreview();
        });
        cbSharesInEmployingCompanyOrParent.addActionListener(recompute);
        dcVestingDate.addPropertyChangeListener("value", e -> recomputePreview());
        tbNumberOfShares.addPropertyChangeListener("value", e -> recomputePreview());
        tbMarketValuePerShare.addPropertyChangeListener("value", e -> recomputePreview());

        JLabel lblTaxableValueLabel = new JLabel("Taxable Value:");
        lblTaxableValue = new JLabel("-");
        JLabel lblEmployerPrsiExemptLabel = new JLabel("Employer PRSI Exempt:");
        lblEmployerPrsiExempt = new JLabel("-");

        JButton btSaveVestingEvent = new JButton("Save Vesting Event");
        btSaveVestingEvent.addActionListener(e -> onSaveVestingEvent());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblVestingDate).addComponent(lblShares)
                                .addComponent(lblMarketValue).addComponent(lblSettlement).addComponent(lblSharesInCompany)
                                .addComponent(lblSettlementDate).addComponent(lblTaxableValueLabel).addComponent(lblEmployerPrsiExemptLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(dcVestingDate).addComponent(tbNumberOfShares)
                                .addComponent(tbMarketValuePerShare).addComponent(cbSettlementType).addComponent(cbSharesInEmployingCompanyOrParent)
                                .addComponent(dcSettlementDate).addComponent(lblTaxableValue).addComponent(lblEmployerPrsiExempt)))
                .addComponent(btSaveVestingEvent));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblVestingDate).addComponent(dcVestingDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblShares).addComponent(tbNumberOfShares))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblMarketValue).addComponent(tbMarketValuePerShare))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSettlement).addComponent(cbSettlementType))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSharesInCompany).addComponent(cbSharesInEmployingCompanyOrParent))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSettlementDate).addComponent(dcSettlementDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTaxableValueLabel).addComponent(lblTaxableValue))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployerPrsiExemptLabel).addComponent(lblEmployerPrsiExempt))
                .addComponent(btSaveVestingEvent));

        add(panel);
    }

    private JFormattedTextField dateField() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
        fmt.setLenient(false);
        DateFormatter formatter = new DateFormatter(fmt);
        formatter.setAllowsInvalid(true);
        return new JFormattedTextField(formatter);
    }

    private void refreshEmployeeSelector() {
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                cbEmployeeSelector.addItem(summary);
            }
        }
    }

    private ShareVestingDTO buildDraft() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        return new ShareVestingDTO(
                selected == null ? null : selected.id(),
                asLocalDate(dcVestingDate.getValue()),
                asBigDecimal(tbNumberOfShares.getValue()),
                asBigDecimal(tbMarketValuePerShare.getValue()),
                (SettlementType) cbSettlementType.getSelectedItem(),
                cbSharesInEmployingCompanyOrParent.isSelected(),
                asLocalDate(dcSettlementDate.getValue()));
    }

    private void recomputePreview() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        ShareVestingPreviewDTO preview = controller.previewTaxableValue(buildDraft());
        lblTaxableValue.setText(preview.taxableValue().toPlainString());
        lblEmployerPrsiExempt.setText(preview.employerPrsiExempt() ? "Yes" : "No");
    }

    private void onSaveVestingEvent() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        controller.saveVestingEvent(buildDraft());
        JOptionPane.showMessageDialog(this, "Vesting event saved - the taxable value has been added to "
                + selected.surname() + ", " + selected.firstName() + "'s additions.", "Save Vesting Event", JOptionPane.INFORMATION_MESSAGE);
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

    private static LocalDate asLocalDate(Object value) {
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }
}
