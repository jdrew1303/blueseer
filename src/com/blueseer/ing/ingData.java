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
import static com.blueseer.utl.BlueSeerUtils.getMessageTag;
import static com.blueseer.utl.BlueSeerUtils.jsonToStringArray;
import static com.blueseer.utl.BlueSeerUtils.sendServerPost;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Data access for ingredient regulatory metadata (allergens, E-numbers,
 * legal names, purchased-compound sub-ingredient declarations, and the
 * mandatory-warning reference table) backing EU/Irish FIC label generation.
 * Follows the same conventions as com.blueseer.inv.invData: PreparedStatement
 * everywhere, a remote-DB JSON dispatch stub on public entry points, and
 * BlueSeerUtils message-tag constants for result reporting.
 */
public class ingData {

    public record ing_mstr(String[] m, String it_item, String ing_legalname, String ing_category,
        String ing_enumber, String ing_iscompound, String ing_active, String ing_notes, double ing_wt_per_uom_g) {
        public ing_mstr(String[] m) {
            this(m, "", "", "", "", "", "1", "", 1.0);
        }
    }

    public record ing_subingredient(String[] m, String it_item, int seq, String sub_name,
        String sub_enumber, String is_allergen) {
        public ing_subingredient(String[] m) {
            this(m, "", 0, "", "", "0");
        }
    }

    public record warn_rule(String[] m, String warn_code, String trigger_type, String trigger_value,
        double threshold_pct, String warning_text, String warn_active) {
        public warn_rule(String[] m) {
            this(m, "", "", "", 0, "", "1");
        }
    }

    public record ing_allergen_ref(String[] m, String allergen_code, String allergen_desc) {
        public ing_allergen_ref(String[] m) {
            this(m, "", "");
        }
    }

    // ------------------------------------------------------------------
    // ing_mstr
    // ------------------------------------------------------------------

