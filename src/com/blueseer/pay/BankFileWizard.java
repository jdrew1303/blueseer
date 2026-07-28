package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PaymentDtos.BankAccountDTO;
import com.blueseer.pay.PaymentDtos.BankFileRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.text.DateFormatter;

/** S-38 Paying Employees using a Bank Payment File (SEPA). */
public class BankFileWizard extends JDialog {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");
    private static final String CARD_SOURCE = "source";
    private static final String CARD_EMPLOYEES = "employees";
    private static final String CARD_FORMAT = "format";
    private static final String CARD_CONFIRM = "confirm";
    private static final String[] FILE_FORMATS = {"Standard SEPA (pain.001)", "Non-Irish IBAN", "Bankline", "Modulr"};

    private final IPaymentController controller;
    private final IPayslipDistributionController distributionController;
    private final CompanyId companyId;

    private CardLayout cardLayout;
    private JPanel cards;
    private String currentCard = CARD_SOURCE;
    private JButton btBack;
    private JButton btNext;
    private JButton btGenerateFile;

    private JComboBox<PeriodDTO> cbPeriod;
    private JComboBox<BankAccountDTO> cbSourceAccount;
    private JFormattedTextField dcPaymentDate;
    private DefaultListModel<EmployeeSummaryDTO> employeeListModel;
    private JList<EmployeeSummaryDTO> listEmployees;
    private JComboBox<String> cbFileFormat;
    private JTextArea summaryArea;

