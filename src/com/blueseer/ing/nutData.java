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

import bsmf.MainFrame;
import static bsmf.MainFrame.bslog;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;
import com.blueseer.utl.BlueSeerUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data access for EU/Irish FIC nutrition-declaration metadata: the nutrient
 * reference table, per-ingredient declared values, and per-finished-item
 * nutrition-label configuration (placement/exemption/portion). Follows
 * ingData.java's exact conventions (PreparedStatement everywhere, no
 * string-concatenated SQL).
 */
public class nutData {

    public record nut_mstr(String nutrient_code, String display_name, String unit, boolean is_mandatory,
            int sort_order, double energy_kj_per_g, double energy_kcal_per_g, double high_threshold_g,
            double high_precision_g, double mid_precision_g, double trace_cutoff_g, double trace_label_g,
            Double nrv_value, boolean has_ri) {
    }

    public record ing_nutrient(String it_item, String nutrient_code, double value_per_100g, String data_source,
            String notes) {
    }

    public record item_nut_cfg(String it_item, String nut_placement, String nut_exempt_reason,
            boolean nut_show_ri_pct, Double nut_portion_size_g, String nut_portion_desc,
            Integer nut_portions_per_pack) {
        public static final String NONE = "NONE";
        public static final String MAIN_LABEL = "MAIN_LABEL";
        public static final String SEPARATE_LABEL = "SEPARATE_LABEL";

        public item_nut_cfg(String it_item) {
            this(it_item, MAIN_LABEL, "", true, null, "", null);
        }
    }

    // ------------------------------------------------------------------
    // nut_mstr (reference table, read-only from the engine's perspective -
    // maintained directly in the DB by whoever does the compliance review,
    // same pattern as ingData.getWarnRules)
    // ------------------------------------------------------------------

    public static ArrayList<nut_mstr> getNutMstrList() {
        ArrayList<nut_mstr> list = new ArrayList<>();
        String sql = "select * from nut_mstr order by sort_order;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet res = ps.executeQuery()) {
            while (res.next()) {
                list.add(fromResultSet(res));
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return list;
    }

    private static nut_mstr fromResultSet(ResultSet res) throws SQLException {
        return new nut_mstr(res.getString("nutrient_code"), res.getString("display_name"), res.getString("unit"),
                "1".equals(res.getString("is_mandatory")), res.getInt("sort_order"), res.getDouble("energy_kj_per_g"),
                res.getDouble("energy_kcal_per_g"), res.getDouble("high_threshold_g"), res.getDouble("high_precision_g"),
                res.getDouble("mid_precision_g"), res.getDouble("trace_cutoff_g"), res.getDouble("trace_label_g"),
                nullableDouble(res, "nrv_value"), "1".equals(res.getString("has_ri")));
    }

    private static Double nullableDouble(ResultSet res, String column) throws SQLException {
        double v = res.getDouble(column);
        return res.wasNull() ? null : v;
    }

    // ------------------------------------------------------------------
    // ing_nutrient (per-ingredient declared value, manual entry only - see
    // the epic's decision against external/public dataset seeding) - always
    // replaced wholesale on save, same "delete then reinsert" shape used
    // for ing_allergen/ing_subingredient.
    // ------------------------------------------------------------------

    public static Map<String, ing_nutrient> getIngNutrients(String item) {
        Map<String, ing_nutrient> map = new LinkedHashMap<>();
        String sql = "select * from ing_nutrient where it_item = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    ing_nutrient rec = new ing_nutrient(res.getString("it_item"), res.getString("nutrient_code"),
                            res.getDouble("value_per_100g"), res.getString("data_source"), res.getString("notes"));
                    map.put(rec.nutrient_code(), rec);
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return map;
    }

