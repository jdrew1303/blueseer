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
package com.blueseer.ing;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Ingredient Data" tab for ItemMaint: EU/Irish FIC regulatory metadata for
 * an item used as a raw material/ingredient elsewhere (legal name, additive
 * category/E-number, allergens, and - for a purchased item that is itself a
 * compound ingredient BlueSeer has no BOM visibility into - the supplier's
 * declared sub-ingredient breakdown). ItemMaint calls loadData()/saveData()
 * alongside its own item_mstr load/save, the same way it already does for
 * attachments (see ItemMaint.getRecord/addRecord/updateRecord).
 */
public class IngredientPanel extends JPanel {

    private final javax.swing.JTextField tbLegalName = new javax.swing.JTextField();
    private final javax.swing.JTextField tbCategory = new javax.swing.JTextField();
    private final javax.swing.JTextField tbENumber = new javax.swing.JTextField();
    private final javax.swing.JTextField tbWtPerUom = new javax.swing.JTextField("1");
    private final JCheckBox cbIsAdditive = new JCheckBox(
            "This ingredient is itself a food additive (preservative, colour, emulsifier, etc.)");
    private final JCheckBox cbIsCompound = new JCheckBox("Purchased compound ingredient (supplier recipe, no BOM here)");
    private final javax.swing.JTextArea taNotes = new javax.swing.JTextArea(3, 20);

    private final Map<String, JCheckBox> allergenBoxes = new LinkedHashMap<>();
    private final JPanel allergenPanel = new JPanel(new MigLayout("wrap 3", "[]10[]10[]", "[]"));

