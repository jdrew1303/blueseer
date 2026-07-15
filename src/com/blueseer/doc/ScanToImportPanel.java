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
import javax.swing.SwingWorker;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;

/**
 * Single, central entry point for "paper in, form pre-filled" across the
 * app (epic docs/epics/agentic-document-import.md) - one screen regardless
 * of document type, rather than a button wedged into every screen that
 * might receive paper. Scans, identifies what kind of document it is, and
 * routes into whichever existing BlueSeer screen owns that record type with
 * the extracted data pre-filled but not yet saved - the target screen's own
 * business logic and validation run exactly as they would for manual entry,
 * so nothing about how e.g. Receiver Maintenance saves a receipt needs to
 * be duplicated or shared here.
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
    private final JButton btOpenTarget = new JButton("Open in Receiver Maintenance");
    private JFileChooser fileChooser;

    private InvoiceExtraction pendingInvoice;

    public ScanToImportPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        JPanel card = new JPanel(new MigLayout("insets 12, wrap 1", "[grow, fill]"));
        card.setBorder(BorderFactory.createTitledBorder("Scan to Import"));
        add(card);

        card.add(new JLabel("Photograph or pick a supplier document - BlueSeer will figure out what it is and take you there."));
        card.add(btChoose);
        card.add(lblStatus);
        card.add(btOpenTarget);
        btOpenTarget.setVisible(false);

        btChoose.addActionListener(e -> chooseAndScan());
        btOpenTarget.addActionListener(e -> routeToTarget());
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
        btOpenTarget.setVisible(false);
        btChoose.setEnabled(false);
        lblStatus.setText("Reading document...");

        new ClassifyThenExtractTask(imageBytes, ext).execute();
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
                    btOpenTarget.setVisible(true);
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
        recvMaint.applyExtractedInvoice(pendingInvoice);
        pendingInvoice = null;
        btOpenTarget.setVisible(false);
        lblStatus.setText(" ");
    }
}