    public static String[] addUpdateIngMstr(ing_mstr x) {
        if (bsmf.MainFrame.remoteDB && ! bsmf.MainFrame.isSSHConnected) {
            ArrayList<String[]> list = new ArrayList<String[]>();
            list.add(new String[]{"id", "addUpdateIngMstr"});
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                String jsonString = objectMapper.writeValueAsString(x);
                return jsonToStringArray(sendServerPost(list, jsonString, null, "dataServING"));
            } catch (IOException ex) {
                bslog(ex);
                return new String[]{BlueSeerUtils.ErrorBit, getMessageTag(1016, Thread.currentThread().getStackTrace()[1].getMethodName())};
            }
        }
        if (x == null || x.it_item().isBlank()) {
            return new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
        String[] m;
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            int rows = _addUpdateIngMstr(x, con);
            m = rows > 0
                ? new String[] {BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess}
                : new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } catch (SQLException s) {
            MainFrame.bslog(s);
            m = new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        }
        return m;
    }

    private static int _addUpdateIngMstr(ing_mstr x, Connection con) throws SQLException {
        int rows;
        String sqlSelect = "select * from ing_mstr where it_item = ?;";
        String sqlInsert = "insert into ing_mstr (it_item, ing_legalname, ing_category, ing_enumber, "
                + "ing_iscompound, ing_active, ing_notes, ing_wt_per_uom_g) values (?,?,?,?,?,?,?,?);";
        String sqlUpdate = "update ing_mstr set ing_legalname = ?, ing_category = ?, ing_enumber = ?, "
                + "ing_iscompound = ?, ing_active = ?, ing_notes = ?, ing_wt_per_uom_g = ? where it_item = ?;";
        try (PreparedStatement ps = con.prepareStatement(sqlSelect)) {
            ps.setString(1, x.it_item());
            try (ResultSet res = ps.executeQuery()) {
                if (!res.isBeforeFirst()) {
                    try (PreparedStatement psi = con.prepareStatement(sqlInsert)) {
                        psi.setString(1, x.it_item());
                        psi.setString(2, x.ing_legalname());
                        psi.setString(3, x.ing_category());
                        psi.setString(4, x.ing_enumber());
                        psi.setString(5, x.ing_iscompound());
                        psi.setString(6, x.ing_active());
                        psi.setString(7, x.ing_notes());
                        psi.setDouble(8, x.ing_wt_per_uom_g() <= 0 ? 1.0 : x.ing_wt_per_uom_g());
                        rows = psi.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psu = con.prepareStatement(sqlUpdate)) {
                        psu.setString(1, x.ing_legalname());
                        psu.setString(2, x.ing_category());
                        psu.setString(3, x.ing_enumber());
                        psu.setString(4, x.ing_iscompound());
                        psu.setString(5, x.ing_active());
                        psu.setString(6, x.ing_notes());
                        psu.setDouble(7, x.ing_wt_per_uom_g() <= 0 ? 1.0 : x.ing_wt_per_uom_g());
                        psu.setString(8, x.it_item());
                        rows = psu.executeUpdate();
                    }
                }
            }
        }
        return rows;
    }

    public static ing_mstr getIngMstr(String item) {
        String[] m;
        ing_mstr r;
        if (bsmf.MainFrame.remoteDB && ! bsmf.MainFrame.isSSHConnected) {
            ArrayList<String[]> list = new ArrayList<String[]>();
            list.add(new String[]{"id", "getIngMstr"});
            list.add(new String[]{"param1", item});
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                String returnstring = sendServerPost(list, "", null, "dataServING");
                return objectMapper.readValue(returnstring, ing_mstr.class);
            } catch (IOException ex) {
                bslog(ex);
                m = new String[]{BlueSeerUtils.ErrorBit, getMessageTag(1016, Thread.currentThread().getStackTrace()[1].getMethodName())};
                return new ing_mstr(m);
            }
        }
        String sql = "select * from ing_mstr where it_item = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (!res.isBeforeFirst()) {
                    m = new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.noRecordFound};
                    r = new ing_mstr(m);
                } else {
                    res.next();
                    m = new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getRecordSuccess};
                    r = new ing_mstr(m, res.getString("it_item"), res.getString("ing_legalname"),
                            res.getString("ing_category"), res.getString("ing_enumber"),
                            res.getString("ing_iscompound"), res.getString("ing_active"),
                            res.getString("ing_notes"), res.getDouble("ing_wt_per_uom_g"));
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
            m = new String[]{BlueSeerUtils.ErrorBit, getMessageTag(1016, Thread.currentThread().getStackTrace()[1].getMethodName())};
            r = new ing_mstr(m);
        }
        return r;
    }

    /**
     * Bulk CSV import (see MassLoad.processIngredientMaster). Each line is
     * it_item, ing_legalname, ing_category, ing_enumber, ing_iscompound,
     * allergen_codes (pipe-delimited), ing_wt_per_uom_g - already validated
     * by MassLoad.checkIngredientMaster before this is called.
     */
    public static String[] addIngredientMasterMass(ArrayList<String> lines, String delim) {
        String[] m = new String[]{BlueSeerUtils.SuccessBit, ""};
        Connection con = null;
        try {
            con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
            for (String rec : lines) {
                String[] ld = rec.split(delim, -1);
                double wtPerUom = 1.0;
                if (ld.length > 6 && !ld[6].isBlank()) {
                    try {
                        wtPerUom = Double.parseDouble(ld[6]);
                    } catch (NumberFormatException ignored) {
                        wtPerUom = 1.0;
                    }
                }
                ing_mstr x = new ing_mstr(null, ld[0], ld[1], ld[2], ld[3], ld[4].isBlank() ? "0" : ld[4], "1", "",
                        wtPerUom <= 0 ? 1.0 : wtPerUom);
                _addUpdateIngMstr(x, con);
                ArrayList<String> codes = new ArrayList<>();
                if (ld.length > 5 && !ld[5].isBlank()) {
                    for (String code : ld[5].split("\\|", -1)) {
                        if (!code.isBlank()) {
                            codes.add(code.trim().toUpperCase());
                        }
                    }
                }
                if (!codes.isEmpty()) {
                    setAllergenCodes(ld[0], codes);
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

    // ------------------------------------------------------------------
    // ing_allergen (many-to-many item <-> allergen code) - always replaced
    // wholesale on save, same "delete then reinsert" shape used elsewhere
    // in BlueSeer for child-row lists (see invData.addRoutingMstr).
    // ------------------------------------------------------------------

    public static ArrayList<String> getAllergenCodes(String item) {
        ArrayList<String> codes = new ArrayList<>();
        String sql = "select allergen_code from ing_allergen where it_item = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    codes.add(res.getString("allergen_code"));
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return codes;
    }

    public static String[] setAllergenCodes(String item, ArrayList<String> codes) {
        String[] m;
        Connection con = null;
        try {
            con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
            con.setAutoCommit(false);
            try (PreparedStatement pd = con.prepareStatement("delete from ing_allergen where it_item = ?;")) {
                pd.setString(1, item);
                pd.executeUpdate();
            }
            try (PreparedStatement pi = con.prepareStatement("insert into ing_allergen (it_item, allergen_code) values (?,?);")) {
                for (String code : codes) {
                    pi.setString(1, item);
                    pi.setString(2, code);
                    pi.addBatch();
                }
                if (!codes.isEmpty()) {
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

    public static boolean isValidAllergenCode(String code) {
        String sql = "select 1 from ing_allergen_ref where allergen_code = ?;";
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

    public static ArrayList<ing_allergen_ref> getAllergenRef() {
        ArrayList<ing_allergen_ref> refs = new ArrayList<>();
        String sql = "select allergen_code, allergen_desc from ing_allergen_ref order by allergen_code;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet res = ps.executeQuery()) {
            while (res.next()) {
                String[] m = new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getRecordSuccess};
                refs.add(new ing_allergen_ref(m, res.getString("allergen_code"), res.getString("allergen_desc")));
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return refs;
    }

    // ------------------------------------------------------------------
    // ing_subingredient (purchased-compound declared breakdown)
    // ------------------------------------------------------------------

    public static ArrayList<ing_subingredient> getSubIngredients(String item) {
        ArrayList<ing_subingredient> subs = new ArrayList<>();
        String sql = "select * from ing_subingredient where it_item = ? order by seq;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    String[] m = new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getRecordSuccess};
                    subs.add(new ing_subingredient(m, res.getString("it_item"), res.getInt("seq"),
                            res.getString("sub_name"), res.getString("sub_enumber"), res.getString("is_allergen")));
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return subs;
    }

    public static String[] setSubIngredients(String item, ArrayList<ing_subingredient> subs) {
        String[] m;
        Connection con = null;
        try {
            con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
            con.setAutoCommit(false);
            try (PreparedStatement pd = con.prepareStatement("delete from ing_subingredient where it_item = ?;")) {
                pd.setString(1, item);
                pd.executeUpdate();
            }
            try (PreparedStatement pi = con.prepareStatement(
                    "insert into ing_subingredient (it_item, seq, sub_name, sub_enumber, is_allergen) values (?,?,?,?,?);")) {
                int seq = 0;
                for (ing_subingredient sub : subs) {
                    seq++;
                    pi.setString(1, item);
                    pi.setInt(2, seq);
                    pi.setString(3, sub.sub_name());
                    pi.setString(4, sub.sub_enumber());
                    pi.setString(5, sub.is_allergen());
                    pi.addBatch();
                }
                if (!subs.isEmpty()) {
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

    // ------------------------------------------------------------------
    // warn_rule (reference table, read-only from the engine's perspective -
    // maintained directly in the DB by whoever does the compliance review,
    // not exposed through a maintenance screen in this phase)
    // ------------------------------------------------------------------

    public static ArrayList<warn_rule> getWarnRules() {
        ArrayList<warn_rule> rules = new ArrayList<>();
        String sql = "select * from warn_rule where warn_active = '1';";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet res = ps.executeQuery()) {
            while (res.next()) {
                String[] m = new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getRecordSuccess};
                rules.add(new warn_rule(m, res.getString("warn_code"), res.getString("trigger_type"),
                        res.getString("trigger_value"), res.getDouble("threshold_pct"),
                        res.getString("warning_text"), res.getString("warn_active")));
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return rules;
    }

    /**
     * Most recent lot/expiry on hand for an item, from BlueSeer's existing
     * in_mstr lot records - a reasonable default for the item-level "Print
     * Label" button. Printing at a moment where the actual production
     * lot/batch is known (e.g. work order completion) should pass that lot's
     * real in_serial/in_expire instead of calling this.
     */
    public static String[] getMostRecentLot(String item) {
        String sql = "select in_serial, in_expire from in_mstr where in_item = ? order by in_date desc limit 1;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    return new String[]{res.getString("in_serial"), res.getString("in_expire")};
                }
            }
        } catch (SQLException s) {
            MainFrame.bslog(s);
        }
        return new String[]{"", ""};
    }

}
