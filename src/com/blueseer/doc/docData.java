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
import static bsmf.MainFrame.bslog;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;
import com.blueseer.utl.BlueSeerUtils;
import com.blueseer.utl.OVData;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Data access for agentic document import: the one-time local-LLM runtime
 * configuration (System Control, never staff-facing), the import audit
 * trail, and the per-supplier item-description memory that lets a repeat
 * invoice line auto-fill without asking again. Follows the same
 * conventions as com.blueseer.ing.ingData: PreparedStatement everywhere,
 * no string-concatenated SQL.
 */
public class docData {

    public record llm_config(String provider, String baseurl, String model, boolean enabled) {
    }

    private static final llm_config DEFAULT_CONFIG = new llm_config("LMSTUDIO", "http://localhost:1234", "", false);

    public static llm_config getLlmConfig() {
        String sql = "select ov_llm_provider, ov_llm_baseurl, ov_llm_model, ov_llm_enabled from ov_ctrl;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet res = ps.executeQuery()) {
            if (res.next()) {
                return new llm_config(res.getString("ov_llm_provider"), res.getString("ov_llm_baseurl"),
                        res.getString("ov_llm_model"), res.getBoolean("ov_llm_enabled"));
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return DEFAULT_CONFIG;
    }

    public static String[] saveLlmConfig(llm_config cfg) {
        String sql = "update ov_ctrl set ov_llm_provider = ?, ov_llm_baseurl = ?, ov_llm_model = ?, ov_llm_enabled = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cfg.provider());
            ps.setString(2, cfg.baseurl());
            ps.setString(3, cfg.model());
            ps.setBoolean(4, cfg.enabled());
            int rows = ps.executeUpdate();
            return rows > 0
                    ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.updateRecordSuccess}
                    : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.updateRecordError};
        }
    }

    public static String[] logImport(String docType, String targetKey, String extractedJson) {
        String logId = String.valueOf(OVData.getNextNbr("docimportlog"));
        String sql = "insert into doc_import_log (log_id, log_doctype, log_target_key, log_extracted_json, log_userid, log_created) values (?,?,?,?,?,?);";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, logId);
            ps.setString(2, docType);
            ps.setString(3, targetKey);
            ps.setString(4, extractedJson);
            ps.setString(5, MainFrame.userid);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            int rows = ps.executeUpdate();
            return rows > 0
                    ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.addRecordSuccess}
                    : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
    }

    /**
     * Returns the remembered item code for this supplier + raw line text,
     * or "" if this exact wording hasn't been seen (and corrected) before.
     */
    public static String lookupItemAlias(String vend, String rawText) {
        if (vend == null || vend.isBlank() || rawText == null || rawText.isBlank()) {
            return "";
        }
        String sql = "select alias_item from doc_item_alias where alias_vend = ? and alias_rawtext = ?;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, vend);
            ps.setString(2, rawText);
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    return res.getString("alias_item");
                }
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
        }
        return "";
    }

    /**
     * Remembers a supplier's raw line text as mapping to the given item, so
     * the next import from the same supplier auto-fills it (epic Sec 7,
     * DOC-21). Overwrites any previous mapping for the same (vend, rawText)
     * pair - the most recent human correction wins.
     */
    public static String[] saveItemAlias(String vend, String rawText, String item) {
        if (vend == null || vend.isBlank() || rawText == null || rawText.isBlank() || item == null || item.isBlank()) {
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
        String sqlDelete = "delete from doc_item_alias where alias_vend = ? and alias_rawtext = ?;";
        String sqlInsert = "insert into doc_item_alias (alias_vend, alias_rawtext, alias_item) values (?,?,?);";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection())) {
            try (PreparedStatement psd = con.prepareStatement(sqlDelete)) {
                psd.setString(1, vend);
                psd.setString(2, rawText);
                psd.executeUpdate();
            }
            try (PreparedStatement psi = con.prepareStatement(sqlInsert)) {
                psi.setString(1, vend);
                psi.setString(2, rawText);
                psi.setString(3, item);
                int rows = psi.executeUpdate();
                return rows > 0
                        ? new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.addRecordSuccess}
                        : new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.addRecordError};
        }
    }

    /**
     * Best-effort match of an extracted supplier name (free text read off a
     * photo) to a real vd_mstr vendor code, for pre-selecting the vendor
     * combo when the central Scan to Import screen routes into Receiver
     * Maintenance. A simple case-insensitive substring match either way -
     * good enough to save a click on an exact/near match, not intended to be
     * clever; returns "" (leaving the user to pick manually) rather than
     * guess when nothing matches or more than one vendor does.
     */
    public static String findVendorByName(String extractedName) {
        if (extractedName == null || extractedName.isBlank()) {
            return "";
        }
        String sql = "select vd_addr, vd_name from vd_mstr where instr(lower(vd_name), lower(?)) > 0 or instr(lower(?), lower(vd_name)) > 0;";
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, extractedName);
            ps.setString(2, extractedName);
            String match = "";
            int matches = 0;
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    matches++;
                    match = res.getString("vd_addr");
                }
            }
            return matches == 1 ? match : "";
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return "";
        }
    }

    /**
     * Fuzzy item search by description - backs the ERPTools.searchItemsByDescription
     * tool (com.blueseer.doc.ERPTools) an extraction agent calls itself to resolve
     * a raw invoice line description to a real item_mstr row, rather than BlueSeer
     * matching it after the fact. Returns up to 5 "CODE - description" candidates,
     * one per line, or "" if nothing matches within the given catalog.
     */
    public static String searchItemsByDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String sql = "select it_item, it_desc from item_mstr where instr(lower(it_desc), lower(?)) > 0 or instr(lower(?), lower(it_desc)) > 0 limit 5;";
        StringBuilder result = new StringBuilder();
        try (Connection con = (ds == null ? DriverManager.getConnection(url + db, user, pass) : ds.getConnection());
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setString(2, description);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    if (result.length() > 0) {
                        result.append("\n");
                    }
                    result.append(res.getString("it_item")).append(" - ").append(res.getString("it_desc"));
                }
            }
        } catch (SQLException e) {
            MainFrame.bslog(e);
            return "";
        }
        return result.toString();
    }
}