    public static String[] setIngNutrients(String item, ArrayList<ing_nutrient> values) {
        String[] m;
        Connection con = null;
        try {
            con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
            con.setAutoCommit(false);
            try (PreparedStatement pd = con.prepareStatement("delete from ing_nutrient where it_item = ?;")) {
                pd.setString(1, item);
                pd.executeUpdate();
            }
            try (PreparedStatement pi = con.prepareStatement(
                    "insert into ing_nutrient (it_item, nutrient_code, value_per_100g, data_source, notes) "
                    + "values (?,?,?,?,?);")) {
                for (ing_nutrient v : values) {
                    pi.setString(1, item);
                    pi.setString(2, v.nutrient_code());
                    pi.setDouble(3, v.value_per_100g());
                    pi.setString(4, v.data_source());
                    pi.setString(5, v.notes());
                    pi.addBatch();
                }
                if (!values.isEmpty()) {
                    pi.executeBatch();
                }
            }
            con.commit();
            m = new String[] {BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess};
        } catch (SQLException s) {
            MainFrame.bslog(s);
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException rb) {
                MainFrame.bslog(rb);
            }
            m = new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException ex) {
                    MainFrame.bslog(ex);
                }
            }
        }
        return m;
    }

    /**
     * Bulk CSV import (see MassLoad.processNutrientMaster). Each line is
     * it_item, nutrient_code, value_per_100g, data_source, notes - already
     * validated by MassLoad.checkNutrientMaster before this is called. A
     * bulk-typing convenience for figures already manually gathered from
     * supplier datasheets (client decision) - each line still upserts one
     * (item, nutrient) row rather than replacing the item's whole nutrient
     * set, so a CSV covering only some nutrients doesn't wipe the rest.
     */
    public static String[] addNutrientMasterMass(ArrayList<String> lines, String delim) {
        String[] m = new String[]{BlueSeerUtils.SuccessBit, ""};
        Connection con = null;
        try {
            con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
            String sqlSelect = "select 1 from ing_nutrient where it_item = ? and nutrient_code = ?;";
            String sqlInsert = "insert into ing_nutrient (it_item, nutrient_code, value_per_100g, data_source, notes) "
                    + "values (?,?,?,?,?);";
            String sqlUpdate = "update ing_nutrient set value_per_100g = ?, data_source = ?, notes = ? "
                    + "where it_item = ? and nutrient_code = ?;";
            for (String rec : lines) {
                String[] ld = rec.split(delim, -1);
                String item = ld[0];
                String nutrientCode = ld[1].trim().toUpperCase();
                double valuePer100g;
                try {
                    valuePer100g = Double.parseDouble(ld[2]);
                } catch (NumberFormatException nfe) {
                    valuePer100g = 0;
                }
                String dataSource = ld.length > 3 && !ld[3].isBlank() ? ld[3].trim().toUpperCase() : "SUPPLIER_SPEC";
                String notes = ld.length > 4 ? ld[4] : "";
                boolean exists;
                try (PreparedStatement ps = con.prepareStatement(sqlSelect)) {
                    ps.setString(1, item);
                    ps.setString(2, nutrientCode);
                    try (ResultSet res = ps.executeQuery()) {
                        exists = res.next();
                    }
                }
                if (exists) {
                    try (PreparedStatement psu = con.prepareStatement(sqlUpdate)) {
                        psu.setDouble(1, valuePer100g);
                        psu.setString(2, dataSource);
                        psu.setString(3, notes);
                        psu.setString(4, item);
                        psu.setString(5, nutrientCode);
                        psu.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psi = con.prepareStatement(sqlInsert)) {
                        psi.setString(1, item);
                        psi.setString(2, nutrientCode);
                        psi.setDouble(3, valuePer100g);
                        psi.setString(4, dataSource);
                        psi.setString(5, notes);
                        psi.executeUpdate();
                    }
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
            m = new String[]{BlueSeerUtils.ErrorBit, s.getMessage()};
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException ex) {
                    MainFrame.bslog(ex);
                }
            }
        }
        return m;
    }

    public static boolean isValidNutrientCode(String code) {
        String sql = "select 1 from nut_mstr where nutrient_code = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet res = ps.executeQuery()) {
                return res.next();
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // item_nut_cfg (per FINISHED item nutrition-label placement/portion
    // configuration)
    // ------------------------------------------------------------------

    public static item_nut_cfg getItemNutCfg(String item) {
        String sql = "select * from item_nut_cfg where it_item = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    double portionSize = res.getDouble("nut_portion_size_g");
                    int portionsPerPack = res.getInt("nut_portions_per_pack");
                    return new item_nut_cfg(item, res.getString("nut_placement"), res.getString("nut_exempt_reason"),
                            "1".equals(res.getString("nut_show_ri_pct")),
                            res.wasNull() || portionSize <= 0 ? null : portionSize,
                            res.getString("nut_portion_desc"),
                            res.wasNull() || portionsPerPack <= 0 ? null : portionsPerPack);
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return new item_nut_cfg(item);
    }

    public static String[] addUpdateItemNutCfg(item_nut_cfg x) {
        String[] m;
        String sqlSelect = "select 1 from item_nut_cfg where it_item = ?;";
        String sqlInsert = "insert into item_nut_cfg (it_item, nut_placement, nut_exempt_reason, nut_show_ri_pct, "
                + "nut_portion_size_g, nut_portion_desc, nut_portions_per_pack) values (?,?,?,?,?,?,?);";
        String sqlUpdate = "update item_nut_cfg set nut_placement = ?, nut_exempt_reason = ?, nut_show_ri_pct = ?, "
                + "nut_portion_size_g = ?, nut_portion_desc = ?, nut_portions_per_pack = ? where it_item = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            boolean exists;
            try (PreparedStatement ps = con.prepareStatement(sqlSelect)) {
                ps.setString(1, x.it_item());
                try (ResultSet res = ps.executeQuery()) {
                    exists = res.next();
                }
            }
            int rows;
            if (exists) {
                try (PreparedStatement psu = con.prepareStatement(sqlUpdate)) {
                    psu.setString(1, x.nut_placement());
                    psu.setString(2, x.nut_exempt_reason());
                    psu.setString(3, x.nut_show_ri_pct() ? "1" : "0");
                    if (x.nut_portion_size_g() == null) {
                        psu.setNull(4, java.sql.Types.DECIMAL);
                    } else {
                        psu.setDouble(4, x.nut_portion_size_g());
                    }
                    psu.setString(5, x.nut_portion_desc());
                    if (x.nut_portions_per_pack() == null) {
                        psu.setNull(6, java.sql.Types.INTEGER);
                    } else {
                        psu.setInt(6, x.nut_portions_per_pack());
                    }
                    psu.setString(7, x.it_item());
                    rows = psu.executeUpdate();
                }
            } else {
                try (PreparedStatement psi = con.prepareStatement(sqlInsert)) {
                    psi.setString(1, x.it_item());
                    psi.setString(2, x.nut_placement());
                    psi.setString(3, x.nut_exempt_reason());
                    psi.setString(4, x.nut_show_ri_pct() ? "1" : "0");
                    if (x.nut_portion_size_g() == null) {
                        psi.setNull(5, java.sql.Types.DECIMAL);
                    } else {
                        psi.setDouble(5, x.nut_portion_size_g());
                    }
                    psi.setString(6, x.nut_portion_desc());
                    if (x.nut_portions_per_pack() == null) {
                        psi.setNull(7, java.sql.Types.INTEGER);
                    } else {
                        psi.setInt(7, x.nut_portions_per_pack());
                    }
                    rows = psi.executeUpdate();
                }
            }
            m = rows > 0
                ? new String[] {BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess}
                : new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } catch (SQLException s) {
            MainFrame.bslog(s);
            m = new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        }
        return m;
    }
}
