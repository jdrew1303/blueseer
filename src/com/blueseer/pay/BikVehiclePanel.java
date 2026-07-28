package com.blueseer.pay;

import com.blueseer.pay.BikDtos.BikCarDTO;
import com.blueseer.pay.BikDtos.BikPreviewDTO;
import com.blueseer.pay.BikDtos.BikVanDTO;
import com.blueseer.pay.BikDtos.BikVehicleDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import javax.swing.text.DateFormatter;

/** S-53 BIK - Cars &amp; Vans. */
public class BikVehiclePanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IBikController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JRadioButton rbCar;
    private JRadioButton rbVan;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JTextField tbOmvOriginal;
    private JTextField tbCo2Emissions;
    private JLabel lblVehicleCategory;
    private JTextField tbActualBusinessKm;
    private JFormattedTextField dcAvailableFrom;
    private JFormattedTextField dcAvailableTo;
    private JCheckBox cbIsElectricVehicle;
    private JTextField tbAmountMadeGood;
    private JCheckBox cbQualifiesFor20PercentReduction;
    private JLabel lblCashEquivalent;
    private JLabel lblFinalBik;

    private JTextField tbVanOmvOriginal;
    private JFormattedTextField dcVanAvailableFrom;
    private JFormattedTextField dcVanAvailableTo;
    private JTextField tbVanAmountMadeGood;
    private JCheckBox cbQualifiesForLimitedPrivateUseExemption;
    private JLabel lblVanFinalBik;

    private JButton btSaveBikEntry;

    public BikVehiclePanel() {
        this(new InMemoryBikController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public BikVehiclePanel(IBikController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        recomputeCar();
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
        content.setBorder(BorderFactory.createTitledBorder("BIK - Cars & Vans"));
        content.setPreferredSize(new Dimension(600, 520));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        north.add(cbEmployeeSelector);

        rbCar = new JRadioButton("Car", true);
        rbVan = new JRadioButton("Van");
        ButtonGroup group = new ButtonGroup();
        group.add(rbCar);
        group.add(rbVan);
        rbCar.addActionListener(e -> { cardLayout.show(cardPanel, "car"); recomputeCar(); });
        rbVan.addActionListener(e -> { cardLayout.show(cardPanel, "van"); recomputeVan(); });
        north.add(rbCar);
        north.add(rbVan);
        content.add(north, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(buildCarCard(), "car");
        cardPanel.add(buildVanCard(), "van");
        content.add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSaveBikEntry = new JButton("Save BIK Entry");
        btSaveBikEntry.addActionListener(e -> onSave());
        south.add(btSaveBikEntry);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private JPanel buildCarCard() {
        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblOmv = new JLabel("OMV (Original Market Value):");
        tbOmvOriginal = new JTextField(12);
        addRecomputeListener(tbOmvOriginal, this::recomputeCar);

        JLabel lblCo2 = new JLabel("CO2 Emissions (g/km):");
        tbCo2Emissions = new JTextField(12);
        addRecomputeListener(tbCo2Emissions, this::recomputeCar);

        JLabel lblCategoryLabel = new JLabel("Vehicle Category:");
        lblVehicleCategory = new JLabel("-");

        JLabel lblKm = new JLabel("Actual Business Kilometres:");
        tbActualBusinessKm = new JTextField(12);
        addRecomputeListener(tbActualBusinessKm, this::recomputeCar);

        JLabel lblFrom = new JLabel("Available From:");
        dcAvailableFrom = newDateField();
        addRecomputeListener(dcAvailableFrom, this::recomputeCar);

        JLabel lblTo = new JLabel("Available To:");
        dcAvailableTo = newDateField();
        addRecomputeListener(dcAvailableTo, this::recomputeCar);

        JLabel lblElectric = new JLabel("Electric Vehicle:");
        cbIsElectricVehicle = new JCheckBox();
        cbIsElectricVehicle.addActionListener(e -> recomputeCar());

        JLabel lblMadeGood = new JLabel("Amount Made Good by Employee:");
        tbAmountMadeGood = new JTextField(12);
        tbAmountMadeGood.setText("0");
        addRecomputeListener(tbAmountMadeGood, this::recomputeCar);

        JLabel lbl20Pct = new JLabel("Qualifies for 20% Reduction:");
        cbQualifiesFor20PercentReduction = new JCheckBox();
        cbQualifiesFor20PercentReduction.addActionListener(e -> recomputeCar());

        JLabel lblCashEquivalentLabel = new JLabel("Cash Equivalent (this period):");
        lblCashEquivalent = new JLabel("-");
        JLabel lblFinalBikLabel = new JLabel("Final BIK Chargeable:");
        lblFinalBik = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblOmv).addComponent(lblCo2).addComponent(lblCategoryLabel)
                                .addComponent(lblKm).addComponent(lblFrom).addComponent(lblTo)
                                .addComponent(lblElectric).addComponent(lblMadeGood).addComponent(lbl20Pct)
                                .addComponent(lblCashEquivalentLabel).addComponent(lblFinalBikLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbOmvOriginal).addComponent(tbCo2Emissions).addComponent(lblVehicleCategory)
                                .addComponent(tbActualBusinessKm).addComponent(dcAvailableFrom).addComponent(dcAvailableTo)
                                .addComponent(cbIsElectricVehicle).addComponent(tbAmountMadeGood).addComponent(cbQualifiesFor20PercentReduction)
                                .addComponent(lblCashEquivalent).addComponent(lblFinalBik))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblOmv).addComponent(tbOmvOriginal))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCo2).addComponent(tbCo2Emissions))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCategoryLabel).addComponent(lblVehicleCategory))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblKm).addComponent(tbActualBusinessKm))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFrom).addComponent(dcAvailableFrom))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTo).addComponent(dcAvailableTo))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblElectric).addComponent(cbIsElectricVehicle))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblMadeGood).addComponent(tbAmountMadeGood))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lbl20Pct).addComponent(cbQualifiesFor20PercentReduction))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCashEquivalentLabel).addComponent(lblCashEquivalent))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFinalBikLabel).addComponent(lblFinalBik)));

        return form;
    }

    private JPanel buildVanCard() {
        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblOmv = new JLabel("OMV (Original Market Value):");
        tbVanOmvOriginal = new JTextField(12);
        addRecomputeListener(tbVanOmvOriginal, this::recomputeVan);

        JLabel lblFrom = new JLabel("Available From:");
        dcVanAvailableFrom = newDateField();
        addRecomputeListener(dcVanAvailableFrom, this::recomputeVan);

        JLabel lblTo = new JLabel("Available To:");
        dcVanAvailableTo = newDateField();
        addRecomputeListener(dcVanAvailableTo, this::recomputeVan);

        JLabel lblMadeGood = new JLabel("Amount Made Good by Employee:");
        tbVanAmountMadeGood = new JTextField(12);
        tbVanAmountMadeGood.setText("0");
        addRecomputeListener(tbVanAmountMadeGood, this::recomputeVan);

        JLabel lblExemption = new JLabel("Qualifies for Limited Private Use Exemption:");
        cbQualifiesForLimitedPrivateUseExemption = new JCheckBox();
        cbQualifiesForLimitedPrivateUseExemption.addActionListener(e -> recomputeVan());

        JLabel lblFinalBikLabel = new JLabel("Final BIK Chargeable:");
        lblVanFinalBik = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblOmv).addComponent(lblFrom).addComponent(lblTo)
                                .addComponent(lblMadeGood).addComponent(lblExemption).addComponent(lblFinalBikLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(tbVanOmvOriginal).addComponent(dcVanAvailableFrom).addComponent(dcVanAvailableTo)
                                .addComponent(tbVanAmountMadeGood).addComponent(cbQualifiesForLimitedPrivateUseExemption)
                                .addComponent(lblVanFinalBik))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblOmv).addComponent(tbVanOmvOriginal))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFrom).addComponent(dcVanAvailableFrom))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTo).addComponent(dcVanAvailableTo))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblMadeGood).addComponent(tbVanAmountMadeGood))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblExemption).addComponent(cbQualifiesForLimitedPrivateUseExemption))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFinalBikLabel).addComponent(lblVanFinalBik)));

        return form;
    }

    private static JFormattedTextField newDateField() {
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(new Date());
        field.setColumns(10);
        return field;
    }

    private void addRecomputeListener(JTextField field, Runnable recompute) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                recompute.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                recompute.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                recompute.run();
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

    private void recomputeCar() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || tbOmvOriginal == null) {
            return;
        }
        BikCarDTO draft = currentCarDraft(selected);
        BikPreviewDTO preview = controller.previewCarBik(draft);
        lblVehicleCategory.setText(preview.vehicleCategoryOrNull() == null ? "-" : preview.vehicleCategoryOrNull());
        lblCashEquivalent.setText(preview.cashEquivalentForPeriod().toPlainString());
        lblFinalBik.setText(preview.finalBikChargeable().toPlainString());
    }

    private void recomputeVan() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || tbVanOmvOriginal == null) {
            return;
        }
        BikVanDTO draft = currentVanDraft(selected);
        BikPreviewDTO preview = controller.previewVanBik(draft);
        lblVanFinalBik.setText(preview.finalBikChargeable().toPlainString());
    }

    private BikCarDTO currentCarDraft(EmployeeSummaryDTO selected) {
        return new BikCarDTO(selected.id(), parse(tbOmvOriginal), parse(tbCo2Emissions), parse(tbActualBusinessKm),
                asDate(dcAvailableFrom), asDate(dcAvailableTo), cbIsElectricVehicle.isSelected(),
                parse(tbAmountMadeGood), cbQualifiesFor20PercentReduction.isSelected());
    }

    private BikVanDTO currentVanDraft(EmployeeSummaryDTO selected) {
        return new BikVanDTO(selected.id(), parse(tbVanOmvOriginal), asDate(dcVanAvailableFrom), asDate(dcVanAvailableTo),
                parse(tbVanAmountMadeGood), cbQualifiesForLimitedPrivateUseExemption.isSelected());
    }

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        BikVehicleDTO data = rbCar.isSelected()
                ? new BikVehicleDTO(selected.id(), currentCarDraft(selected), null)
                : new BikVehicleDTO(selected.id(), null, currentVanDraft(selected));
        SaveResult result = controller.saveBikVehicleEntry(data);
        JOptionPane.showMessageDialog(this,
                result.wasCreate() ? "BIK entry saved and scheduled as a recurring addition."
                        : (result.detailMessageOrNull() == null ? "Save failed." : result.detailMessageOrNull()),
                "BIK - Cars & Vans", JOptionPane.INFORMATION_MESSAGE);
    }

    private static java.time.LocalDate asDate(JFormattedTextField field) {
        Object value = field.getValue();
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
