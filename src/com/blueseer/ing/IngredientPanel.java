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
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ingredient Data" tab for ItemMaint: EU/Irish FIC regulatory metadata for
 * an item used as a raw material/ingredient elsewhere (legal name, additive
 * category/E-number, allergens, and - for a purchased item that is itself a
 * compound ingredient BlueSeer has no BOM visibility into - the supplier's
 * declared sub-ingredient breakdown), plus QUID/reconstitution settings for
 * an item that is itself a finished good with its own BOM. ItemMaint calls
 * loadData()/saveData() alongside its own item_mstr load/save, the same way
 * it already does for attachments (see ItemMaint.getRecord/addRecord/
 * updateRecord).
 *
 * Explanatory text lives in tooltips behind a small help icon next to each
 * field rather than as permanently-visible paragraphs (see {@link
 * #helpIcon}), so the form stays a compact two-column grid instead of a
 * tall single-column stack of label/field/paragraph/label/field/...
 */
public class IngredientPanel extends JPanel {

    // Food vs. packaging/non-food raw material (ing_mstr.ing_material_type) - drives whether
    // this item is even eligible to appear in an ingredient list/nutrition rollup at all (see
    // IngredientLabelEngine.isFoodMaterial). No control lives on this panel for it - ItemMaint
    // owns a single "Food Item" checkbox on its Main tab that shows/hides this
    // whole tab (and Nutrition Data) rather than disabling sections within an always-visible
    // tab, so a non-food ERP catalog isn't cluttered with food-specific tabs by default. ItemMaint
    // calls setTrackedAsFood()/isTrackedAsFood() around loadData()/saveData() to keep this field
    // in sync with its own checkbox without a second writer touching ing_mstr's row.
    private String materialType = ingData.ing_mstr.FOOD;

    private final javax.swing.JTextField tbLegalName = new javax.swing.JTextField();
    private final javax.swing.JTextField tbCategory = new javax.swing.JTextField();
    private final javax.swing.JTextField tbENumber = new javax.swing.JTextField();
    private final javax.swing.JTextField tbWtPerUom = new javax.swing.JTextField("1");
    private final JCheckBox cbIsAdditive = new JCheckBox("This ingredient is itself a food additive");
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

    private final JCheckBox cbMoistLoss = new JCheckBox("This item's production process loses moisture");

    // Storage/usage instructions (FIC Article 9(1)(g)/(j)) - mandatory particulars
    // in their own right, not specific to nutrition, but living in this same tab
    // since it's already this item's "regulatory label data" home (same ing_mstr
    // row as legal name/category/etc, so it stays here rather than on the
    // Nutrition Data tab - splitting it there would race NutritionPanel's own
    // save over shared ing_mstr columns).
    private final javax.swing.JTextArea taStorageInstr = new javax.swing.JTextArea(2, 20);
    private final javax.swing.JTextArea taUsageInstr = new javax.swing.JTextArea(2, 20);

    // BOM-driven QUID + reconstitution table: rows are THIS item's own
    // flattened ingredients (see IngredientLabelEngine.getBomIngredients),
    // never free-typed, so what can be flagged always matches the recipe.
    private final DefaultTableModel bomModel = new DefaultTableModel(
            new Object[]{"Item code", "Description", "Needs QUID %", "Reconstitutes into"}, 0) {
        @Override
        public Class<?> getColumnClass(int col) {
            return col == 2 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 2 || col == 3;
        }
    };
    private final JTable bomTable = new JTable(bomModel);
    private final JComboBox<String> reconCombo = new JComboBox<>();
    private final JPanel bomPanel = new JPanel(new BorderLayout(0, 5));

    public IngredientPanel() {
        // A plain content panel inside a JScrollPane, not this outer JPanel directly -
        // this tab grew substantially with the nutrition declaration + storage/usage
        // sections below, well past what fits in the tab area unscrolled.
        setLayout(new BorderLayout());
        JPanel content = new JPanel(new MigLayout("insets 10", "[]5[grow,fill]"));
        add(new JScrollPane(content), BorderLayout.CENTER);

        content.add(new JLabel("Legal ingredient name"));
        content.add(tbLegalName, "wrap");

        content.add(labelWithHelp("Weight (g) per 1 unit of measure",
                "Leave as 1 if this item is already tracked by weight (kg/g) consistent with the rest of "
                + "the recipe. Set this for a <i>volume</i>-tracked ingredient (mL/L) so it sorts and sums "
                + "correctly against solids - e.g. water tracked in mL: 1; a lighter oil tracked in mL: ~0.92."));
        content.add(tbWtPerUom, "wrap");

        content.add(cbIsAdditive, "wrap");
        cbIsAdditive.setToolTipText(helpHtml(
                "Leave unchecked for a plain ingredient (sugar, flour, water, ...). Check this only when this "
                + "item <i>is</i> the additive itself - e.g. an item called \"Sodium Benzoate\" used in a recipe. "
                + "It will then appear in the printed ingredient list at its own position (sorted by its own "
                + "quantity, like any other ingredient), formatted per EU convention as \"Category (E-number)\", "
                + "e.g. \"Preservative (E211)\"."));
        content.add(new JLabel("Additive category"));
        content.add(tbCategory, "wrap");
        content.add(new JLabel("E-number"));
        content.add(tbENumber, "wrap");
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
        content.add(wrapTitled("Allergens (EU Annex II)", allergenPanel), "span 2, growx, wrap");

        content.add(cbIsCompound, "wrap");
        cbIsCompound.setToolTipText(helpHtml(
                "Check only for a <i>purchased</i> item that is itself a compound ingredient BlueSeer has no BOM "
                + "for (e.g. bought-in chocolate chips). It's declared under its own name, followed by its "
                + "supplier-declared sub-ingredients in brackets - omitted only when under 2% of the finished "
                + "product and none of its sub-ingredients is an allergen. An in-house sub-recipe with its own "
                + "BOM doesn't need this - it's always fully expanded automatically."));

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
        subPanel.setPreferredSize(new java.awt.Dimension(10, 140));
        subPanel.setVisible(false);
        content.add(wrapTitled("Supplier-declared sub-ingredients", subPanel), "span 2, grow, wrap");

        cbIsCompound.addActionListener(e -> subPanel.setVisible(cbIsCompound.isSelected()));

        content.add(cbMoistLoss, "wrap");
        cbMoistLoss.setToolTipText(helpHtml(
                "Only relevant when this item is itself printed as a finished-good label (e.g. this cake, not an "
                + "ingredient used inside something else). Leave unchecked for a cold/no-cook product (QUID % is "
                + "calculated against the total raw ingredient weight). Check it for a cooked/baked/dried product "
                + "(QUID % is instead calculated against this item's Net Weight, since the raw mix no longer "
                + "reflects what's actually in the finished product)."));

        bomTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        bomTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        bomTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(reconCombo) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                    int row, int col) {
                String selfCode = String.valueOf(bomModel.getValueAt(row, 0));
                reconCombo.removeAllItems();
                reconCombo.addItem("");
                for (int i = 0; i < bomModel.getRowCount(); i++) {
                    String code = String.valueOf(bomModel.getValueAt(i, 0));
                    if (!code.equals(selfCode)) {
                        reconCombo.addItem(code);
                    }
                }
                reconCombo.setSelectedItem(value == null ? "" : value);
                return reconCombo;
            }
        });
        bomPanel.add(new JScrollPane(bomTable), BorderLayout.CENTER);
        bomPanel.setPreferredSize(new java.awt.Dimension(10, 160));
        bomPanel.setVisible(false);
        JLabel bomTitle = new JLabel("This item's ingredients (QUID % / reconstitution)");
        JPanel bomTitleRow = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
        bomTitleRow.add(bomTitle);
        bomTitleRow.add(helpIcon(
                "Lists this item's own flattened BOM ingredients - only shown when this item is a finished/"
                + "manufactured good with a recipe of its own. <b>Needs QUID %</b>: check for an ingredient named "
                + "in this product's name, emphasized on the pack, or characterizing it (e.g. the ham in a ham "
                + "sandwich) - its percentage is then printed on the label. <b>Reconstitutes into</b>: for a "
                + "diluent (e.g. water) used solely to rehydrate a concentrated/dehydrated ingredient in "
                + "<i>this</i> recipe (e.g. milk powder) - its weight is folded into the chosen ingredient instead "
                + "of appearing as its own list entry. Both are specific to this one product; the same raw "
                + "material may be flagged differently (or not at all) on a different product's label."));
        JPanel bomWrapper = new JPanel(new BorderLayout());
        bomWrapper.add(bomTitleRow, BorderLayout.NORTH);
        bomWrapper.add(bomPanel, BorderLayout.CENTER);
        content.add(bomWrapper, "span 2, grow, wrap");

        // ------------------------------------------------------------
        // Storage/usage instructions (FIC Article 9(1)(g)/(j)) - mandatory
        // particulars in their own right, applied to every label via the
        // $STORAGE/$USAGE tokens, independent of the nutrition panel (Nutrition
        // Data tab, see NutritionPanel).
        // ------------------------------------------------------------
        JPanel storageUsagePanel = new JPanel(new MigLayout("insets 0", "[]5[grow,fill]", "[]"));
        storageUsagePanel.add(new JLabel("Storage conditions"));
        storageUsagePanel.add(new JScrollPane(taStorageInstr), "grow, wrap");
        storageUsagePanel.add(new JLabel("Usage instructions"));
        storageUsagePanel.add(new JScrollPane(taUsageInstr), "grow, wrap");
        content.add(wrapTitled("Storage & Usage Instructions", storageUsagePanel), "span 2, grow, wrap");

        content.add(new JLabel("Notes"));
        content.add(new JScrollPane(taNotes), "span 2, grow, wrap");
    }

    /** True unless ItemMaint has flagged this item as Packaging/Non-Food via {@link #setTrackedAsFood}. */
    public boolean isTrackedAsFood() {
        return ingData.ing_mstr.FOOD.equals(materialType);
    }

    /** Called by ItemMaint right before {@link #saveData} so its own Main-tab checkbox is what
     *  actually ends up in ing_mstr - this panel has no control of its own for the field anymore. */
    public void setTrackedAsFood(boolean tracked) {
        materialType = tracked ? ingData.ing_mstr.FOOD : ingData.ing_mstr.PACKAGING;
    }

    /** A field label followed by a small help icon carrying the long-form explanation as a tooltip. */
    private static JPanel labelWithHelp(String label, String helpHtmlBody) {
        JPanel row = new JPanel(new MigLayout("insets 0", "[]3[]", "[]"));
        row.add(new JLabel(label));
        row.add(helpIcon(helpHtmlBody));
        return row;
    }

    private static JLabel helpIcon(String helpHtmlBody) {
        JLabel icon = new JLabel(FontIcon.of(MaterialDesignH.HELP_CIRCLE_OUTLINE, 15, java.awt.Color.GRAY));
        icon.setToolTipText(helpHtml(helpHtmlBody));
        icon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return icon;
    }

    private static String helpHtml(String body) {
        return "<html><div style='width:360px'>" + body + "</div></html>";
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
        materialType = ingData.ing_mstr.FOOD;
        tbLegalName.setText("");
        tbCategory.setText("");
        tbENumber.setText("");
        tbWtPerUom.setText("1");
        cbIsAdditive.setSelected(false);
        cbIsCompound.setSelected(false);
        cbMoistLoss.setSelected(false);
        taNotes.setText("");
        for (JCheckBox cb : allergenBoxes.values()) {
            cb.setSelected(false);
        }
        subModel.setRowCount(0);
        bomModel.setRowCount(0);
        bomPanel.setVisible(false);
        taStorageInstr.setText("");
        taUsageInstr.setText("");
    }

    public void loadData(String item) {
        clear();
        if (item == null || item.isBlank()) {
            return;
        }
        ingData.ing_mstr rec = ingData.getIngMstr(item);
        if (rec.m() != null && rec.m().length > 0 && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit)) {
            materialType = ingData.ing_mstr.PACKAGING.equalsIgnoreCase(rec.ing_material_type())
                    ? ingData.ing_mstr.PACKAGING : ingData.ing_mstr.FOOD;
            tbLegalName.setText(rec.ing_legalname());
            tbCategory.setText(rec.ing_category());
            tbENumber.setText(rec.ing_enumber());
            tbWtPerUom.setText(String.valueOf(rec.ing_wt_per_uom_g() <= 0 ? 1.0 : rec.ing_wt_per_uom_g()));
            cbIsAdditive.setSelected(!rec.ing_category().isBlank() || !rec.ing_enumber().isBlank());
            tbCategory.setEnabled(cbIsAdditive.isSelected());
            tbENumber.setEnabled(cbIsAdditive.isSelected());
            cbIsCompound.setSelected("1".equals(rec.ing_iscompound()));
            subPanel.setVisible(cbIsCompound.isSelected());
            taNotes.setText(rec.ing_notes());
            taStorageInstr.setText(rec.ing_storage_instr());
            taUsageInstr.setText(rec.ing_usage_instr());
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
        cbMoistLoss.setSelected(ingData.getMoistLoss(item));

        List<IngredientLabelEngine.BomIngredient> bomList = new IngredientLabelEngine().getBomIngredients(item);
        bomPanel.setVisible(!bomList.isEmpty());
        if (!bomList.isEmpty()) {
            java.util.Set<String> quidSet = new java.util.HashSet<>(ingData.getQuidItemCodes(item));
            Map<String, String> reconMap = ingData.getReconMap(item);
            for (IngredientLabelEngine.BomIngredient bi : bomList) {
                bomModel.addRow(new Object[]{bi.item(), bi.desc(), quidSet.contains(bi.item()),
                        reconMap.getOrDefault(bi.item(), "")});
            }
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
                enumber, cbIsCompound.isSelected() ? "1" : "0", "1", taNotes.getText(), wtPerUom <= 0 ? 1.0 : wtPerUom,
                taStorageInstr.getText(), taUsageInstr.getText(), materialType);
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

        ingData.setMoistLoss(item, cbMoistLoss.isSelected());

        if (bomTable.isEditing()) {
            bomTable.getCellEditor().stopCellEditing();
        }
        ArrayList<String> quidItems = new ArrayList<>();
        Map<String, String> reconMap = new LinkedHashMap<>();
        for (int i = 0; i < bomModel.getRowCount(); i++) {
            String code = String.valueOf(bomModel.getValueAt(i, 0));
            boolean needsQuid = Boolean.TRUE.equals(bomModel.getValueAt(i, 2));
            String target = String.valueOf(bomModel.getValueAt(i, 3));
            if (needsQuid) {
                quidItems.add(code);
            }
            if (!target.isBlank() && !target.equals("null")) {
                reconMap.put(code, target);
            }
        }
        ingData.setQuidItemCodes(item, quidItems);
        ingData.setReconMap(item, reconMap);
    }
}
