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
package com.blueseer.trc;

import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Lot genealogy / recall lookup - a standalone screen (not an ItemMaint tab,
 * since it's not scoped to one item), registered as a {@code menu_mstr}
 * panel the same way as {@link com.blueseer.ing.NutritionCompletenessPanel}.
 *
 * Two lookup directions, both built on {@link LotGenealogyData} (no new
 * schema - see that class's header for how the linkage already exists in
 * {@code tran_mstr}):
 *
 * <ul>
 * <li><b>Supplier lot</b> - "a supplier tells us lot X is bad, what did it go
 * into and who received it" - enter the raw material's lot/serial number,
 * this walks every batch that consumed it and every customer who received
 * each of those batches.</li>
 * <li><b>Batch</b> - "we need to recall batch Y, who has it" - enter the
 * finished good's own batch/serial number directly.</li>
 * </ul>
 *
 * Results are shown as one flat, denormalized table (one row per
 * batch/customer-shipment pair) so both directions and the CSV export share
 * the same shape - a supplier-lot search just produces more than one distinct
 * "Batch Serial" value.
 */
public class LotGenealogyPanel extends JPanel {

    private final JRadioButton rbSupplierLot = new JRadioButton("Supplier lot (find batches + customers)", true);
    private final JRadioButton rbBatch = new JRadioButton("Batch (find customers only)");
    private final JTextField tbItem = new JTextField(10);
    private final JTextField tbSerial = new JTextField(14);
    private final JButton btSearch = new JButton("Search");
    private final JButton btExport = new JButton("Export CSV");
    private final JLabel summaryLabel = new JLabel(" ");

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Batch Serial", "Batch Item", "Batch Description", "Customer Code", "Customer Name",
                    "Phone", "Email", "Ship Date", "Qty Shipped", "Order #"}, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    private final JTable table = new JTable(model);

    public LotGenealogyPanel() {
        setLayout(new BorderLayout(0, 8));

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(rbSupplierLot);
        modeGroup.add(rbBatch);

        JPanel toolbar = new JPanel(new MigLayout("insets 8, wrap 6", "[]10[]5[]10[]5[]push[]", "[][]"));
        toolbar.add(rbSupplierLot, "span 3");
        toolbar.add(rbBatch, "span 3, wrap");
        toolbar.add(new JLabel("Raw material item (optional, supplier-lot mode only)"));
        toolbar.add(tbItem);
        toolbar.add(new JLabel("Lot / Serial #"));
        toolbar.add(tbSerial);
        toolbar.add(btSearch);
        toolbar.add(summaryLabel, "align right");
        add(toolbar, BorderLayout.NORTH);

        JPanel south = new JPanel(new MigLayout("insets 8", "[]", "[]"));
        south.add(btExport);
        add(south, BorderLayout.SOUTH);

        rbSupplierLot.addActionListener(e -> tbItem.setEnabled(true));
        rbBatch.addActionListener(e -> tbItem.setEnabled(false));

        add(new JScrollPane(table), BorderLayout.CENTER);

        btExport.setEnabled(false);
        btSearch.addActionListener(e -> runSearch());
        btExport.addActionListener(e -> exportCsv());
    }

    private void runSearch() {
        String serial = tbSerial.getText().trim();
        if (serial.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a lot/serial number to search for.");
            return;
        }
        boolean supplierMode = rbSupplierLot.isSelected();
        String item = tbItem.getText().trim();

        btSearch.setEnabled(false);
        btExport.setEnabled(false);
        summaryLabel.setText("Searching...");
        model.setRowCount(0);

        new SwingWorker<ArrayList<Object[]>, Void>() {
            @Override
            protected ArrayList<Object[]> doInBackground() {
                ArrayList<Object[]> rows = new ArrayList<>();
                if (supplierMode) {
                    LotGenealogyData.SupplierLotTrace trace = LotGenealogyData.traceSupplierLot(item, serial);
                    for (LotGenealogyData.BatchHit batch : trace.batches()) {
                        appendBatchRows(rows, batch.batchSerial(), batch.batchItem(), batch.batchDesc());
                    }
                } else {
                    appendBatchRows(rows, serial, null, null);
                }
                return rows;
            }

            private void appendBatchRows(ArrayList<Object[]> rows, String batchSerial, String batchItem,
                    String batchDesc) {
                ArrayList<LotGenealogyData.CustomerShipment> shipments =
                        LotGenealogyData.traceBatchCustomers(batchSerial);
                String resolvedItem = batchItem;
                String resolvedDesc = batchDesc;
                if (shipments.isEmpty()) {
                    rows.add(new Object[]{batchSerial, resolvedItem == null ? "" : resolvedItem,
                            resolvedDesc == null ? "" : resolvedDesc, "", "(no shipments found for this batch yet)",
                            "", "", "", "", ""});
                    return;
                }
                for (LotGenealogyData.CustomerShipment s : shipments) {
                    rows.add(new Object[]{batchSerial, s.finishedItem(), s.itemDesc(), s.custCode(), s.custName(),
                            s.custPhone(), s.custEmail(), s.shipDate(), s.qty(), s.order()});
                }
            }

            @Override
            protected void done() {
                try {
                    ArrayList<Object[]> rows = get();
                    if (rows.isEmpty()) {
                        summaryLabel.setText(supplierMode
                                ? "No batches found that consumed this lot/serial."
                                : "No shipments found for this batch.");
                    } else {
                        for (Object[] row : rows) {
                            model.addRow(row);
                        }
                        long distinctBatches = rows.stream().map(r -> r[0]).distinct().count();
                        summaryLabel.setText(rows.size() + " row(s) across " + distinctBatches + " batch(es)");
                    }
                    btExport.setEnabled(!rows.isEmpty());
                } catch (Exception ex) {
                    bsmf.MainFrame.bslog(ex);
                    JOptionPane.showMessageDialog(LotGenealogyPanel.this, "Search failed: " + ex.getMessage());
                } finally {
                    btSearch.setEnabled(true);
                }
            }
        }.execute();
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("lot_genealogy.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                if (col > 0) {
                    fw.write(",");
                }
                fw.write(csvEscape(model.getColumnName(col)));
            }
            fw.write("\n");
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    if (col > 0) {
                        fw.write(",");
                    }
                    fw.write(csvEscape(String.valueOf(model.getValueAt(row, col))));
                }
                fw.write("\n");
            }
        } catch (IOException ex) {
            bsmf.MainFrame.bslog(ex);
            JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage());
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
