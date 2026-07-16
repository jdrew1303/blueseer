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
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single, central entry point for "paper in, form pre-filled" across the
 * app (epic docs/epics/agentic-document-import.md) - one screen regardless
 * of document type, rather than a button wedged into every screen that
 * might receive paper. Multiple documents can be dropped in at once; each
 * sits in the Inbox until {@link ScanQueueProcessor}'s background thread
 * classifies/extracts it (Pending Review, or Error on failure); reviewing
 * and approving a Pending Review item moves it to the Outbox; Send
 * actually routes it into whichever existing BlueSeer screen owns that
 * record type (Sent) - the target screen's own business logic and
 * validation run exactly as they would for manual entry, so nothing about
 * how e.g. Receiver Maintenance saves a receipt needs to be duplicated or
 * shared here.
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
    private static final int REFRESH_INTERVAL_MS = 2000;

    /** One row in the left rail nav list - see {@link RailCellRenderer}. */
    private record RailItem(String state, String label, FlatSVGIcon icon) {
    }

    // Left rail
    private final JButton btAddFiles = new JButton("+ Add Files...");
    private final DefaultListModel<RailItem> railModel = new DefaultListModel<>();
    private final JList<RailItem> railList = new JList<>(railModel);
    private final Map<String, Integer> railCounts = new HashMap<>();
    private JFileChooser fileChooser;

    // List card
    private final QueueTableModel queueTableModel = new QueueTableModel();
    private final JTable queueTable = new JTable(queueTableModel);
    private final JButton btOpenSelected = new JButton("Review");
    private final JButton btRetrySelected = new JButton("Retry");
    private final JButton btDiscardSelected = new JButton("Discard");
    private final JLabel lblListStatus = new JLabel(" ");

    // Detail card
    private final DocumentPreviewPanel preview = new DocumentPreviewPanel();
    private final JComboBox<String> ddDocType = new JComboBox<>(new String[]{TYPE_INVOICE, TYPE_OTHER});
    private final JButton btApplyDocType = new JButton("Use This Type");
    private final JTextField tbSupplier = new JTextField(20);
    private final JTextField tbInvoiceNumber = new JTextField(12);
    private final JTextField tbTotal = new JTextField(8);
    private final LineTableModel lineModel = new LineTableModel();
    private final JTable lineTable = new JTable(lineModel);
    private final JLabel lblReconcileWarning = new JLabel(" ");
    private final JButton btBack = new JButton("Back to List");
    private final JButton btApprove = new JButton("Approve");
    private final JButton btReject = new JButton("Reject");
    private final JButton btSend = new JButton("Send");
    private final JButton btSendAndOpenNext = new JButton("Send & Open Next");
    private final JLabel lblDetailStatus = new JLabel(" ");
    private JSplitPane detailSplit;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(cardLayout);

    private String currentRailState = docData.QUEUE_INBOX;
    private docData.QueueItem currentDetailItem;
    private String currentInvoiceDate = "";
    private List<DocTagsParser.Block> currentDocTagsBlocks = List.of();

    public ScanToImportPanel() {
        ScanQueueProcessor.ensureStarted();

        setLayout(new BorderLayout());
        add(buildLeftRail(), BorderLayout.WEST);
        centerPanel.add(buildListCard(), "list");
        centerPanel.add(buildDetailCard(), "detail");
        add(centerPanel, BorderLayout.CENTER);

        btAddFiles.addActionListener(e -> addFiles());
        railList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && railList.getSelectedValue() != null) {
                selectRailState(railList.getSelectedValue().state());
            }
        });
        btOpenSelected.addActionListener(e -> openSelected());
        btRetrySelected.addActionListener(e -> retrySelected());
        btDiscardSelected.addActionListener(e -> discardSelected());
        btBack.addActionListener(e -> backToList());
        btApprove.addActionListener(e -> approveSelected());
        btReject.addActionListener(e -> rejectSelected());
        btSend.addActionListener(e -> sendSelected(false));
        btSendAndOpenNext.addActionListener(e -> sendSelected(true));
        btApplyDocType.addActionListener(e -> applyDocTypeOverride());
        queueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && btOpenSelected.isVisible()) {
                    openSelected();
                }
            }
        });

        railList.setSelectedIndex(0);
        selectRailState(docData.QUEUE_INBOX);

        Timer refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> {
            if (isShowing()) {
                refreshCounts();
                if (!"detail".equals(currentCardName())) {
                    refreshList();
                }
            }
        });
        refreshTimer.start();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (detailSplit != null) {
            SwingUtilities.invokeLater(() -> detailSplit.setDividerLocation(0.6));
        }
    }

    private String currentCardName() {
        return currentDetailItem == null ? "list" : "detail";
    }

    private JPanel buildLeftRail() {
        JPanel rail = new JPanel(new BorderLayout(0, 8));
        rail.setBorder(BorderFactory.createTitledBorder("Scan to Import"));
        rail.setPreferredSize(new Dimension(220, 0));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(btAddFiles);
        rail.add(topPanel, BorderLayout.NORTH);

        railModel.addElement(new RailItem(docData.QUEUE_INBOX, "Inbox", new FlatSVGIcon("images/queue-inbox.svg", 18, 18)));
        railModel.addElement(new RailItem(docData.QUEUE_PENDING_REVIEW, "Pending Review", new FlatSVGIcon("images/queue-pendingreview.svg", 18, 18)));
        railModel.addElement(new RailItem(docData.QUEUE_OUTBOX, "Outbox", new FlatSVGIcon("images/queue-outbox.svg", 18, 18)));
        railModel.addElement(new RailItem(docData.QUEUE_SENT, "Sent", new FlatSVGIcon("images/queue-sent.svg", 18, 18)));
        railModel.addElement(new RailItem(docData.QUEUE_ERROR, "Error", new FlatSVGIcon("images/queue-error.svg", 18, 18)));
        railList.setCellRenderer(new RailCellRenderer());
        railList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        railList.setFocusable(true);
        railList.setBorder(BorderFactory.createEmptyBorder());
        rail.add(railList, BorderLayout.CENTER);
        return rail;
    }

    /**
     * Draws each rail row as icon + label + a pill-shaped count badge (only
     * shown once a section actually has something in it), with a flat
     * highlight fill for the selected row - matching the sidebar look of
     * the reference mockup rather than JToggleButton's default chrome.
     */
    private class RailCellRenderer extends JPanel implements ListCellRenderer<RailItem> {

        private static final Color SELECTED_BACKGROUND = new Color(0xDC, 0xEA, 0xFB);
        private static final Color BADGE_BACKGROUND = new Color(0x3B, 0x6F, 0xD6);

        private final JLabel iconLabel = new JLabel();
        private final JLabel textLabel = new JLabel();
        private final JLabel badgeLabel = new JLabel();

        RailCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 10));
            setOpaque(true);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(iconLabel);
            left.add(textLabel);
            add(left, BorderLayout.CENTER);

            badgeLabel.setOpaque(true);
            badgeLabel.setForeground(Color.WHITE);
            badgeLabel.setBackground(BADGE_BACKGROUND);
            badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
            badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, 11f));
            badgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
            add(badgeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RailItem> list, RailItem value,
                int index, boolean isSelected, boolean cellHasFocus) {
            iconLabel.setIcon(value.icon());
            textLabel.setText(value.label());
            setBackground(isSelected ? SELECTED_BACKGROUND : list.getBackground());
            int count = railCounts.getOrDefault(value.state(), 0);
            badgeLabel.setVisible(count > 0);
            badgeLabel.setText(String.valueOf(count));
            return this;
        }
    }

    private JPanel buildListCard() {
        JPanel panel = new JPanel(new BorderLayout());
        queueTable.setRowHeight(24);
        queueTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(queueTable), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(btOpenSelected);
        toolbar.add(btRetrySelected);
        toolbar.add(btDiscardSelected);
        toolbar.add(lblListStatus);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDetailCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(btBack);
        topBar.add(lblDetailStatus);
        wrapper.add(topBar, BorderLayout.NORTH);

        detailSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, preview, buildReviewCard());
        detailSplit.setResizeWeight(0.6);
        wrapper.add(detailSplit, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildReviewCard() {
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 1", "[grow, fill]"));
        card.setBorder(BorderFactory.createTitledBorder("Extracted Fields - review and correct"));

        JPanel infoSection = new JPanel(new MigLayout("insets 8, wrap 2", "[right]8[grow, fill]"));
        infoSection.setBorder(BorderFactory.createTitledBorder("Document Info"));
        infoSection.add(new JLabel("Document Type:"));
        JPanel docTypeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        docTypeRow.add(ddDocType);
        docTypeRow.add(btApplyDocType);
        infoSection.add(docTypeRow);
        infoSection.add(new JLabel("Supplier:"));
        infoSection.add(tbSupplier);
        infoSection.add(new JLabel("Invoice/Packing Slip #:"));
        infoSection.add(tbInvoiceNumber);
        infoSection.add(new JLabel("Printed Total:"));
        infoSection.add(tbTotal);
        card.add(infoSection);

        JPanel lineSection = new JPanel(new MigLayout("insets 8, wrap 1", "[grow, fill]"));
        lineSection.setBorder(BorderFactory.createTitledBorder("Line Items - click a cell to correct it"));
        lineTable.setRowHeight(24);
        JScrollPane tableScroll = new JScrollPane(lineTable);
        tableScroll.setPreferredSize(new Dimension(400, 180));
        lineSection.add(tableScroll, "grow, push");
        card.add(lineSection, "grow, push");

        lblReconcileWarning.setForeground(new Color(0xB0, 0x30, 0x30));
        card.add(lblReconcileWarning);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionRow.add(btApprove);
        actionRow.add(btReject);
        actionRow.add(btSend);
        actionRow.add(btSendAndOpenNext);
        card.add(actionRow);

        return card;
    }

    private void addFiles() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setMultiSelectionEnabled(true);
        }
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        int added = 0;
        for (File file : fileChooser.getSelectedFiles()) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "jpeg";
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (docData.addQueueItem(bytes, fileName, ext) != null) {
                    added++;
                }
            } catch (IOException ex) {
                MainFrame.bslog(ex);
            }
        }
        lblListStatus.setText(added + " document(s) added to Inbox.");
        railList.setSelectedIndex(0);
        selectRailState(docData.QUEUE_INBOX);
    }

    private void selectRailState(String state) {
        currentRailState = state;
        currentDetailItem = null;
        preview.clear();
        clearReviewFields();
        cardLayout.show(centerPanel, "list");
        refreshList();
    }

    private void refreshCounts() {
        railCounts.put(docData.QUEUE_INBOX, docData.countQueueItems(docData.QUEUE_INBOX));
        railCounts.put(docData.QUEUE_PENDING_REVIEW, docData.countQueueItems(docData.QUEUE_PENDING_REVIEW));
        railCounts.put(docData.QUEUE_OUTBOX, docData.countQueueItems(docData.QUEUE_OUTBOX));
        railCounts.put(docData.QUEUE_SENT, docData.countQueueItems(docData.QUEUE_SENT));
        railCounts.put(docData.QUEUE_ERROR, docData.countQueueItems(docData.QUEUE_ERROR));
        railList.repaint();
    }

    /**
     * Re-queries the current rail section's items - called both on demand
     * (opening a section, after an action) and every couple of seconds by
     * the background-refresh timer while this list is on screen, so a
     * document the background processor just finished shows up without the
     * user doing anything. fireTableDataChanged() (inside setItems) clears
     * JTable's row selection as a side effect, so the previously selected
     * item's id is captured beforehand and restored afterward - otherwise
     * a periodic refresh landing between "select a row" and "click Open"
     * would silently drop the selection and make the button a no-op.
     */
    private void refreshList() {
        String selectedId = queueTable.getSelectedRow() >= 0
                ? queueTableModel.getItemAt(queueTable.getSelectedRow()).id() : null;
        queueTableModel.setItems(docData.listQueueItems(currentRailState));
        refreshCounts();
        boolean isError = docData.QUEUE_ERROR.equals(currentRailState);
        boolean isInbox = docData.QUEUE_INBOX.equals(currentRailState);
        boolean isSent = docData.QUEUE_SENT.equals(currentRailState);
        boolean isOutbox = docData.QUEUE_OUTBOX.equals(currentRailState);
        btOpenSelected.setVisible(!isInbox && !isError);
        btOpenSelected.setText(isSent ? "View" : (isOutbox ? "Open" : "Review"));
        btRetrySelected.setVisible(isError);
        btDiscardSelected.setVisible(isError || isInbox);
        if (selectedId != null) {
            for (int i = 0; i < queueTableModel.getRowCount(); i++) {
                if (queueTableModel.getItemAt(i).id().equals(selectedId)) {
                    queueTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private void openSelected() {
        int row = queueTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        openDetail(queueTableModel.getItemAt(row), currentRailState);
    }

    private void openDetail(docData.QueueItem item, String context) {
        currentDetailItem = item;

        byte[] bytes = docData.readQueueFileBytes(item);
        if (bytes != null) {
            preview.showDocument(bytes, item.ext());
        } else {
            preview.clear();
        }
        currentDocTagsBlocks = parseStoredBlocks(item.docTags());

        boolean isInvoice = DocumentClassification.INVOICE.equals(item.docType());
        ddDocType.setSelectedItem(isInvoice ? TYPE_INVOICE : TYPE_OTHER);
        boolean editableContext = docData.QUEUE_PENDING_REVIEW.equals(context) || docData.QUEUE_OUTBOX.equals(context);
        ddDocType.setEnabled(editableContext);
        btApplyDocType.setVisible(editableContext);

        if (isInvoice && item.extractedJson() != null && !item.extractedJson().isBlank()) {
            try {
                InvoiceExtraction invoice = DocumentExtractionService.fromJson(item.extractedJson(), InvoiceExtraction.class);
                showReviewFields(invoice);
                highlightSourceBlocks(invoice);
            } catch (DocumentExtractionService.DocumentExtractionException ex) {
                MainFrame.bslog(ex);
                clearReviewFields();
            }
        } else {
            clearReviewFields();
        }
        setReviewFieldsEditable(editableContext);

        boolean pendingCtx = docData.QUEUE_PENDING_REVIEW.equals(context);
        boolean outboxCtx = docData.QUEUE_OUTBOX.equals(context);
        btApprove.setVisible(pendingCtx);
        btReject.setVisible(pendingCtx);
        btSend.setVisible(outboxCtx);
        btSendAndOpenNext.setVisible(outboxCtx);

        lblDetailStatus.setText(item.filename() + (item.error().isBlank() ? "" : " - " + item.error()));
        cardLayout.show(centerPanel, "detail");
    }

    private void retrySelected() {
        int row = queueTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        docData.setQueueState(queueTableModel.getItemAt(row).id(), docData.QUEUE_INBOX);
        refreshList();
    }

    private void discardSelected() {
        int row = queueTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        docData.deleteQueueItem(queueTableModel.getItemAt(row).id());
        refreshList();
    }

    private void backToList() {
        currentDetailItem = null;
        preview.clear();
        clearReviewFields();
        cardLayout.show(centerPanel, "list");
        refreshList();
    }

    private void approveSelected() {
        if (currentDetailItem == null) {
            return;
        }
        InvoiceExtraction edited = buildInvoiceFromForm();
        try {
            String json = DocumentExtractionService.toJson(edited);
            docData.updateQueueExtractedJson(currentDetailItem.id(), DocumentClassification.INVOICE, json, currentDetailItem.docTags());
        } catch (DocumentExtractionService.DocumentExtractionException ex) {
            MainFrame.bslog(ex);
        }
        docData.setQueueState(currentDetailItem.id(), docData.QUEUE_OUTBOX);
        backToList();
    }

    private void rejectSelected() {
        if (currentDetailItem == null) {
            return;
        }
        docData.deleteQueueItem(currentDetailItem.id());
        backToList();
    }

    private void sendSelected(boolean openNext) {
        if (currentDetailItem == null) {
            return;
        }
        InvoiceExtraction toRoute = buildInvoiceFromForm();
        if (!routeToTarget(toRoute)) {
            return;
        }
        docData.setQueueState(currentDetailItem.id(), docData.QUEUE_SENT);
        if (openNext) {
            List<docData.QueueItem> remaining = docData.listQueueItems(docData.QUEUE_OUTBOX);
            if (!remaining.isEmpty()) {
                openDetail(remaining.get(0), docData.QUEUE_OUTBOX);
                return;
            }
        }
        backToList();
    }

    private boolean routeToTarget(InvoiceExtraction toRoute) {
        try {
            MainFrame.loadPanel("ReceiverMaintMenu", MainFrame.main);
            MainFrame.hidepanels();
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            MainFrame.bslog(ex);
            lblDetailStatus.setText("Couldn't open Receiver Maintenance.");
            return false;
        }
        Object recvMaintObj = MainFrame.panelmap.get("com.blueseer.rcv.RecvMaint");
        if (!(recvMaintObj instanceof RecvMaint recvMaint)) {
            lblDetailStatus.setText("Couldn't open Receiver Maintenance.");
            return false;
        }
        recvMaint.setVisible(true);
        recvMaint.initvars(new String[0]);
        recvMaint.applyExtractedInvoice(toRoute);
        return true;
    }

    /**
     * Lets the user override the AI's classification - it won't always be
     * right. Re-runs extraction with the invoice schema on the same bytes
     * (via the same ScanQueueProcessor pipeline the background worker
     * uses) rather than duplicating any of the classify/extract logic
     * here, and saves the corrected result back onto the queue row without
     * changing its Pending Review/Outbox state.
     */
    private void applyDocTypeOverride() {
        if (currentDetailItem == null) {
            return;
        }
        if (TYPE_INVOICE.equals(ddDocType.getSelectedItem())) {
            byte[] bytes = docData.readQueueFileBytes(currentDetailItem);
            if (bytes == null) {
                return;
            }
            btApplyDocType.setEnabled(false);
            lblDetailStatus.setText("Reading as an invoice/packing slip...");
            new ReExtractTask(currentDetailItem, bytes).execute();
        } else {
            docData.updateQueueExtractedJson(currentDetailItem.id(), DocumentClassification.OTHER, "", "");
            clearReviewFields();
            lblDetailStatus.setText("Marked as not a supported document type.");
        }
    }

    private class ReExtractTask extends SwingWorker<ScanQueueProcessor.ExtractionResult, Void> {

        private final docData.QueueItem item;
        private final byte[] bytes;
        private String errorMessage;

        ReExtractTask(docData.QueueItem item, byte[] bytes) {
            this.item = item;
            this.bytes = bytes;
        }

        @Override
        protected ScanQueueProcessor.ExtractionResult doInBackground() {
            try {
                return ScanQueueProcessor.runExtraction(bytes, item.ext(), true);
            } catch (DocumentExtractionService.DocumentExtractionException ex) {
                errorMessage = ex.getMessage();
                return null;
            }
        }

        @Override
        protected void done() {
            btApplyDocType.setEnabled(true);
            if (errorMessage != null) {
                lblDetailStatus.setText(errorMessage);
                return;
            }
            try {
                ScanQueueProcessor.ExtractionResult result = get();
                if (result == null || result.invoice() == null) {
                    lblDetailStatus.setText("Recognized as an invoice, but couldn't read the details - try a clearer photo.");
                    return;
                }
                String json = DocumentExtractionService.toJson(result.invoice());
                String blocksJson = result.blocks().isEmpty() ? "" : DocumentExtractionService.toJson(result.blocks());
                docData.updateQueueExtractedJson(item.id(), DocumentClassification.INVOICE, json, blocksJson);
                currentDocTagsBlocks = result.blocks();
                showReviewFields(result.invoice());
                highlightSourceBlocks(result.invoice());
                lblDetailStatus.setText("This looks like a supplier invoice/packing slip.");
            } catch (Exception ex) {
                MainFrame.bslog(ex);
                lblDetailStatus.setText("Something went wrong reading the document.");
            }
        }
    }

    private InvoiceExtraction buildInvoiceFromForm() {
        double total;
        try {
            total = Double.parseDouble(tbTotal.getText().trim());
        } catch (NumberFormatException ex) {
            total = 0;
        }
        return new InvoiceExtraction(tbSupplier.getText().trim(), tbInvoiceNumber.getText().trim(),
                currentInvoiceDate, total, lineModel.getLines());
    }

    private void showReviewFields(InvoiceExtraction invoice) {
        tbSupplier.setText(invoice.supplier());
        tbInvoiceNumber.setText(invoice.invoiceNumber());
        tbTotal.setText(bsFormatDouble(invoice.total()));
        lineModel.setLines(invoice.lines() == null ? new ArrayList<>() : new ArrayList<>(invoice.lines()));
        lblReconcileWarning.setText(invoice.totalsReconcile() ? " "
                : "The line items don't add up to the printed total - check quantities/prices below.");
        currentInvoiceDate = invoice.date();
    }

    private void clearReviewFields() {
        tbSupplier.setText("");
        tbInvoiceNumber.setText("");
        tbTotal.setText("");
        lineModel.setLines(new ArrayList<>());
        lblReconcileWarning.setText(" ");
        preview.highlightBlocks(List.of());
        currentInvoiceDate = "";
    }

    private void setReviewFieldsEditable(boolean editable) {
        tbSupplier.setEditable(editable);
        tbInvoiceNumber.setEditable(editable);
        tbTotal.setEditable(editable);
    }

    private static String bsFormatDouble(double value) {
        return String.format("%.2f", value);
    }

    private static List<DocTagsParser.Block> parseStoredBlocks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(DocumentExtractionService.fromJson(json, DocTagsParser.Block[].class));
        } catch (DocumentExtractionService.DocumentExtractionException ex) {
            return List.of();
        }
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
     * Backs the queue list table for whichever rail section is currently
     * selected (Inbox/Pending Review/Outbox/Sent/Error).
     */
    private static class QueueTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Filename", "Type", "Updated", "Status"};

        private List<docData.QueueItem> items = new ArrayList<>();

        void setItems(List<docData.QueueItem> items) {
            this.items = items;
            fireTableDataChanged();
        }

        docData.QueueItem getItemAt(int row) {
            return items.get(row);
        }

        @Override
        public int getRowCount() {
            return items.size();
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
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            docData.QueueItem item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.filename();
                case 1 -> item.docType() == null || item.docType().isBlank() ? "-" : item.docType();
                case 2 -> item.updated() == null ? "" : item.updated().toString();
                case 3 -> item.error() == null ? "" : item.error();
                default -> "";
            };
        }
    }

    /**
     * Backs the line-item review table - plain in-memory editable rows, not
     * tied to any Swing DB-bound table convention elsewhere in the app,
     * since these rows aren't a real BlueSeer record yet (nothing's been
     * saved). Column edits go straight back into InvoiceExtraction.Line
     * records on {@link #getLines()} so approving/sending always reads
     * whatever the user last typed.
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