    private final DefaultTableModel subModel = new DefaultTableModel(
            new Object[]{"Sub-ingredient name", "E-number", "Allergen"}, 0) {
        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? Boolean.class : String.class;
        }
    };
    private final JTable subTable = new JTable(subModel);
    private final JPanel subPanel = new JPanel(new BorderLayout(0, 5));

    public IngredientPanel() {
        setLayout(new MigLayout("fill, insets 10", "[]10[grow,fill]", "[]5[]5[]10[]10[]10[grow,fill]"));

        add(new JLabel("Legal ingredient name"));
        add(tbLegalName, "wrap");

        add(new JLabel("Weight (g) per 1 unit of measure"));
        add(tbWtPerUom, "wrap");
        JLabel wtHint = new JLabel(
                "<html><div style='width:480px'>Leave as 1 if this item is already tracked by weight (kg/g) consistent with the rest of "
                + "the recipe. Set this for a <i>volume</i>-tracked ingredient (mL/L) so it sorts and sums "
                + "correctly against solids - e.g. water tracked in mL: 1; a lighter oil tracked in mL: ~0.92.</div></html>");
        wtHint.setForeground(java.awt.Color.GRAY);
        add(wtHint, "span 2, wrap");

        add(cbIsAdditive, "span 2, wrap");
        JLabel additiveHint = new JLabel(
                "<html><div style='width:480px'>Leave unchecked for a plain ingredient (sugar, flour, water, ...). Check this only "
                + "when this item <i>is</i> the additive itself - e.g. an item called \"Sodium Benzoate\" used "
                + "in a recipe. It will then appear in the printed ingredient list at its own position (sorted "
                + "by its own quantity, like any other ingredient), formatted per EU convention as "
                + "\"Category (E-number)\", e.g. \"Preservative (E211)\".</div></html>");
        additiveHint.setForeground(java.awt.Color.GRAY);
        add(additiveHint, "span 2, wrap");
        add(new JLabel("Additive category"));
        add(tbCategory, "wrap");
        add(new JLabel("E-number"));
        add(tbENumber, "wrap");
        tbCategory.setEnabled(false);
        tbENumber.setEnabled(false);
        cbIsAdditive.addActionListener(e -> {
            tbCategory.setEnabled(cbIsAdditive.isSelected());
            tbENumber.setEnabled(cbIsAdditive.isSelected());
        });

        for (ingData.ing_allergen_ref ref : ingData.getAllergenRef()) {
            JCheckBox cb = new JCheckBox(shortAllergenLabel(ref.allergen_code(), ref.allergen_desc()));
            cb.setToolTipText(ref.allergen_desc());
            allergenBoxes.put(ref.allergen_code(), cb);
            allergenPanel.add(cb);
        }
        add(wrapTitled("Allergens (EU Annex II)", allergenPanel), "span 2, growx, wrap");

        add(cbIsCompound, "span 2, wrap");

        subTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        JButton btnAddRow = new JButton("Add row");
        btnAddRow.addActionListener(e -> subModel.addRow(new Object[]{"", "", Boolean.FALSE}));
        JButton btnRemoveRow = new JButton("Remove row");
        btnRemoveRow.addActionListener(e -> {
            int row = subTable.getSelectedRow();
            if (row >= 0) {
                subModel.removeRow(row);
            }
        });
        JPanel subButtons = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
        subButtons.add(btnAddRow);
        subButtons.add(btnRemoveRow);
        subPanel.add(new JScrollPane(subTable), BorderLayout.CENTER);
        subPanel.add(subButtons, BorderLayout.SOUTH);
        subPanel.setVisible(false);
        add(wrapTitled("Supplier-declared sub-ingredients (used when ≥ 2% of finished product weight)", subPanel),
                "span 2, grow, wrap");

        cbIsCompound.addActionListener(e -> subPanel.setVisible(cbIsCompound.isSelected()));

        add(new JLabel("Notes"));
        add(new JScrollPane(taNotes), "span 2, grow");
    }

    private static JPanel wrapTitled(String title, JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    // EU Annex II short names for checkbox labels - allergen_desc carries the full legal
    // wording (shown as the tooltip instead) since a couple of entries (tree nuts' bracketed
    // examples, the sulphites concentration threshold) run 90+ characters and blow out the
    // MigLayout grid width if used as the visible label.
    private static final Map<String, String> ALLERGEN_SHORT_LABEL = Map.ofEntries(
            Map.entry("GLUTEN", "Cereals containing gluten"),
            Map.entry("CRUSTACEANS", "Crustaceans"),
            Map.entry("EGGS", "Eggs"),
            Map.entry("FISH", "Fish"),
            Map.entry("PEANUTS", "Peanuts"),
            Map.entry("SOYBEANS", "Soybeans"),
            Map.entry("MILK", "Milk"),
            Map.entry("NUTS", "Tree nuts"),
            Map.entry("CELERY", "Celery"),
            Map.entry("MUSTARD", "Mustard"),
            Map.entry("SESAME", "Sesame seeds"),
            Map.entry("SULPHITES", "Sulphur dioxide/sulphites"),
            Map.entry("LUPIN", "Lupin"),
            Map.entry("MOLLUSCS", "Molluscs"));

    private static String shortAllergenLabel(String code, String desc) {
        String known = ALLERGEN_SHORT_LABEL.get(code);
        if (known != null) {
            return known;
        }
        int paren = desc.indexOf(" and products");
        return paren > 0 ? desc.substring(0, paren) : desc;
    }

    public void clear() {
        tbLegalName.setText("");
        tbCategory.setText("");
        tbENumber.setText("");
        tbWtPerUom.setText("1");
        cbIsAdditive.setSelected(false);
        tbCategory.setEnabled(false);
        tbENumber.setEnabled(false);
        cbIsCompound.setSelected(false);
        subPanel.setVisible(false);
        taNotes.setText("");
        for (JCheckBox cb : allergenBoxes.values()) {
            cb.setSelected(false);
        }
        subModel.setRowCount(0);
    }

    public void loadData(String item) {
        clear();
        if (item == null || item.isBlank()) {
            return;
        }
        ingData.ing_mstr rec = ingData.getIngMstr(item);
        if (rec.m() != null && rec.m().length > 0 && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit)) {
            tbLegalName.setText(rec.ing_legalname());
            tbCategory.setText(rec.ing_category());
            tbENumber.setText(rec.ing_enumber());
            tbWtPerUom.setText(String.valueOf(rec.ing_wt_per_uom_g() <= 0 ? 1.0 : rec.ing_wt_per_uom_g()));
            boolean isAdditive = !rec.ing_category().isBlank() || !rec.ing_enumber().isBlank();
            cbIsAdditive.setSelected(isAdditive);
            tbCategory.setEnabled(isAdditive);
            tbENumber.setEnabled(isAdditive);
            cbIsCompound.setSelected("1".equals(rec.ing_iscompound()));
            subPanel.setVisible(cbIsCompound.isSelected());
            taNotes.setText(rec.ing_notes());
        }
        for (String code : ingData.getAllergenCodes(item)) {
            JCheckBox cb = allergenBoxes.get(code);
            if (cb != null) {
                cb.setSelected(true);
            }
        }
        for (ingData.ing_subingredient sub : ingData.getSubIngredients(item)) {
            subModel.addRow(new Object[]{sub.sub_name(), sub.sub_enumber(), "1".equals(sub.is_allergen())});
        }
    }

    public void saveData(String item) {
        if (item == null || item.isBlank()) {
            return;
        }
        String category = cbIsAdditive.isSelected() ? tbCategory.getText() : "";
        String enumber = cbIsAdditive.isSelected() ? tbENumber.getText() : "";
        double wtPerUom;
        try {
            wtPerUom = Double.parseDouble(tbWtPerUom.getText().trim());
        } catch (NumberFormatException nfe) {
            wtPerUom = 1.0;
        }
        ingData.ing_mstr rec = new ingData.ing_mstr(null, item, tbLegalName.getText(), category,
                enumber, cbIsCompound.isSelected() ? "1" : "0", "1", taNotes.getText(), wtPerUom <= 0 ? 1.0 : wtPerUom);
        ingData.addUpdateIngMstr(rec);

        ArrayList<String> codes = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : allergenBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                codes.add(entry.getKey());
            }
        }
        ingData.setAllergenCodes(item, codes);

        ArrayList<ingData.ing_subingredient> subs = new ArrayList<>();
        for (int i = 0; i < subModel.getRowCount(); i++) {
            String name = String.valueOf(subModel.getValueAt(i, 0));
            if (name.isBlank()) {
                continue;
            }
            String subEnumber = String.valueOf(subModel.getValueAt(i, 1));
            boolean isAllergen = Boolean.TRUE.equals(subModel.getValueAt(i, 2));
            subs.add(new ingData.ing_subingredient(null, item, i, name, subEnumber.equals("null") ? "" : subEnumber,
                    isAllergen ? "1" : "0"));
        }
        ingData.setSubIngredients(item, subs);
    }
}
