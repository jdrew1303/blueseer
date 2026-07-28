package com.blueseer.pay;

import com.blueseer.pay.ImportDtos.ColumnMappingDTO;
import com.blueseer.pay.ImportDtos.ImportCommitResultDTO;
import com.blueseer.pay.ImportDtos.ImportProfile;
import com.blueseer.pay.ImportDtos.ImportRowResultDTO;
import com.blueseer.pay.ImportDtos.ImportValidationResultDTO;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * S-28 Importing Hours from Text/CSV and S-29 Full Periodic CSV Import, per
 * docs/architecture/irish-payroll-2026-screen-specs.md - one wizard shape
 * for both, parameterised by {@link ImportProfile}.
 */
public class ImportHoursWizard extends JDialog {

    private static final String CARD_CHOOSE_FILE = "choose-file";
    private static final String CARD_MAP_COLUMNS = "map-columns";
    private static final String CARD_PREVIEW = "preview";

    private final IImportController controller;
    private final ImportProfile profile;

    private File selectedFile;
    private List<String> headers = List.of();
    private ImportValidationResultDTO lastValidation;

    private JTextField tbFilePath;
    private CardLayout cardLayout;
    private JPanel cards;
    private DefaultTableModel mappingModel;
    private DefaultTableModel previewModel;
    private JButton btBack;
    private JButton btNext;
    private JButton btImport;
    private String currentCard = CARD_CHOOSE_FILE;

    public ImportHoursWizard(Frame owner, IImportController controller, ImportProfile profile) {
        super(owner, profile == ImportProfile.HOURS_ONLY ? "Import Hours from Text/CSV" : "Full Periodic CSV Import", true);
        this.controller = controller;
        this.profile = profile;
        initComponents();
        setSize(620, 420);
        setLocation(60, 60);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.add(buildChooseFileCard(), CARD_CHOOSE_FILE);
        cards.add(buildMapColumnsCard(), CARD_MAP_COLUMNS);
        cards.add(buildPreviewCard(), CARD_PREVIEW);
        add(cards, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btBack = new JButton("Back");
        btBack.addActionListener(e -> onBack());
        btBack.setEnabled(false);
        btNext = new JButton("Next");
        btNext.addActionListener(e -> onNext());
        btImport = new JButton("Import");
        btImport.setVisible(false);
        btImport.addActionListener(e -> onImport());
        nav.add(btBack);
        nav.add(btNext);
        nav.add(btImport);
        add(nav, BorderLayout.SOUTH);
    }

    private JPanel buildChooseFileCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tbFilePath = new JTextField(30);
        tbFilePath.setEditable(false);
        JButton btBrowse = new JButton("Browse...");
        btBrowse.addActionListener(e -> onBrowse());
        panel.add(new JLabel("File:"));
        panel.add(tbFilePath);
        panel.add(btBrowse);
        return panel;
    }

    private JPanel buildMapColumnsCard() {
        JPanel panel = new JPanel(new BorderLayout());
        mappingModel = new DefaultTableModel(new Object[] { "Source Column", "Maps To" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        JTable table = new JTable(mappingModel);
        JComboBox<String> targetCombo = new JComboBox<>(ImportDtos.targetFields(profile).toArray(new String[0]));
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(targetCombo));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPreviewCard() {
        JPanel panel = new JPanel(new BorderLayout());
        previewModel = new DefaultTableModel(new Object[] { "Row", "Status", "Detail" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(previewModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            tbFilePath.setText(selectedFile.getAbsolutePath());
        }
    }

    private void onNext() {
        if (CARD_CHOOSE_FILE.equals(currentCard)) {
            if (selectedFile == null) {
                JOptionPane.showMessageDialog(this, "Choose a file first.", "Import", JOptionPane.WARNING_MESSAGE);
                return;
            }
            headers = controller.parseFileHeaders(selectedFile, profile);
            mappingModel.setRowCount(0);
            for (String header : headers) {
                mappingModel.addRow(new Object[] { header, "(ignore)" });
            }
            cardLayout.show(cards, CARD_MAP_COLUMNS);
            currentCard = CARD_MAP_COLUMNS;
            btBack.setEnabled(true);
        } else if (CARD_MAP_COLUMNS.equals(currentCard)) {
            Map<String, String> sourceToTarget = new LinkedHashMap<>();
            for (int i = 0; i < mappingModel.getRowCount(); i++) {
                sourceToTarget.put((String) mappingModel.getValueAt(i, 0), (String) mappingModel.getValueAt(i, 1));
            }
            lastValidation = controller.validateImport(selectedFile, new ColumnMappingDTO(sourceToTarget), profile);
            previewModel.setRowCount(0);
            for (ImportRowResultDTO row : lastValidation.rows()) {
                previewModel.addRow(new Object[] { row.rowNumber(), row.valid() ? "Valid" : "Error", row.errorOrNull() == null ? "" : row.errorOrNull() });
            }
            cardLayout.show(cards, CARD_PREVIEW);
            currentCard = CARD_PREVIEW;
            btNext.setVisible(false);
            btImport.setVisible(true);
        }
    }

    private void onBack() {
        if (CARD_PREVIEW.equals(currentCard)) {
            cardLayout.show(cards, CARD_MAP_COLUMNS);
            currentCard = CARD_MAP_COLUMNS;
            btNext.setVisible(true);
            btImport.setVisible(false);
        } else if (CARD_MAP_COLUMNS.equals(currentCard)) {
            cardLayout.show(cards, CARD_CHOOSE_FILE);
            currentCard = CARD_CHOOSE_FILE;
            btBack.setEnabled(false);
        }
    }

    private void onImport() {
        Map<String, String> sourceToTarget = new LinkedHashMap<>();
        for (int i = 0; i < mappingModel.getRowCount(); i++) {
            sourceToTarget.put((String) mappingModel.getValueAt(i, 0), (String) mappingModel.getValueAt(i, 1));
        }
        ImportCommitResultDTO result = controller.commitImport(selectedFile, new ColumnMappingDTO(sourceToTarget), profile);
        StringBuilder summary = new StringBuilder();
        summary.append(result.committedCount()).append(" row(s) committed, ").append(result.excludedCount()).append(" excluded.");
        for (String message : result.messages()) {
            summary.append('\n').append(message);
        }
        JOptionPane.showMessageDialog(this, summary.toString(), "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
