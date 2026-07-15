/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.doc;

import bsmf.MainFrame;
import com.blueseer.doc.schema.DocumentClassification;
import com.blueseer.doc.schema.InvoiceExtraction;
import com.blueseer.rcv.RecvMaint;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Single, central entry point for "paper in, form pre-filled" across the
 * app (epic docs/epics/agentic-document-import.md) - one screen regardless
 * of document type, rather than a button wedged into every screen that
 * might receive paper. Scans, identifies what kind of document it is, shows
 * it side by side with what was read off it (IcePDF for a PDF, a plain
 * image otherwise - see DocumentPreviewPanel) so the user can catch a
 * misread quantity or price before anything is saved, and then routes into
 * whichever existing BlueSeer screen owns that record type with the
 * (possibly corrected) data pre-filled but not yet saved - the target
 * screen's own business logic and validation run exactly as they would for
 * manual entry, so nothing about how e.g. Receiver Maintenance saves a
 * receipt needs to be duplicated or shared here.
 *
 * Only one target is wired up so far: an invoice/packing-slip routes into
 * Receiver Maintenance. Adding a new document type/target means a new
 * schema record next to {@link InvoiceExtraction}, a new
 * {@link DocumentClassification#documentType()} value, and a new branch in
 * {@link #routeToTarget}.
 */
public class ScanToImportPanel extends JPanel {

    private final JButton btChoose = new JButton("Choose Photo or File...");
    private final JLabel lblStatus = new JLabel(" ");
    private final JLabel lblReconcileWarning = new JLabel(" ");
    private final JButton btOpenTarget = new JButton("Open in Receiver Maintenance");
    private JFileChooser fileChooser;

    private final DocumentPreviewPanel preview = new DocumentPreviewPanel();
    private final JTextField tbSupplier = new JTextField(20);
    private final JTextField tbInvoiceNumber = new JTextField(12);
    private final JTextField tbTotal = new JTextField(8);
    private final LineTableModel lineModel = new LineTableModel();
    private final JTable lineTable = new JTable(lineModel);

    private InvoiceExtraction pendingInvoice;

    public ScanToImportPanel() {
        setLayout(new java.awt.BorderLayout());

        JPanel topStrip = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel introCard = new JPanel(new MigLayout("insets 12, wrap 1", "[grow, fill]"));
        introCard.setBorder(BorderFactory.createTitledBorder("Scan to Import"));
        topStrip.add(introCard);
        introCard.add(new JLabel("Photograph or pick a supplier document - BlueSeer will figure out what it is and show you what it read."));
        introCard.add(btChoose);
        introCard.add(lblStatus);
        add(topStrip, java.awt.BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, preview, buildReviewCard());
        splitPane.setResizeWeight(0.55);
        splitPane.setDividerLocation(500);
        add(splitPane, java.awt.BorderLayout.CENTER);

        btChoose.addActionListener(e -> chooseAndScan());
        btOpenTarget.addActionListener(e -> routeToTarget());
    }

    private JPanel buildReviewCard() {
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 2", "[right]8[grow, fill]"));
        card.setBorder(BorderFactory.createTitledBorder("Extracted Fields - review and correct before opening"));

        card.add(new JLabel("Supplier:"));
        card.add(tbSupplier);
        card.add(new JLabel("Invoice/Packing Slip #:"));
        card.add(tbInvoiceNumber);
        card.add(new JLabel("Printed Total:"));
        card.add(tbTotal);

        lineTable.setRowHeight(24);
        JScrollPane tableScroll = new JScrollPane(lineTable);
        tableScroll.setPreferredSize(new java.awt.Dimension(400, 200));
        card.add(new JLabel("Line Items (click a cell to correct it):"), "span 2");
        card.add(tableScroll, "span 2, grow, push");

        lblReconcileWarning.setForeground(new Color(0xB0, 0x30, 0x30));
        card.add(lblReconcileWarning, "span 2");

        card.add(btOpenTarget, "span 2, align right");
        btOpenTarget.setVisible(false);

        return card;
    }

    private void chooseAndScan() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        }
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "jpeg";
        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            MainFrame.bslog(ex);
            lblStatus.setText("Couldn't read that file.");
            return;
        }

        pendingInvoice = null;
        clearReview();
        preview.showDocument(imageBytes, ext);
        btChoose.setEnabled(false);
        lblStatus.setText("Reading document...");

        new ClassifyThenExtractTask(imageBytes, ext).execute();
    }

    private void clearReview() {
        tbSupplier.setText("");
        tbInvoiceNumber.setText("");
        tbTotal.setText("");
        lineModel.setLines(new ArrayList<>());
        lblReconcileWarning.setText(" ");
        btOpenTarget.setVisible(false);
    }

    private void showReview(InvoiceExtraction invoice) {
        tbSupplier.setText(invoice.supplier());
        tbInvoiceNumber.setText(invoice.invoiceNumber());
        tbTotal.setText(bsFormatDouble(invoice.total()));
        lineModel.setLines(invoice.lines() == null ? new ArrayList<>() : new ArrayList<>(invoice.lines()));
        lblReconcileWarning.setText(invoice.totalsReconcile() ? " "
                : "The line items don't add up to the printed total - check quantities/prices below.");
        btOpenTarget.setVisible(true);
    }

    private static String bsFormatDouble(double value) {
        return String.format("%.2f", value);
    }

    private class ClassifyThenExtractTask extends SwingWorker<Object, Void> {

        private final byte[] imageBytes;
        private final String ext;
        private String errorMessage;
        private DocumentClassification classification;

        ClassifyThenExtractTask(byte[] imageBytes, String ext) {
            this.imageBytes = imageBytes;
            this.ext = ext;
        }

        @Override
        public Object doInBackground() {
            try {
                classification = DocumentExtractionService.extractStructured(imageBytes, ext,
                        DocumentClassification.SYSTEM_INSTRUCTIONS, DocumentClassification.JSON_SHAPE,
                        DocumentClassification.class);
                if (DocumentClassification.INVOICE.equals(classification.documentType())) {
                    return DocumentExtractionService.extractStructured(imageBytes, ext,
                            InvoiceExtraction.SYSTEM_INSTRUCTIONS, InvoiceExtraction.JSON_SHAPE, InvoiceExtraction.class);
                }
                return null;
            } catch (DocumentExtractionService.DocumentExtractionException ex) {
                errorMessage = ex.getMessage();
                return null;
            }
        }

        @Override
        public void done() {
            btChoose.setEnabled(true);
            if (errorMessage != null) {
                lblStatus.setText(errorMessage);
                return;
            }
            if (classification == null || DocumentClassification.OTHER.equals(classification.documentType())) {
                lblStatus.setText("This doesn't look like a supported document type yet.");
                return;
            }
            try {
                Object result = get();
                if (result instanceof InvoiceExtraction invoice) {
                    pendingInvoice = invoice;
                    lblStatus.setText("This looks like a supplier invoice/packing slip.");
                    showReview(invoice);
                } else {
                    lblStatus.setText("Recognized as " + classification.documentType() + ", but there's no screen wired up for it yet.");
                }
            } catch (Exception ex) {
                MainFrame.bslog(ex);
                lblStatus.setText("Something went wrong reading the document.");
            }
        }
    }

    private void routeToTarget() {
        if (pendingInvoice == null) {
            return;
        }
        // Rebuild the record from whatever's now in the review fields/table -
        // the user may have corrected a misread quantity/price/supplier name
        // after comparing against the source document in the preview pane.
        double total;
        try {
            total = Double.parseDouble(tbTotal.getText().trim());
        } catch (NumberFormatException ex) {
            total = pendingInvoice.total();
        }
        InvoiceExtraction toRoute = new InvoiceExtraction(
                tbSupplier.getText().trim(), tbInvoiceNumber.getText().trim(), pendingInvoice.date(),
                total, lineModel.getLines());

        try {
            MainFrame.loadPanel("ReceiverMaintMenu", MainFrame.main);
            MainFrame.hidepanels();
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            MainFrame.bslog(ex);
            lblStatus.setText("Couldn't open Receiver Maintenance.");
            return;
        }
        Object recvMaintObj = MainFrame.panelmap.get("com.blueseer.rcv.RecvMaint");
        if (!(recvMaintObj instanceof RecvMaint recvMaint)) {
            lblStatus.setText("Couldn't open Receiver Maintenance.");
            return;
        }
        recvMaint.setVisible(true);
        recvMaint.initvars(new String[0]);
        recvMaint.applyExtractedInvoice(toRoute);
        pendingInvoice = null;
        clearReview();
        preview.clear();
        lblStatus.setText(" ");
    }

    /**
     * Backs the line-item review table - plain in-memory editable rows, not
     * tied to any Swing DB-bound table convention elsewhere in the app,
     * since these rows aren't a real BlueSeer record yet (nothing's been
     * saved). Column edits go straight back into InvoiceExtraction.Line
     * records on {@link #getLines()} so routeToTarget always reads whatever
     * the user last typed.
     */
    private static class LineTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Description", "Qty", "Unit", "Unit Cost"};

        private List<InvoiceExtraction.Line> lines = new ArrayList<>();

        void setLines(List<InvoiceExtraction.Line> lines) {
            this.lines = lines;
            fireTableDataChanged();
        }

        List<InvoiceExtraction.Line> getLines() {
            return lines;
        }

        @Override
        public int getRowCount() {
            return lines.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InvoiceExtraction.Line line = lines.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> line.description();
                case 1 -> line.quantity();
                case 2 -> line.unit();
                case 3 -> line.unitCost();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            InvoiceExtraction.Line line = lines.get(rowIndex);
            String description = line.description();
            double quantity = line.quantity();
            String unit = line.unit();
            double unitCost = line.unitCost();
            try {
                switch (columnIndex) {
                    case 0 -> description = value.toString();
                    case 1 -> quantity = Double.parseDouble(value.toString());
                    case 2 -> unit = value.toString();
                    case 3 -> unitCost = Double.parseDouble(value.toString());
                    default -> {
                    }
                }
            } catch (NumberFormatException ex) {
                return;
            }
            lines.set(rowIndex, new InvoiceExtraction.Line(description, quantity, unit, unitCost, line.confidence()));
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
