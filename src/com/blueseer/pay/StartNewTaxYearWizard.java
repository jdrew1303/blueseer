package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.TaxYearRolloverDtos.ChecklistRowDTO;
import com.blueseer.pay.TaxYearRolloverDtos.TaxYearRolloverResult;
import com.blueseer.pay.TaxYearRolloverDtos.YearEndChecklistDTO;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** S-65 Start New Tax Year. */
public class StartNewTaxYearWizard extends JDialog {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String CHECKLIST = "checklist";
    private static final String CONFIRM = "confirm";
    private static final String COMPLETE = "complete";

    private final ITaxYearRolloverController controller;
    private final int closingTaxYear;
    private final int newTaxYear;

    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JButton btStartNewYear;
    private JLabel lblCompleteText;

    public StartNewTaxYearWizard(Window owner, ITaxYearRolloverController controller, int closingTaxYear, int newTaxYear) {
        super(owner, "Start New Tax Year", ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.closingTaxYear = closingTaxYear;
        this.newTaxYear = newTaxYear;
        initComponents();
        setSize(520, 380);
        setLocationRelativeTo(owner);
        refreshChecklist();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(buildChecklistCard(), CHECKLIST);
        cardPanel.add(buildConfirmCard(), CONFIRM);
        cardPanel.add(buildCompleteCard(), COMPLETE);
        add(cardPanel, BorderLayout.CENTER);
        cardLayout.show(cardPanel, CHECKLIST);
    }

    private DefaultTableModel checklistModel;

    private JPanel buildChecklistCard() {
        JPanel panel = new JPanel(new BorderLayout());
        checklistModel = new DefaultTableModel(new Object[] {"Check", "Status", "Detail"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(checklistModel);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                boolean ok = "OK".equals(value);
                c.setForeground(ok ? new Color(0, 130, 0) : new Color(180, 0, 0));
                return c;
            }
        });
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btNext = new JButton("Next >");
        btNext.addActionListener(e -> cardLayout.show(cardPanel, CONFIRM));
        south.add(btNext);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConfirmCard() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Start tax year " + newTaxYear + "? This closes " + closingTaxYear + " for new pay entries."));
        panel.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btBack = new JButton("< Back");
        btBack.addActionListener(e -> cardLayout.show(cardPanel, CHECKLIST));
        btStartNewYear = new JButton("Start New Year");
        btStartNewYear.addActionListener(e -> onStartNewYear());
        south.add(btBack);
        south.add(btStartNewYear);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCompleteCard() {
        JPanel panel = new JPanel(new BorderLayout());
        lblCompleteText = new JLabel("-");
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(lblCompleteText);
        panel.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> dispose());
        south.add(btClose);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshChecklist() {
        checklistModel.setRowCount(0);
        YearEndChecklistDTO checklist = controller.getYearEndChecklist(DEMO_COMPANY, closingTaxYear);
        boolean allOk = true;
        for (ChecklistRowDTO row : checklist.rows()) {
            checklistModel.addRow(new Object[] {row.label(), row.satisfied() ? "OK" : "PENDING", row.detail()});
            allOk &= row.satisfied();
        }
        btStartNewYear.setEnabled(allOk);
    }

    private void onStartNewYear() {
        btStartNewYear.setEnabled(false);
        SwingWorker<TaxYearRolloverResult, Void> worker = new SwingWorker<>() {
            @Override
            protected TaxYearRolloverResult doInBackground() {
                return controller.startNewTaxYear(DEMO_COMPANY, newTaxYear);
            }

            @Override
            protected void done() {
                try {
                    TaxYearRolloverResult result = get();
                    if (result.success()) {
                        lblCompleteText.setText("<html><body style='width: 380px'>" + result.message() + "</body></html>");
                        cardLayout.show(cardPanel, COMPLETE);
                    } else {
                        JOptionPane.showMessageDialog(StartNewTaxYearWizard.this, result.message(), "Start New Tax Year", JOptionPane.ERROR_MESSAGE);
                        btStartNewYear.setEnabled(true);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StartNewTaxYearWizard.this, "Rollover failed: " + ex.getMessage(), "Start New Tax Year", JOptionPane.ERROR_MESSAGE);
                    btStartNewYear.setEnabled(true);
                }
            }
        };
        worker.execute();
    }
}
