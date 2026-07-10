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
         * Ingredient list as a sequence of positioned ZPL field commands, word-
         * wrapped to the given pixel width, with allergens rendered bold via
         * "double-strike" (the same text re-emitted offset by one dot) - the
         * standard way to fake bold on a ZPL printer whose ^FD field can't mix
         * font weights and that has no separate bold font loaded.
         *
         * Word width is estimated at roughly 0.6x the font height per
         * character, since exact glyph metrics for the printer's resident font
         * aren't available on the host. If wrapping looks off on a real
         * printout, retune CHAR_WIDTH_RATIO below rather than fontHeight.
         */
        public String toZplIngredientBlock(int x, int y, int width, int fontHeight, int lineSpacing) {
            StringBuilder marked = new StringBuilder();
            appendSegmentsMarked(marked, segments);
            double charWidth = fontHeight * CHAR_WIDTH_RATIO;

            StringBuilder zpl = new StringBuilder();
            int cx = x;
            int cy = y;
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
                        int wordWidth = (int) Math.ceil(word.length() * charWidth);
                        if (cx > x && cx + wordWidth > x + width) {
                            cx = x;
                            cy += fontHeight + lineSpacing;
                        }
                        appendZplField(zpl, cx, cy, fontHeight, word.toString(), wordBold);
                        cx += wordWidth + (int) Math.ceil(charWidth);
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
            return zpl.toString();
        }

        private static final double CHAR_WIDTH_RATIO = 0.6;

        private static void appendZplField(StringBuilder zpl, int x, int y, int fontHeight, String text, boolean bold) {
            String escaped = zplEscape(text);
            zpl.append("^FO").append(x).append(",").append(y)
                    .append("^A0N,").append(fontHeight).append(",").append(fontHeight)
                    .append("^FD").append(escaped).append("^FS");
            if (bold) {
                // double-strike one dot down-and-right to thicken the strokes
                zpl.append("^FO").append(x + 1).append(",").append(y + 1)
                        .append("^A0N,").append(fontHeight).append(",").append(fontHeight)
                        .append("^FD").append(escaped).append("^FS");
            }
        }

        private static String zplEscape(String s) {
            // ^ and ~ are ZPL command-prefix characters; strip rather than risk
            // corrupting the command stream if either ever appears in ingredient text.
            return s.replace("^", "").replace("~", "");
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

    private record BomLine(String child, String type, double qtyPer) {
    }

    private static final double COMPOUND_INGREDIENT_THRESHOLD_PCT = 2.0;

    public IngredientLabelResult generate(String finishedItem, String lotNumber, String bestBeforeDate) {
        Map<String, Double> flatQty = new LinkedHashMap<>();
        List<String> internalNotes = new ArrayList<>();
        double finishedWeightG = 0;
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            flattenRecursive(con, finishedItem, 1.0, flatQty);
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

    private void flattenRecursive(Connection con, String item, double qtyPerUnit, Map<String, Double> flatQty)
            throws SQLException {
        for (BomLine line : getBomLines(con, item)) {
            double childQty = qtyPerUnit * line.qtyPer();
            if (line.type().equalsIgnoreCase("M")) {
                flattenRecursive(con, line.child(), childQty, flatQty);
            } else {
                double gramsPerUom = getWtPerUomG(con, line.child());
                flatQty.merge(line.child(), childQty * gramsPerUom, Double::sum);
            }
        }
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

    private double getFinishedNetWeightG(Connection con, String item) throws SQLException {
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