    public BankFileWizard(Frame owner, IPaymentController controller, IPayslipDistributionController distributionController, CompanyId companyId) {
        super(owner, "Pay Employees - Bank Payment File", true);
        this.controller = controller;
        this.distributionController = distributionController;
        this.companyId = companyId;
        initComponents();
        setSize(560, 420);
        setLocation(60, 60);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.add(buildSourceCard(), CARD_SOURCE);
        cards.add(buildEmployeesCard(), CARD_EMPLOYEES);
        cards.add(buildFormatCard(), CARD_FORMAT);
        cards.add(buildConfirmCard(), CARD_CONFIRM);
        add(cards, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btBack = new JButton("Back");
        btBack.setEnabled(false);
        btBack.addActionListener(e -> onBack());
        btNext = new JButton("Next");
        btNext.addActionListener(e -> onNext());
        btGenerateFile = new JButton("Generate File");
        btGenerateFile.setVisible(false);
        btGenerateFile.addActionListener(e -> onGenerateFile());
        nav.add(btBack);
        nav.add(btNext);
        nav.add(btGenerateFile);
        add(nav, BorderLayout.SOUTH);
    }

    private JPanel buildSourceCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Period:"));
        cbPeriod = new JComboBox<>();
        cbPeriod.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : "Period " + value.periodNumber() + " (" + value.payDate() + ")"));
        for (PeriodDTO period : distributionController.getProcessedPeriods(companyId)) {
            cbPeriod.addItem(period);
        }
        panel.add(cbPeriod);

        panel.add(new JLabel("Source Account:"));
        cbSourceAccount = new JComboBox<>();
        for (BankAccountDTO account : controller.getSourceAccounts(companyId)) {
            cbSourceAccount.addItem(account);
        }
        panel.add(cbSourceAccount);

        panel.add(new JLabel("Payment Date:"));
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        dcPaymentDate = new JFormattedTextField(formatter);
        dcPaymentDate.setColumns(10);
        panel.add(dcPaymentDate);

        cbPeriod.addActionListener(e -> onPeriodChanged());
        onPeriodChanged();
        return panel;
    }

    private void onPeriodChanged() {
        PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
        if (period != null) {
            dcPaymentDate.setValue(Date.from(period.payDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
    }

    private JPanel buildEmployeesCard() {
        JPanel panel = new JPanel(new BorderLayout());
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
        panel.add(new JScrollPane(listEmployees), BorderLayout.CENTER);
        JButton btSelectAll = new JButton("Select All");
        btSelectAll.addActionListener(e -> listEmployees.setSelectionInterval(0, employeeListModel.size() - 1));
        panel.add(btSelectAll, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFormatCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("File Format:"));
        cbFileFormat = new JComboBox<>(FILE_FORMATS);
        panel.add(cbFileFormat);
        return panel;
    }

    private JPanel buildConfirmCard() {
        JPanel panel = new JPanel(new BorderLayout());
        summaryArea = new JTextArea(8, 40);
        summaryArea.setEditable(false);
        panel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        return panel;
    }

    private void onNext() {
        if (CARD_SOURCE.equals(currentCard)) {
            PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
            if (period == null || cbSourceAccount.getSelectedItem() == null) {
                JOptionPane.showMessageDialog(this, "Select a period and source account first.", "Bank Payment File", JOptionPane.WARNING_MESSAGE);
                return;
            }
            employeeListModel.clear();
            for (EmployeeSummaryDTO summary : controller.getCreditTransferEmployees(companyId, period.periodNumber())) {
                employeeListModel.addElement(summary);
            }
            cardLayout.show(cards, CARD_EMPLOYEES);
            currentCard = CARD_EMPLOYEES;
            btBack.setEnabled(true);
        } else if (CARD_EMPLOYEES.equals(currentCard)) {
            if (listEmployees.getSelectedValuesList().isEmpty() && !employeeListModel.isEmpty()) {
                listEmployees.setSelectionInterval(0, employeeListModel.size() - 1);
            }
            cardLayout.show(cards, CARD_FORMAT);
            currentCard = CARD_FORMAT;
        } else if (CARD_FORMAT.equals(currentCard)) {
            PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
            BankAccountDTO account = (BankAccountDTO) cbSourceAccount.getSelectedItem();
            List<EmployeeSummaryDTO> selected = listEmployees.getSelectedValuesList();
            summaryArea.setText("Period: " + period.periodNumber() + "\nSource Account: " + account.label()
                    + "\nPayment Date: " + dcPaymentDate.getText() + "\nFile Format: " + cbFileFormat.getSelectedItem()
                    + "\nEmployees: " + selected.size() + "\n\nClick \"Generate File\" to save the payment file.");
            cardLayout.show(cards, CARD_CONFIRM);
            currentCard = CARD_CONFIRM;
            btNext.setVisible(false);
            btGenerateFile.setVisible(true);
        }
    }

    private void onBack() {
        if (CARD_CONFIRM.equals(currentCard)) {
            cardLayout.show(cards, CARD_FORMAT);
            currentCard = CARD_FORMAT;
            btNext.setVisible(true);
            btGenerateFile.setVisible(false);
        } else if (CARD_FORMAT.equals(currentCard)) {
            cardLayout.show(cards, CARD_EMPLOYEES);
            currentCard = CARD_EMPLOYEES;
        } else if (CARD_EMPLOYEES.equals(currentCard)) {
            cardLayout.show(cards, CARD_SOURCE);
            currentCard = CARD_SOURCE;
            btBack.setEnabled(false);
        }
    }

    private void onGenerateFile() {
        PeriodDTO period = (PeriodDTO) cbPeriod.getSelectedItem();
        BankAccountDTO account = (BankAccountDTO) cbSourceAccount.getSelectedItem();
        List<EmployeeId> selectedIds = new ArrayList<>();
        for (EmployeeSummaryDTO summary : listEmployees.getSelectedValuesList()) {
            selectedIds.add(summary.id());
        }
        java.time.LocalDate paymentDate = ((Date) dcPaymentDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        BankFileRequestDTO req = new BankFileRequestDTO(companyId, account, paymentDate, period.periodNumber(), selectedIds,
                (String) cbFileFormat.getSelectedItem());
        byte[] fileBytes = controller.generateBankFile(req);

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("payments-period" + period.periodNumber() + ".txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), fileBytes);
                JOptionPane.showMessageDialog(this, "Payment file written to " + chooser.getSelectedFile().getAbsolutePath(),
                        "Bank Payment File", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to write file: " + ex.getMessage(), "Bank Payment File", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
