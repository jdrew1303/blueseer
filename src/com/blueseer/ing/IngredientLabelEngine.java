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

import static bsmf.MainFrame.bslog;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recursively expands a finished good's BOM into a deduplicated, descending-
 * weight ingredient list formatted per EU FIC (Regulation (EU) 1169/2011) and
 * the common Irish/EU mandatory-warning rules, then attaches batch/best-
 * before information.
 *
 * IMPORTANT: the {@code warn_rule} table and the 2%/allergen compound-
 * ingredient logic below are a starting-point implementation, not a legal
 * sign-off - a qualified food-labelling/regulatory professional should
 * review this against the client's actual products before it goes live on
 * real packaging.
 *
 * Two different things both get called "compound ingredients" here, and
 * BlueSeer treats them very differently:
 *  - An in-house sub-recipe with its own BOM (pbm_mstr.ps_type = 'M', e.g.
 *    a dough or icing made on-site) - BlueSeer has full recipe visibility,
 *    so it's always fully expanded into the flat list. That's always legally
 *    permitted (more disclosure than the law requires is never a violation).
 *  - A purchased item that is itself a compound ingredient BlueSeer has no
 *    BOM for (ing_mstr.ing_iscompound, e.g. bought-in chocolate spread) -
 *    per FIC Annex VII Part E this is declared under its own name at its own
 *    position in the list, immediately followed by its declared
 *    sub-ingredients in brackets, e.g. "Chocolate Spread (Sugar, Vegetable
 *    Oil, Cocoa Powder, ...)" - NOT flattened/merged into the main list.
 *    The bracket is only omittable when the compound is under 2% of the
 *    finished product AND none of its declared sub-ingredients is an
 *    allergen.
 *
 * Every leaf ingredient's quantity is normalized to grams via
 * ing_mstr.ing_wt_per_uom_g ("grams per 1 unit of this item's own UOM",
 * default 1) before summing/sorting, so a liquid tracked by volume (e.g.
 * water in mL) compares correctly against solids tracked by weight - this
 * needs to be set explicitly per ingredient; it is not inferred from the
 * item's unit-of-measure code.
 *
 * The 2% threshold is computed against the finished good's item_mstr.it_net_wt
 * (already-populated net/retail weight), not a sum of the recipe's pre-loss
 * BOM inputs - per FIC's own QUID rule, a percentage "shall correspond to
 * the quantity of the ingredient(s) used, related to the finished product",
 * and it_net_wt is the one place BlueSeer already records that finished
 * weight (net weight already used for shipping/packaging). This sidesteps
 * needing to model bake/process loss ourselves. It_net_wt is assumed to be
 * expressed in grams; if it isn't set at all, the engine can't safely tell
 * whether a compound ingredient is under 2%, so it conservatively always
 * shows the bracket breakdown and records that in internalNotes.
 *
 * <h2>QUID (Quantitative Ingredient Declaration, FIC Annex VIII / FSAI)</h2>
 * An ingredient flagged via {@code ing_quid} for the finished item being
 * labeled (named in the product name, emphasized on the pack, or
 * characterizing the product) gets its percentage appended in the
 * ingredient list, using one of two methods depending on
 * {@code item_mstr.it_moistloss} for the finished item:
 * <ul>
 *   <li><b>Mixing Bowl Method</b> (it_moistloss = 0, e.g. a sandwich or dry
 *       mix that doesn't change weight in processing): the ingredient's raw
 *       weight divided by the sum of all raw ingredient weights (this
 *       engine's own flattened BOM total) - "Ham (20%)".</li>
 *   <li><b>Finished-Weight Method</b> (it_moistloss = 1, e.g. baked, cooked,
 *       or dried, so the product loses moisture): the ingredient's raw
 *       weight divided by item_mstr.it_net_wt instead, since the raw mixing-
 *       bowl total no longer reflects what's actually in the finished
 *       product - "Strawberries (75%)" even though they were only 60% of
 *       the pre-bake mix.</li>
 * </ul>
 * If the finished-weight method produces over 100% (a heavily dried/reduced
 * product, e.g. cooked ham), EU law forbids printing a percentage over
 * 100% as confusing, so the declaration switches to
 * "Prepared with 122g of pork per 100g of finished product." instead of a
 * bracketed percentage - this can only happen with the finished-weight
 * method, since the mixing-bowl method's denominator already includes the
 * ingredient itself and so can never exceed 100%.
 *
 * Reconstituted/dehydrated ingredients (FSAI: QUID must use the weight
 * <i>after</i> rehydration, not the dry/concentrated weight): {@code
 * ing_recon} maps, per FINISHED item, a diluent's item code to the item
 * code its weight should fold into - e.g. for CAKE001 specifically, "water"
 * folds into "milk powder". This is scoped to one finished item's recipe
 * (not a global property of the water item), because the same raw
 * material can be a plain ingredient in one recipe, a minor flavoring in
 * another, and the reconstitution diluent for something else entirely in
 * a third - exactly the same reasoning as {@code ing_quid} being scoped
 * per finished item rather than living on the ingredient's own master
 * record. This engine folds the diluent's flattened BOM weight directly
 * into the target's accumulated total during flattening (see {@link
 * #flattenRecursive}), so the diluent never appears as its own
 * ingredient-list entry - its weight silently becomes part of the
 * target's. Both {@code ing_quid} and {@code ing_recon} are maintained
 * from ItemMaint's Ingredient Data tab as a single table of the finished
 * item's own flattened BOM ingredients (see {@link #getBomIngredients}),
 * not free-text item-code entry, so what can be selected always matches
 * what's actually in the recipe.
 */
public class IngredientLabelEngine {

    /** One EU-FIC-mandated-emphasis-aware run of ingredient list text, with
     *  an optional bracketed sub-ingredient breakdown (compound ingredients
     *  only - see class javadoc). */
    public record Segment(String text, boolean isAllergen, List<Segment> bracketed) {
        public Segment(String text, boolean isAllergen) {
            this(text, isAllergen, List.of());
        }
    }

    public record IngredientLabelResult(List<Segment> segments, List<String> warnings,
            String lotNumber, String bestBeforeDate, List<String> internalNotes) {

        /** Ingredient list only, HTML markup="html" ready (for JasperReports). */
        public String toHtmlIngredientList() {
            StringBuilder sb = new StringBuilder();
            appendSegments(sb, segments, true);
            return sb.toString();
        }

        /** Ingredient list only, plain text with allergens upper-cased (for ZPL). */
        public String toPlainIngredientList() {
            StringBuilder sb = new StringBuilder();
            appendSegments(sb, segments, false);
            return sb.toString();
        }

        /** Mandatory warnings only, one sentence each, plain text. */
        public String toPlainWarnings() {
            StringBuilder sb = new StringBuilder();
            for (String w : warnings) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(w).append(".");
            }
            return sb.toString();
        }

        public String toHtml() {
            StringBuilder sb = new StringBuilder(toHtmlIngredientList());
            appendWarnings(sb, true);
            return sb.toString();
        }

        public String toPlainText() {
            StringBuilder sb = new StringBuilder(toPlainIngredientList());
            appendWarnings(sb, false);
            return sb.toString();
        }

        private void appendWarnings(StringBuilder sb, boolean html) {
            if (!warnings.isEmpty()) {
                sb.append(".");
                for (String w : warnings) {
                    sb.append(" ").append(html ? escapeHtml(w) : w).append(".");
                }
            }
        }

        private static void appendSegments(StringBuilder sb, List<Segment> segs, boolean html) {
            for (int i = 0; i < segs.size(); i++) {
                Segment s = segs.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(renderOne(s, html));
                if (!s.bracketed().isEmpty()) {
                    sb.append(" (");
                    for (int j = 0; j < s.bracketed().size(); j++) {
                        if (j > 0) {
                            sb.append(", ");
                        }
                        sb.append(renderOne(s.bracketed().get(j), html));
                    }
                    sb.append(")");
                }
            }
        }

        private static String renderOne(Segment s, boolean html) {
            if (html) {
                return s.isAllergen() ? "<b>" + escapeHtml(s.text()) + "</b>" : escapeHtml(s.text());
            }
            return s.isAllergen() ? s.text().toUpperCase(Locale.ROOT) : s.text();
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static final char BOLD_START = '';
        private static final char BOLD_END = '';


        /**
         * The entire dynamic section of the label below the fixed item-
         * description/"Ingredients:" header: the word-wrapped bold ingredient
         * list, a static allergen-bold disclaimer, any mandatory additive
         * warnings (only emitted if non-empty, so a simple recipe doesn't
         * waste label space on a blank warnings block), a divider line, then
         * a two-column row - Net Weight/Best Before/Batch-Lot stacked on the
         * left, the barcode with its human-readable interpretation line on
         * the right, so it isn't just sitting in unused whitespace below.
         *
         * Every Y position from the disclaimer onward is computed from where
         * the ingredient list actually finished wrapping, not a fixed guess,
         * so a short recipe doesn't leave a large dead gap and a long one
         * doesn't run into the row below it (barring a genuinely enormous
         * ingredient list, which would still run past the bottom of the
         * physical label - the same limit any fixed-size label has). The
         * returned {@link ZplTextUtils.Rendered#endY} lets whatever section
         * comes after this one (nutrition panel, storage/usage) start right
         * there too, chaining the same way internally.
         */
        public ZplTextUtils.Rendered toZplLabelBody(int x, int y, int width, int fontHeight, int lineSpacing,
                String itemNumber, String netWeight) {
            StringBuilder zpl = new StringBuilder();
            int cy = renderIngredientWords(zpl, x, y, width, fontHeight, lineSpacing, "Ingredients: ");

            cy += 16;
            int disclaimerFontH = 20;
            for (String dl : ZplTextUtils.wrapPlain("Allergens are shown in BOLD within the ingredients list.", disclaimerFontH, width)) {
                ZplTextUtils.appendZplField(zpl, x, cy, disclaimerFontH, dl, false);
                cy += disclaimerFontH + 4;
            }

            if (!warnings.isEmpty()) {
                cy += 10;
                for (String wl : ZplTextUtils.wrapPlain(toPlainWarnings(), disclaimerFontH, width)) {
                    ZplTextUtils.appendZplField(zpl, x, cy, disclaimerFontH, wl, false);
                    cy += disclaimerFontH + 4;
                }
            }

            cy += 14;
            ZplTextUtils.appendDivider(zpl, x, cy, width);
            cy += 20;

            int rowTop = cy;
            int rowFontH = 26;
            int rowHeight = 34;
            ZplTextUtils.appendZplField(zpl, x, cy, rowFontH, "Net Weight:", false);
            ZplTextUtils.appendZplField(zpl, x + 160, cy, rowFontH, netWeight, false);
            cy += rowHeight;
            ZplTextUtils.appendZplField(zpl, x, cy, rowFontH, "Best Before:", false);
            ZplTextUtils.appendZplField(zpl, x + 160, cy, rowFontH, bestBeforeDate, false);
            cy += rowHeight;
            ZplTextUtils.appendZplField(zpl, x, cy, rowFontH, "Batch/Lot No:", false);
            ZplTextUtils.appendZplField(zpl, x + 160, cy, rowFontH, lotNumber, false);
            cy += rowHeight;

            int barcodeX = x + 460;
            int barcodeHeight = 90;
            zpl.append("^BY2,3,").append(barcodeHeight)
                    .append("^FO").append(barcodeX).append(",").append(rowTop)
                    .append("^BCN,,Y,N^FD>:").append(ZplTextUtils.zplEscape(itemNumber)).append("^FS");

            return new ZplTextUtils.Rendered(zpl.toString(), cy);
        }

        /**
         * Word-wraps and renders the bold-aware ingredient list into zpl,
         * returning the Y just past the last line. The label (e.g.
         * "Ingredients: ") is rendered inline before the first word instead
         * of on its own line/field, so the list starts on the same row -
         * label space on the physical labels is tight enough that a whole
         * blank line just for the header is wasteful. Only the first line
         * shares the row with the label; wrapped lines still reset to x.
         */
        private int renderIngredientWords(StringBuilder zpl, int x, int y, int width, int fontHeight,
                int lineSpacing, String label) {
            StringBuilder marked = new StringBuilder();
            appendSegmentsMarked(marked, segments);
            double spaceWidth = ZplTextUtils.measureWidth(" ", fontHeight);

            int cx = x;
            int cy = y;
            if (label != null && !label.isEmpty()) {
                ZplTextUtils.appendZplField(zpl, x, y, fontHeight, label, false);
                cx = x + (int) Math.ceil(ZplTextUtils.measureWidth(label, fontHeight));
            }
            StringBuilder word = new StringBuilder();
            boolean wordBold = false;
            boolean inBold = false;
            for (int i = 0; i <= marked.length(); i++) {
                char c = i < marked.length() ? marked.charAt(i) : ' ';
                if (c == BOLD_START) {
                    inBold = true;
                    continue;
                }
                if (c == BOLD_END) {
                    inBold = false;
                    continue;
                }
                if (c == ' ') {
                    if (word.length() > 0) {
                        int wordWidth = (int) Math.ceil(ZplTextUtils.measureWidth(word.toString(), fontHeight));
                        if (cx > x && cx + wordWidth > x + width) {
                            cx = x;
                            cy += fontHeight + lineSpacing;
                        }
                        ZplTextUtils.appendZplField(zpl, cx, cy, fontHeight, word.toString(), wordBold);
                        cx += wordWidth + (int) Math.ceil(spaceWidth);
                        word.setLength(0);
                        wordBold = false;
                    }
                } else {
                    if (word.length() == 0) {
                        wordBold = inBold;
                    }
                    word.append(c);
                }
            }
            return cy + fontHeight;
        }

        private static void appendSegmentsMarked(StringBuilder sb, List<Segment> segs) {
            for (int i = 0; i < segs.size(); i++) {
                Segment s = segs.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                appendOneMarked(sb, s);
                if (!s.bracketed().isEmpty()) {
                    sb.append(" (");
                    for (int j = 0; j < s.bracketed().size(); j++) {
                        if (j > 0) {
                            sb.append(", ");
                        }
                        appendOneMarked(sb, s.bracketed().get(j));
                    }
                    sb.append(")");
                }
            }
        }

        private static void appendOneMarked(StringBuilder sb, Segment s) {
            if (s.isAllergen()) {
                sb.append(BOLD_START).append(s.text()).append(BOLD_END);
            } else {
                sb.append(s.text());
            }
        }
    }

    /** Package-visible (not private) so {@link NutritionLabelEngine} can reuse the same BOM walk. */
    record BomLine(String child, String type, double qtyPer) {
    }

    private static final double COMPOUND_INGREDIENT_THRESHOLD_PCT = 2.0;

    public IngredientLabelResult generate(String finishedItem, String lotNumber, String bestBeforeDate) {
        Map<String, Double> flatQty = new LinkedHashMap<>();
        List<String> internalNotes = new ArrayList<>();
        double finishedWeightG = 0;
        Map<String, String> reconMap = ingData.getReconMap(finishedItem);
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            flattenRecursive(con, finishedItem, 1.0, flatQty, reconMap);
            finishedWeightG = getFinishedNetWeightG(con, finishedItem);
        } catch (SQLException s) {
            bslog(s);
        }

        boolean weightKnown = finishedWeightG > 0;
        if (!weightKnown) {
            internalNotes.add("item_mstr.it_net_wt is not set for " + finishedItem + " - can't compute the 2% "
                    + "compound-ingredient threshold, so every compound ingredient's sub-ingredients are shown "
                    + "in full rather than risk under-disclosing.");
        }

        boolean moistureLoss = ingData.getMoistLoss(finishedItem);
        java.util.Set<String> quidItems = new java.util.HashSet<>(ingData.getQuidItemCodes(finishedItem));
        double totalRawWeightG = flatQty.values().stream().mapToDouble(Double::doubleValue).sum();
        double quidDenominatorG = moistureLoss ? finishedWeightG : totalRawWeightG;
        if (!quidItems.isEmpty() && quidDenominatorG <= 0) {
            internalNotes.add((moistureLoss ? "item_mstr.it_net_wt" : "the flattened BOM total")
                    + " is not available for " + finishedItem + " - can't compute QUID %, so the flagged "
                    + "QUID ingredient(s) are shown without a percentage.");
            quidItems = java.util.Set.of();
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(flatQty.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Segment> segments = new ArrayList<>();
        java.util.Set<String> triggeredAllergenCodes = new java.util.LinkedHashSet<>();
        java.util.Set<String> triggeredENumbers = new java.util.LinkedHashSet<>();
        java.util.Set<String> triggeredCategories = new java.util.LinkedHashSet<>();

        for (Map.Entry<String, Double> entry : sorted) {
            String item = entry.getKey();
            double qty = entry.getValue();
            ingData.ing_mstr rec = ingData.getIngMstr(item);
            boolean found = rec.m() != null && rec.m().length > 0 && rec.m()[0].equals(com.blueseer.utl.BlueSeerUtils.SuccessBit);
            String legalName = found && !rec.ing_legalname().isBlank() ? rec.ing_legalname() : item;
            String category = found ? rec.ing_category() : "";
            String enumber = found ? rec.ing_enumber() : "";
            List<String> allergenCodes = ingData.getAllergenCodes(item);
            boolean isAllergen = !allergenCodes.isEmpty();
            boolean isCompound = found && "1".equals(rec.ing_iscompound());

            String mainText = (!category.isBlank() && !enumber.isBlank()) ? category + " (" + enumber + ")"
                    : (!category.isBlank() ? category : legalName);

            if (quidItems.contains(item)) {
                double quidPct = (qty / quidDenominatorG) * 100.0;
                mainText = quidPct > 100.0
                        ? "Prepared with " + formatQuidNumber(quidPct) + "g of " + legalName.toLowerCase(Locale.ROOT)
                                + " per 100g of finished product"
                        : mainText + " (" + formatQuidNumber(quidPct) + "%)";
            }

            List<Segment> bracket = List.of();
            if (isCompound) {
                List<ingData.ing_subingredient> subs = ingData.getSubIngredients(item);
                boolean hasAllergenSub = subs.stream().anyMatch(s -> "1".equals(s.is_allergen()));
                double pctOfFinished = weightKnown ? (qty / finishedWeightG) * 100.0 : Double.NaN;
                boolean showBracket = !subs.isEmpty() && (!weightKnown || pctOfFinished >= COMPOUND_INGREDIENT_THRESHOLD_PCT || hasAllergenSub);
                if (showBracket) {
                    List<Segment> subSegs = new ArrayList<>();
                    for (ingData.ing_subingredient sub : subs) {
                        String subText = sub.sub_enumber().isBlank() ? sub.sub_name() : sub.sub_name() + " (" + sub.sub_enumber() + ")";
                        subSegs.add(new Segment(subText, "1".equals(sub.is_allergen())));
                        if (!sub.sub_enumber().isBlank()) {
                            triggeredENumbers.add(sub.sub_enumber().toUpperCase(Locale.ROOT));
                        }
                    }
                    bracket = subSegs;
                }
            }

            segments.add(new Segment(mainText, isAllergen, bracket));

            triggeredAllergenCodes.addAll(allergenCodes);
            if (!enumber.isBlank()) {
                triggeredENumbers.add(enumber.toUpperCase(Locale.ROOT));
            }
            if (!category.isBlank()) {
                triggeredCategories.add(category);
            }
        }

        List<String> warnings = evaluateWarnings(triggeredAllergenCodes, triggeredENumbers, triggeredCategories);

        return new IngredientLabelResult(segments, warnings, lotNumber, bestBeforeDate, internalNotes);
    }

    /** Every worked QUID example in FSAI guidance rounds to a whole number (20%, 75%, 122%). */
    private static String formatQuidNumber(double pct) {
        return String.valueOf(Math.round(pct));
    }

    private List<String> evaluateWarnings(java.util.Set<String> allergenCodes, java.util.Set<String> enumbers,
            java.util.Set<String> categories) {
        List<String> warnings = new ArrayList<>();
        for (ingData.warn_rule rule : ingData.getWarnRules()) {
            boolean triggered = switch (rule.trigger_type()) {
                case "ALLERGEN_CODE" -> allergenCodes.contains(rule.trigger_value().toUpperCase(Locale.ROOT));
                case "E_NUMBER" -> enumbers.contains(rule.trigger_value().toUpperCase(Locale.ROOT));
                case "CATEGORY" -> categories.stream().anyMatch(c -> c.equalsIgnoreCase(rule.trigger_value()));
                default -> false;
            };
            if (triggered) {
                warnings.add(rule.warning_text());
            }
        }
        return warnings;
    }

    /** Package-visible (not private) so {@link NutritionLabelEngine} can reuse the same BOM walk
     *  for its own per-nutrient rollup instead of duplicating the recursion. */
    void flattenRecursive(Connection con, String item, double qtyPerUnit, Map<String, Double> flatQty,
            Map<String, String> reconMap) throws SQLException {
        for (BomLine line : getBomLines(con, item)) {
            double childQty = qtyPerUnit * line.qtyPer();
            if (line.type().equalsIgnoreCase("M")) {
                flattenRecursive(con, line.child(), childQty, flatQty, reconMap);
            } else {
                double gramsPerUom = getWtPerUomG(con, line.child());
                String key = reconMap.getOrDefault(line.child(), line.child());
                flatQty.merge(key, childQty * gramsPerUom, Double::sum);
            }
        }
    }

    /** One row of {@link #getBomIngredients} - a raw, unfolded leaf ingredient item code and its description. */
    public record BomIngredient(String item, String desc) {
    }

    /**
     * The finished item's own flattened BOM ingredients (item code +
     * description, sorted by descending raw weight, deduplicated, no
     * reconstitution folding applied) - this is what ItemMaint's Ingredient
     * Data tab shows as a checkbox/dropdown table so QUID and reconstitution
     * targets are always picked from what's actually in the recipe, never
     * free-typed. Returns an empty list for an item with no BOM of its own
     * (a raw material/purchased ingredient, not a finished/manufactured good).
     */
    public List<BomIngredient> getBomIngredients(String finishedItem) {
        Map<String, Double> flatQty = new LinkedHashMap<>();
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            flattenRecursive(con, finishedItem, 1.0, flatQty, Map.of());
        } catch (SQLException s) {
            bslog(s);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(flatQty.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<BomIngredient> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : sorted) {
            out.add(new BomIngredient(e.getKey(), com.blueseer.inv.invData.getItemDesc(e.getKey())));
        }
        return out;
    }

    private double getWtPerUomG(Connection con, String item) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select ing_wt_per_uom_g from ing_mstr where it_item = ?;")) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    double v = res.getDouble("ing_wt_per_uom_g");
                    return v <= 0 ? 1.0 : v;
                }
            }
        }
        return 1.0;
    }

    /** Package-visible (not private) so {@link NutritionLabelEngine} can reuse the same
     *  finished-weight lookup for its own per-100g normalization. */
    double getFinishedNetWeightG(Connection con, String item) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("select it_net_wt from item_mstr where it_item = ?;")) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    return res.getDouble("it_net_wt");
                }
            }
        }
        return 0;
    }

    /**
     * All lines of an item's primary BOM. Deliberately does not join
     * item_cost the way OVData.getpsmstrlist() does - a raw material with no
     * cost record set up would otherwise be silently dropped from a legal
     * ingredient list, which is a correctness bug we can't afford here.
     */
    private List<BomLine> getBomLines(Connection con, String item) throws SQLException {
        List<BomLine> lines = new ArrayList<>();
        String bomId = null;
        try (PreparedStatement ps = con.prepareStatement(
                "select bom_id from bom_mstr where bom_item = ? and bom_primary = '1';")) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    bomId = res.getString("bom_id");
                }
            }
        }
        if (bomId == null) {
            return lines;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "select ps_child, ps_type, ps_qty_per from pbm_mstr where ps_parent = ? and ps_bom = ?;")) {
            ps.setString(1, item);
            ps.setString(2, bomId);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    double qtyPer;
                    try {
                        qtyPer = Double.parseDouble(res.getString("ps_qty_per"));
                    } catch (NumberFormatException nfe) {
                        qtyPer = 0;
                    }
                    lines.add(new BomLine(res.getString("ps_child"), res.getString("ps_type"), qtyPer));
                }
            }
        }
        return lines;
    }
}
