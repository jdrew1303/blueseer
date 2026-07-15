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

    private static final String TYPE_INVOICE = "Invoice / Packing Slip";
    private static final String TYPE_OTHER = "Other / Not Supported";

    private final JButton btChoose = new JButton("Choose Photo or File...");
    private final JLabel lblStatus = new JLabel(" ");
    private final JLabel lblReconcileWarning = new JLabel(" ");
    private final JButton btOpenTarget = new JButton("Open in Receiver Maintenance");
    private final javax.swing.JComboBox<String> ddDocType = new javax.swing.JComboBox<>(new String[]{TYPE_INVOICE, TYPE_OTHER});
    private final JButton btApplyDocType = new JButton("Use This Type");
    private JFileChooser fileChooser;

    private final DocumentPreviewPanel preview = new DocumentPreviewPanel();
    private final JTextField tbSupplier = new JTextField(20);
    private final JTextField tbInvoiceNumber = new JTextField(12);
    private final JTextField tbTotal = new JTextField(8);
    private final LineTableModel lineModel = new LineTableModel();
    private final JTable lineTable = new JTable(lineModel);

    private final JSplitPane splitPane;
    private InvoiceExtraction pendingInvoice;
    private byte[] currentImageBytes;
    private String currentExt;
    private List<DocTagsParser.Block> currentDocTagsBlocks = List.of();

    public ScanToImportPanel() {
        setLayout(new java.awt.BorderLayout());

        JPanel topStrip = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel introCard = new JPanel(new MigLayout("insets 12, wrap 1", "[grow, fill]"));
        introCard.setBorder(BorderFactory.createTitledBorder("Scan to Import"));
        topStrip.add(introCard);
        introCard.add(new JLabel("Photograph or pick a supplier document - BlueSeer will figure out what it is and show you what it read."));
        introCard.add(btChoose);
        introCard.add(lblStatus);
        JPanel docTypeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        docTypeRow.add(new JLabel("Document Type:"));
        docTypeRow.add(ddDocType);
        docTypeRow.add(btApplyDocType);
        introCard.add(docTypeRow);
        ddDocType.setVisible(false);
        btApplyDocType.setVisible(false);
        add(topStrip, java.awt.BorderLayout.NORTH);

        // The document is the source of truth, so it gets the larger share
        // of the split (60/40) - resizeWeight alone only governs how *extra*
        // space from a window resize is distributed, so the initial 60%
        // position is set proportionally once this panel actually has a
        // size (see addNotify()).
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, preview, buildReviewCard());
        splitPane.setResizeWeight(0.6);
        add(splitPane, java.awt.BorderLayout.CENTER);

        btChoose.addActionListener(e -> chooseAndScan());
        btOpenTarget.addActionListener(e -> routeToTarget());
        btApplyDocType.addActionListener(e -> applyDocTypeOverride());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.6));
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
        currentImageBytes = imageBytes;
        currentExt = ext;
        currentDocTagsBlocks = List.of();
        clearReview();
        ddDocType.setVisible(false);
        btApplyDocType.setVisible(false);
        preview.showDocument(imageBytes, ext);
        btChoose.setEnabled(false);
        lblStatus.setText("Reading document...");

        new ClassifyThenExtractTask(imageBytes, ext).execute();
    }

    /**
     * Lets the user override the AI's classification - it won't always be
     * right, and until more document types/targets are wired up, "Other" is
     * a dead end otherwise. Re-runs extraction with the invoice schema on
     * the same bytes rather than duplicating any of the classify/extract
     * logic here.
     */
    private void applyDocTypeOverride() {
        if (currentImageBytes == null) {
            return;
        }
        if (TYPE_INVOICE.equals(ddDocType.getSelectedItem())) {
            if (pendingInvoice != null) {
                return;
            }
            btApplyDocType.setEnabled(false);
            lblStatus.setText("Reading as an invoice/packing slip...");
            new ExtractInvoiceTask(currentImageBytes, currentExt).execute();
        } else {
            pendingInvoice = null;
            clearReview();
            lblStatus.setText("Marked as not a supported document type.");
        }
    }

    private void clearReview() {
        tbSupplier.setText("");
        tbInvoiceNumber.setText("");
        tbTotal.setText("");
        lineModel.setLines(new ArrayList<>());
        lblReconcileWarning.setText(" ");
        btOpenTarget.setVisible(false);
        preview.highlightBlocks(List.of());
    }

    private void showReview(InvoiceExtraction invoice) {
        tbSupplier.setText(invoice.supplier());
        tbInvoiceNumber.setText(invoice.invoiceNumber());
        tbTotal.setText(bsFormatDouble(invoice.total()));
        lineModel.setLines(invoice.lines() == null ? new ArrayList<>() : new ArrayList<>(invoice.lines()));
        lblReconcileWarning.setText(invoice.totalsReconcile() ? " "
                : "The line items don't add up to the printed total - check quantities/prices below.");
        btOpenTarget.setVisible(true);
        highlightSourceBlocks(invoice);
    }

    private static String bsFormatDouble(double value) {
        return String.format("%.2f", value);
    }

    /**
     * Draws a box on the preview around whichever DocTags-tagged region of
     * the source document each extracted field most likely came from -
     * only has anything to work with when the optional document-layout
     * model is configured (see docData.layout_llm_config); a plain
     * substring correlation rather than asking either model to echo
     * coordinates back through the JSON, which small models aren't
     * reliable at copying faithfully.
     */
    private void highlightSourceBlocks(InvoiceExtraction invoice) {
        if (currentDocTagsBlocks.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>();
        values.add(invoice.supplier());
        values.add(invoice.invoiceNumber());
        if (invoice.lines() != null) {
            for (InvoiceExtraction.Line line : invoice.lines()) {
                values.add(line.description());
            }
        }
        List<DocTagsParser.Block> matches = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String needle = value.trim().toLowerCase();
            for (DocTagsParser.Block block : currentDocTagsBlocks) {
                String haystack = block.text().toLowerCase();
                if (!haystack.isBlank() && (haystack.contains(needle) || needle.contains(haystack))) {
                    matches.add(block);
                    break;
                }
            }
        }
        preview.highlightBlocks(matches);
    }

    /**
     * Result of one classify(+extract) run - blocks is only ever non-empty
     * when the optional document-layout model produced DocTags for this
     * document (see docData.layout_llm_config); empty otherwise (the
     * ordinary single-pass, image-straight-to-JSON path).
     */
    private record ExtractionResult(DocumentClassification classification, InvoiceExtraction invoice,
            List<DocTagsParser.Block> blocks) {
    }

    /**
     * Runs classify+extract (or, with forceInvoice, extraction alone,
     * skipping classification entirely - the manual "Use This Type"
     * override path). When a document-layout model is configured, this is
     * genuinely two model calls per step: image -> DocTags (the layout
     * model), then DocTags plain text -> JSON (the regular extraction
     * model, text-only) - a layout model like granite-docling-258M can't
     * classify/extract JSON itself, see DocumentExtractionService's class
     * javadoc. Falls back to sending the image straight to the extraction
     * model, exactly as before, whenever no layout model is configured.
     */
    private ExtractionResult runExtraction(byte[] imageBytes, String ext, boolean forceInvoice)
            throws DocumentExtractionService.DocumentExtractionException {
        docData.layout_llm_config layoutCfg = docData.getLayoutLlmConfig();
        List<DocTagsParser.Block> blocks = List.of();
        String plainText = null;
        if (layoutCfg.configured()) {
            String rawDocTags = DocumentExtractionService.convertToDocTags(imageBytes, ext, layoutCfg);
            blocks = DocTagsParser.parse(rawDocTags);
            plainText = DocTagsParser.toPlainText(blocks);
        }

        DocumentClassification classification = null;
        if (!forceInvoice) {
            classification = plainText != null
                    ? DocumentExtractionService.extractStructuredFromText(plainText, DocumentClassification.SYSTEM_INSTRUCTIONS,
                            DocumentClassification.JSON_SHAPE, DocumentClassification.class)
                    : DocumentExtractionService.extractStructured(imageBytes, ext, DocumentClassification.SYSTEM_INSTRUCTIONS,
                            DocumentClassification.JSON_SHAPE, DocumentClassification.class);
        }

        InvoiceExtraction invoice = null;
        if (forceInvoice || (classification != null && DocumentClassification.INVOICE.equals(classification.documentType()))) {
            invoice = plainText != null
                    ? DocumentExtractionService.extractStructuredFromText(plainText, InvoiceExtraction.SYSTEM_INSTRUCTIONS,
                            InvoiceExtraction.JSON_SHAPE, InvoiceExtraction.class)
                    : DocumentExtractionService.extractStructured(imageBytes, ext, InvoiceExtraction.SYSTEM_INSTRUCTIONS,
                            InvoiceExtraction.JSON_SHAPE, InvoiceExtraction.class);
        }

        return new ExtractionResult(classification, invoice, blocks);
    }

    private class ClassifyThenExtractTask extends SwingWorker<ExtractionResult, Void> {

        private final byte[] imageBytes;
        private final String ext;
        private String errorMessage;

        ClassifyThenExtractTask(byte[] imageBytes, String ext) {
            this.imageBytes = imageBytes;
            this.ext = ext;
        }

        @Override
        public ExtractionResult doInBackground() {
            try {
                return runExtraction(imageBytes, ext, false);
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
            ExtractionResult result;
            try {
                result = get();
            } catch (Exception ex) {
                MainFrame.bslog(ex);
                lblStatus.setText("Something went wrong reading the document.");
                return;
            }
            if (result == null || result.classification() == null) {
                lblStatus.setText("Something went wrong reading the document.");
                return;
            }
            currentDocTagsBlocks = result.blocks();
            boolean detectedInvoice = DocumentClassification.INVOICE.equals(result.classification().documentType());
            ddDocType.setSelectedItem(detectedInvoice ? TYPE_INVOICE : TYPE_OTHER);
            ddDocType.setVisible(true);
            btApplyDocType.setVisible(true);
            if (!detectedInvoice) {
                lblStatus.setText("This doesn't look like a supported document type yet - if it's actually an invoice/packing slip, "
                        + "pick that above and click \"" + btApplyDocType.getText() + "\".");
                return;
            }
            if (result.invoice() != null) {
                pendingInvoice = result.invoice();
                lblStatus.setText("This looks like a supplier invoice/packing slip.");
                showReview(result.invoice());
            } else {
                lblStatus.setText("Recognized as an invoice, but couldn't read the details - try a clearer photo.");
            }
        }
    }

    private class ExtractInvoiceTask extends SwingWorker<ExtractionResult, Void> {

        private final byte[] imageBytes;
        private final String ext;
        private String errorMessage;

        ExtractInvoiceTask(byte[] imageBytes, String ext) {
            this.imageBytes = imageBytes;
            this.ext = ext;
        }

        @Override
        protected ExtractionResult doInBackground() {
            try {
                return runExtraction(imageBytes, ext, true);
            } catch (DocumentExtractionService.DocumentExtractionException ex) {
                errorMessage = ex.getMessage();
                return null;
            }
        }

        @Override
        protected void done() {
            btApplyDocType.setEnabled(true);
            if (errorMessage != null) {
                lblStatus.setText(errorMessage);
                return;
            }
            try {
                ExtractionResult result = get();
                if (result != null && result.invoice() != null) {
                    currentDocTagsBlocks = result.blocks();
                    pendingInvoice = result.invoice();
                    lblStatus.setText("This looks like a supplier invoice/packing slip.");
                    showReview(result.invoice());
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
        currentImageBytes = null;
        currentExt = null;
        currentDocTagsBlocks = List.of();
        clearReview();
        ddDocType.setVisible(false);
        btApplyDocType.setVisible(false);
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
