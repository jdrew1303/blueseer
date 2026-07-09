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

package com.blueseer.ord;

import com.blueseer.pur.*;
import bsmf.MainFrame;
import static bsmf.MainFrame.bslog;
import com.blueseer.utl.OVData;
import com.blueseer.utl.BlueSeerUtils;
import static bsmf.MainFrame.checkperms;
import static bsmf.MainFrame.db;
import static bsmf.MainFrame.ds;
import static bsmf.MainFrame.pass;
import java.awt.Color;
import java.awt.Component;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import static bsmf.MainFrame.reinitpanels;
import static bsmf.MainFrame.tags;
import static bsmf.MainFrame.url;
import static bsmf.MainFrame.user;
import com.blueseer.ctr.cusData;
import static com.blueseer.edi.ediData.getEDIMetaValueAsKVStringPair;
import static com.blueseer.edi.ediData.getEDIMetaValueDetail;
import static com.blueseer.edi.ediData.getEDIMetaValueHeader;
import static com.blueseer.ord.ordData._evaluateOrderChange;
import static com.blueseer.ord.ordData.applyOrderChange;
import static com.blueseer.ord.ordData.getOrderChangeBrowseDetail;
import static com.blueseer.ord.ordData.updateOrderChangeStatus;
import static com.blueseer.utl.BlueSeerUtils.bsNumber;
import static com.blueseer.utl.BlueSeerUtils.bsNumberToUS;
import static com.blueseer.utl.BlueSeerUtils.bsParseDouble;
import static com.blueseer.utl.BlueSeerUtils.cleanDirString;
import static com.blueseer.utl.BlueSeerUtils.currformatDouble;
import static com.blueseer.utl.BlueSeerUtils.getDateDB;
import static com.blueseer.utl.BlueSeerUtils.getGlobalColumnTag;
import static com.blueseer.utl.BlueSeerUtils.getGlobalProgTag;
import static com.blueseer.utl.BlueSeerUtils.getMessageTag;
import static com.blueseer.utl.BlueSeerUtils.jsonToData;
import static com.blueseer.utl.BlueSeerUtils.sendServerPost;
import static com.blueseer.utl.BlueSeerUtils.setDateDB;
import static com.blueseer.utl.OVData.getSystemJasperDirectory;
import com.blueseer.vdr.venData;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.ListOfArrayDataSource;
import net.sf.jasperreports.view.JasperViewer;

/**
 *
 * @author vaughnte
 */
public class OrderChangeBrowse extends javax.swing.JPanel {
 
     public Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
     
     public String currentid = "";
     public String rsData; 
     Object[][] roData;
    ArrayList<String[]> initDataSets = new ArrayList<>();
    String defaultcurrency = "";
     
    javax.swing.table.DefaultTableModel modeltable = new javax.swing.table.DefaultTableModel(new Object[][]{},
                        new String[]{getGlobalColumnTag("detail"), 
                            getGlobalColumnTag("id"),
                            getGlobalColumnTag("order"),
                            getGlobalColumnTag("po"),
                            getGlobalColumnTag("date"),
                            getGlobalColumnTag("name"),                              
                            "old Due Date",
                            "new Due Date",
                            "Changes",
                            getGlobalColumnTag("status"),
                            "Commit",
                            getGlobalColumnTag("void"),
                            "View"})
            {
                      @Override  
                      public Class getColumnClass(int col) {  
                        if (col == 0 || col == 10 || col == 11 || col == 12)       
                            return ImageIcon.class;  
                        else return String.class;  //other columns accept String values  
                      }  
                      
                      @Override
                      public boolean isCellEditable(int row, int column) {
                            return false;
                            //Only the first column
                            // return column == 1;
                      }
                      
                        };
                
    javax.swing.table.DefaultTableModel modeldetail = new javax.swing.table.DefaultTableModel(new Object[][]{},
                        new String[]{
                            "View",
                            getGlobalColumnTag("line"), 
                            getGlobalColumnTag("item"), 
                            "old price", 
                            "new price", 
                            "old qty", 
                            "new qty" })
            {
                      @Override  
                      public Class getColumnClass(int col) {  
                        if (col == 0)       
                            return ImageIcon.class;  
                        else return String.class;  //other columns accept String values  
                      }  
                      
                      @Override
                      public boolean isCellEditable(int row, int column) {
                            return false;
                            //Only the first column
                            // return column == 1;
                      }
                      
                        };;
    
     class ButtonRenderer extends JButton implements TableCellRenderer {

        public ButtonRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(Color.blue);
                setBackground(UIManager.getColor("Button.background"));
            }
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }
    
    class SomeRenderer extends DefaultTableCellRenderer {
        
    public Component getTableCellRendererComponent(JTable table,
            Object value, boolean isSelected, boolean hasFocus, int row,
            int column) {

        Component c = super.getTableCellRendererComponent(table,
                value, isSelected, hasFocus, row, column);
        
        String change = (String)table.getModel().getValueAt(table.convertRowIndexToModel(row), 8);
        String status = (String)table.getModel().getValueAt(table.convertRowIndexToModel(row), 9);
        
         if (! "none".equals(change) && column == 8 && status.equals("open")) {
              c.setBackground(Color.ORANGE);
              c.setForeground(Color.WHITE);
        } else if (status.equals("detached")) {
            c.setBackground(table.getBackground());
            c.setForeground(Color.RED);
        } else {
            c.setBackground(table.getBackground());
            c.setForeground(table.getBackground());
        }       
        
        //c.setBackground(row % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE);
      // c.setBackground(row % 2 == 0 ? Color.GREEN : Color.LIGHT_GRAY);
      // c.setBackground(row % 3 == 0 ? new Color(245,245,220) : Color.LIGHT_GRAY);
       /*
            if (column == 3)
            c.setForeground(Color.BLUE);
            else
                c.setBackground(table.getBackground());
       */
        return c;
    }
    }

    class DetailRenderer extends DefaultTableCellRenderer {
        
    public Component getTableCellRendererComponent(JTable table,
            Object value, boolean isSelected, boolean hasFocus, int row,
            int column) {

        Component c = super.getTableCellRendererComponent(table,
                value, isSelected, hasFocus, row, column);
        
         double oldprice = Double.valueOf((String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 3).toString());
         double newprice = Double.valueOf((String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 4).toString());
         double oldqty = Double.valueOf((String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 5).toString());
         double newqty = Double.valueOf((String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 6).toString());
         
        c.setBackground(table.getBackground());
        c.setForeground(table.getBackground()); 
        
        if (oldprice != newprice) {
              if (column == 3 || column == 4) {
                c.setBackground(Color.ORANGE);
                c.setForeground(Color.WHITE);
              }
        }
         
        if (oldqty != newqty) {
          if (column == 5 || column == 6) {
            c.setBackground(Color.ORANGE);
            c.setForeground(Color.WHITE);
          }
        }
        
        //c.setBackground(row % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE);
      // c.setBackground(row % 2 == 0 ? Color.GREEN : Color.LIGHT_GRAY);
      // c.setBackground(row % 3 == 0 ? new Color(245,245,220) : Color.LIGHT_GRAY);
       /*
            if (column == 3)
            c.setForeground(Color.BLUE);
            else
                c.setBackground(table.getBackground());
       */
        return c;
    }
    }

    public void executeTask(String x, String[] y) { 
      
        class Task extends SwingWorker<String[], Void> {
         
          String action = "";
          String[] key = null;
          
          public Task(String action, String[] key) { 
              this.action = action;
              this.key = key;
          }     
            
        @Override
        public String[] doInBackground() throws Exception {
            String[] message = new String[2];
            message[0] = "";
            message[1] = "";
            
            rsData = "";
            
            
            switch(this.action) {
                case "dataInit":
                    message = getInitialization();
                    break;
                    
                case "exportOrderChange":
                    message = serverPostExportOrderChange();
                    break;
                    
                case "getOrderChangeBrowseView":
                    message = getOrderChangeBrowseView();
                    break; 
                    
                case "getOrderChangeBrowseDetail":
                    message = getDetail(this.key[0], this.key[1], this.key[2]);
                    break;
                    
                default:
                    message = new String[]{"1", "unknown action"};
            }
            
            
            
            
            return message;
        }
 
        
       public void done() {
            try {
            String[] message = get();
           
            BlueSeerUtils.endTask(message);           
            
            enableAll();
            
            if (this.action.equals("dataInit")) {
                    done_Initialization();
            }
            
            if (this.action.equals("exportOrderChange")) {
                if (rsData != null && ! rsData.isBlank()) {
                  createExportFile(rsData);
                  bsmf.MainFrame.show("export file created");
                }
            }
            
            if (this.action.equals("getOrderChangeBrowseView")) {
                done_getOrderChangeBrowseView();
            }
            
            if (this.action.equals("getOrderChangeBrowseDetail")) {
                done_getDetail();
            }
            
            } catch (Exception e) {
                MainFrame.bslog(e);
            } 
           
        }
    }  
      
       BlueSeerUtils.startTask(new String[]{"","Running..."});
       Task z = new Task(x, y); 
       z.execute(); 
       
    }
   
    
    
    /**
     * Creates new form ScrapReportPanel
     */
    public OrderChangeBrowse() {
        initComponents();
        setLanguageTags(this);
    }

    public void getdetail(String id, String po) {
      
         modeldetail.setNumRows(0);
         double total = 0;
        
        try {

            Connection con = null;
        if (ds != null) {
          con = ds.getConnection();
        } else {
          con = DriverManager.getConnection(url + db, user, pass);  
        }
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                int i = 0;
                String blanket = "";
                
                 Enumeration<TableColumn> en = tabledetail.getColumnModel().getColumns();
                 while (en.hasMoreElements()) {
                     TableColumn tc = en.nextElement();
                     if (modeldetail.getColumnClass(tc.getModelIndex()).getSimpleName().equals("ImageIcon")) {
                         continue;
                     }
                     tc.setCellRenderer(new OrderChangeBrowse.DetailRenderer());
                 }
                
                 if (cbdetached.isSelected()) {
                    res = st.executeQuery("select sodc_line, sodc_item, sod_listprice, sodc_price, sod_ord_qty, sodc_qty from sod_chg " +
                        " inner join so_chg on soc_id = sodc_id " +
                        " left outer join sod_det on sodc_po = sod_po and sodc_line = sod_line " +
                        " where sodc_po = " + "'" + po + "'" + 
                        " and sodc_id = " + "'" + id + "'" +
                        ";"); 
                 } else {
                   res = st.executeQuery("select sodc_line, sodc_item, sod_ord_qty, sod_listprice, sodc_qty, sodc_price from sod_chg " +
                        " inner join so_chg on soc_id = sodc_id " +
                        " inner join sod_det on sodc_po = sod_po and sodc_line = sod_line " +
                        " where sod_po = " + "'" + po + "'" + 
                        " and soc_id = " + "'" + id + "'" +
                        ";");  
                 }
                
                while (res.next()) {
                   modeldetail.addRow(new Object[]{ 
                      BlueSeerUtils.clickgear,
                      bsNumber(res.getString("sodc_line")), 
                      res.getString("sodc_item"),
                      bsParseDouble(currformatDouble(res.getDouble("sod_listprice"))),
                      bsParseDouble(currformatDouble(res.getDouble("sodc_price"))),
                      bsNumber(res.getDouble("sod_ord_qty")), 
                      bsNumber(res.getDouble("sodc_qty"))});
                }
              
                
              //  tabledetail.getColumnModel().getColumn(2).setCellRenderer(BlueSeerUtils.NumberRenderer.getCurrencyRenderer(BlueSeerUtils.getCurrencyLocale(OVData.getDefaultCurrency())));
              //  tabledetail.getColumnModel().getColumn(3).setCellRenderer(BlueSeerUtils.NumberRenderer.getCurrencyRenderer(BlueSeerUtils.getCurrencyLocale(OVData.getDefaultCurrency())));
                 this.repaint();

            } catch (SQLException s) {
                MainFrame.bslog(s);
                bsmf.MainFrame.show(getMessageTag(1016, Thread.currentThread().getStackTrace()[1].getMethodName()));
            } finally {
                if (res != null) {
                    res.close();
                }
                if (st != null) {
                    st.close();
                }
                con.close();
            }
        } catch (Exception e) {
            MainFrame.bslog(e);
        }

    }
  
    public void commitChange(String po) {
        
    }
    
    public void setLanguageTags(Object myobj) {
       JPanel panel = null;
        JTabbedPane tabpane = null;
        JScrollPane scrollpane = null;
        if (myobj instanceof JPanel) {
            panel = (JPanel) myobj;
        } else if (myobj instanceof JTabbedPane) {
           tabpane = (JTabbedPane) myobj; 
        } else if (myobj instanceof JScrollPane) {
           scrollpane = (JScrollPane) myobj;    
        } else {
            return;
        }
       Component[] components = panel.getComponents();
       for (Component component : components) {
           if (component instanceof JPanel) {
                    if (tags.containsKey(this.getClass().getSimpleName() + ".panel." + component.getName())) {
                       ((JPanel) component).setBorder(BorderFactory.createTitledBorder(tags.getString(this.getClass().getSimpleName() +".panel." + component.getName())));
                    } 
                    setLanguageTags((JPanel) component);
                }
                if (component instanceof JLabel ) {
                    if (tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) {
                       ((JLabel) component).setText(tags.getString(this.getClass().getSimpleName() +".label." + component.getName()));
                    }
                }
                if (component instanceof JButton ) {
                    if (tags.containsKey("global.button." + component.getName())) {
                       ((JButton) component).setText(tags.getString("global.button." + component.getName()));
                    }
                }
                if (component instanceof JCheckBox) {
                    if (tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) {
                       ((JCheckBox) component).setText(tags.getString(this.getClass().getSimpleName() +".label." + component.getName()));
                    } 
                }
                if (component instanceof JRadioButton) {
                    if (tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) {
                       ((JRadioButton) component).setText(tags.getString(this.getClass().getSimpleName() +".label." + component.getName()));
                    } 
                }
       }
    }
    
     public void disableAll() {
        btRun.setEnabled(false);
        btexport.setEnabled(false);
        btdetail.setEnabled(false);
        btprint.setEnabled(false);
    }
    
    public void enableAll() {
        btRun.setEnabled(true);
        btexport.setEnabled(true);
        btdetail.setEnabled(true);
        btprint.setEnabled(true);
    }
    
    
    public void initvars(String[] arg) {
       executeTask("dataInit", null);         
    }
   
    public String[] getInitialization() {
        initDataSets = ordData.getOrderBrowseInit(this.getClass().getName(), bsmf.MainFrame.userid);
        if (initDataSets.isEmpty()) {
           return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.dataInitError}; 
        } else {
           return new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getRecordSuccess}; 
        }
    }  
    
    public void done_Initialization() {
        Calendar calfrom = Calendar.getInstance();
        Calendar calto = Calendar.getInstance();
        ddsite.removeAllItems();
        ddfromcust.removeAllItems();
        ddtocust.removeAllItems(); 
        String defaultsite = "";
        for (String[] s : initDataSets) {
            if (s[0].equals("currency")) {
              defaultcurrency = s[1];  
            }
            if (s[0].equals("sites")) {
              ddsite.addItem(s[1]); 
            }
            if (s[0].equals("site")) {
              defaultsite = s[1]; 
            }
            if (s[0].equals("customers")) {
              ddfromcust.addItem(s[1]); 
              ddtocust.addItem(s[1]);
            }
            
            if (s[0].equals("system")) {
              String[] t = s[1].split(",",-1);
              if (t[0].equals("browse_start_date")) {
               if (! t[1].isBlank() && BlueSeerUtils.isParsableToInt(t[1]) && t[1].length() < 8) {
               calfrom.add(Calendar.DATE, Integer.valueOf(t[1]));
               dcfrom.setDate(calfrom.getTime()); 
               }
               if (! t[1].isBlank() && BlueSeerUtils.isParsableToInt(t[1]) && t[1].length() == 8) {
               dcfrom.setDate(BlueSeerUtils.parseDate(BlueSeerUtils.convertDateFormat("yyyyMMdd", t[1]))); 
               }
            }
            if (t[0].equals("browse_end_date")) {
               if (! t[1].isBlank() && BlueSeerUtils.isParsableToInt(t[1]) && t[1].length() < 8) {
               calto.add(Calendar.DATE, Integer.valueOf(t[1]));
               dcto.setDate(calto.getTime()); 
               }
               if (! t[1].isBlank() && BlueSeerUtils.isParsableToInt(t[1]) && t[1].length() == 8) {
               dcto.setDate(BlueSeerUtils.parseDate(BlueSeerUtils.convertDateFormat("yyyyMMdd", t[1]))); 
               }
            }
            }
            
        }
        if (ddsite.getItemCount() > 0) {
            ddsite.setSelectedItem(defaultsite);
        }
        ddtocust.setSelectedIndex(ddtocust.getItemCount() - 1);
        lbltotrecs.setText("0");
        labeldettotal.setText("");
        tbsearch.setText("");
        currentid = "";
        tabledetail.setModel(modeldetail);
        tabledetail.getColumnModel().getColumn(0).setMaxWidth(100);
         cbopen.setSelected(true);
         cbclose.setSelected(false);
        java.util.Date now = new java.util.Date();
        calfrom.add(Calendar.DATE, -30);
        dcfrom.setDate(calfrom.getTime()); 
        calto.add(Calendar.DATE, +30);
        dcto.setDate(calto.getTime());
        modeltable.setNumRows(0);
        modeldetail.setNumRows(0);
        tablereport.setModel(modeltable);
        tabledetail.setModel(modeldetail);
        
          tablereport.getColumnModel().getColumn(0).setMaxWidth(100);
          tablereport.getColumnModel().getColumn(10).setMaxWidth(100);
          tablereport.getColumnModel().getColumn(11).setMaxWidth(100);
          tablereport.getColumnModel().getColumn(12).setMaxWidth(100);
          
       Enumeration<TableColumn> en = tablereport.getColumnModel().getColumns();
         while (en.hasMoreElements()) {
             TableColumn tc = en.nextElement();
             if (modeltable.getColumnClass(tc.getModelIndex()).getSimpleName().equals("ImageIcon")) {
                 continue;
             }
             tc.setCellRenderer(new OrderChangeBrowse.SomeRenderer());
         }
        
        btdetail.setEnabled(false);
        detailpanel.setVisible(false);
        
       
    }
    
    public String[] getDetail(String id, String po, String cbdetached) {
      
        String jsonString = null;
        if (bsmf.MainFrame.remoteDB && ! bsmf.MainFrame.isSSHConnected) {
            ArrayList<String[]> list = new ArrayList<>();
            list.add(new String[]{"id", "getOrderChangeBrowseDetail"});
            list.add(new String[]{"param1", id});
            list.add(new String[]{"param2", po});
            list.add(new String[]{"param3", cbdetached});
            try {
                jsonString = sendServerPost(list, "", null, "dataServORD"); 
            } catch (IOException ex) {
                bslog(ex);
                return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag(1010, "getDetail")};
            }
        } else {
            jsonString = getOrderChangeBrowseDetail(id, po, cbdetached);  
        }        
        roData = jsonToData(jsonString);
        
        return new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getMessageTag(1125)};
      
    }
   
    public void done_getDetail() {
      modeldetail.setNumRows(0);
         double dols = 0;
         /*
         BlueSeerUtils.clickgear,
                      bsNumber(res.getString("sodc_line")), 
                      res.getString("sodc_item"),
                      bsParseDouble(currformatDouble(res.getDouble("sod_listprice"))),
                      bsParseDouble(currformatDouble(res.getDouble("sodc_price"))),
                      bsNumber(res.getDouble("sod_ord_qty")), 
                      bsNumber(res.getDouble("sodc_qty"))});
         */
         
         for (int j = 0; j < roData.length; j++) { 
            if (roData[j][0].equals("gear")) { 
                roData[j][0] = BlueSeerUtils.clickgear;
            }
        }
         
       if (roData != null) {
        if (roData.length > 0) {
            for (Object[] rowData : roData) {
                dols += (bsParseDouble(rowData[3].toString()) * bsParseDouble(rowData[5].toString()));
                modeldetail.addRow(rowData);
            } 
        }
        labeldettotal.setText(currformatDouble(dols));
       }
       roData = null;
    }
    
    public String[] getOrderChangeBrowseView() {
        String[] x = new String[2];
      
        String fromcust = "";
        String tocust = "";
        
        if (ddfromcust.getSelectedItem() == null || ddfromcust.getSelectedItem().toString().isEmpty()) {
                    fromcust = bsmf.MainFrame.lowchar;
        } else {
            fromcust = ddfromcust.getSelectedItem().toString();
        }
         if (ddtocust.getSelectedItem() == null || ddtocust.getSelectedItem().toString().isEmpty()) {
            tocust = bsmf.MainFrame.hichar;
        } else {
            tocust = ddtocust.getSelectedItem().toString();
        }
        String jsonString = null; 
        if (bsmf.MainFrame.remoteDB && ! bsmf.MainFrame.isSSHConnected) { 
        ArrayList<String[]> list = new ArrayList<>();
        list.add(new String[]{"id","getOrderChangeBrowseView"});
        list.add(new String[]{"fromdate",setDateDB(dcfrom.getDate())});
        list.add(new String[]{"todate",setDateDB(dcto.getDate())});
        list.add(new String[]{"fromcust", fromcust});
        list.add(new String[]{"tocust",tocust});
        list.add(new String[]{"site",ddsite.getSelectedItem().toString()});
        list.add(new String[]{"posearch",tbsearch.getText()});
        list.add(new String[]{"isdetached", String.valueOf(cbdetached.isSelected())});
        try {
                jsonString = sendServerPost(list, "", null, "dataServORD"); 
            } catch (IOException ex) {
                bslog(ex);
                return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag(1010, "getOrderChangeBrowseView")};
            }
        } else {
            jsonString = ordData.getOrderChangeBrowseView(new String[]{setDateDB(dcfrom.getDate()), 
                setDateDB(dcto.getDate()), 
                fromcust, 
                tocust, 
                ddsite.getSelectedItem().toString(), 
                tbsearch.getText(),
                String.valueOf(cbdetached.isSelected())
            });
        }
        
        
        roData = jsonToData(jsonString);
       
      return new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getMessageTag(1125)};
    }

    public void done_getOrderChangeBrowseView() {
       
        int i = 0;
        
        modeltable.setNumRows(0);
        
        if (roData != null) {
        
         // potential data adjustment before adding
        for (int j = 0; j < roData.length; j++) { // 
                if (roData[j][10].equals("change")) { 
                    roData[j][10] = BlueSeerUtils.clickchange;
                } 
                if (roData[j][11].equals("void")) { 
                    roData[j][11] = BlueSeerUtils.clickvoid;
                }
                if (roData[j][12].equals("gear")) { 
                    roData[j][12] = BlueSeerUtils.clickgear;
                }
        }
        
        for (Object[] rowData : roData) {
            // bypass POs that are not in the search criteria
         if (! cbopen.isSelected() && rowData[8].equals("open")) {
                continue;
         }
         if (! cbclose.isSelected() && rowData[8].equals("closed")) {
                continue;
         }
         if (! cbapplied.isSelected() && rowData[8].equals("applied")) {
                continue;
         }
          modeltable.addRow(rowData);
          i++;
        }
        lbltotrecs.setText(String.valueOf(i));
        
        }          
        roData = null;
    }   
     
    public void showEDIKVHeader(String key) {
        javax.swing.JTextArea ta = new javax.swing.JTextArea();
        
        JScrollPane scroll = new JScrollPane(ta);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
                
        ArrayList<String[]> list = getEDIMetaValueHeader(key);
        
        if (list == null || list.isEmpty()) {
            bsmf.MainFrame.show("no header level edi kv data to show");
            return;
        }
        
        ta.setText("  " + "\n\n");
        for (String[] s : list) {
          ta.append("Key: " + s[2] + " \t\t  Value: " + s[3] + "  \n");
        }
        
        ta.setCaretPosition(0);
        ta.setEditable(false);
        
        JDialog dialogHeader = new JDialog();
        dialogHeader.setTitle("Header level Key/Value Pair Information : " + key);
        dialogHeader.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel panelHeader = new JPanel();
        panelHeader.setLayout(new GridBagLayout());
        GridBagConstraints panelGBC = new GridBagConstraints();

        panelGBC.weightx = 1;                    //I want to fill whole panel with JTextArea
        panelGBC.weighty = 1;                    //so both weights =1
        panelGBC.fill = GridBagConstraints.BOTH; //and fill is set to BOTH
        
        panelHeader.add(scroll, panelGBC);
        dialogHeader.add(panelHeader);
        dialogHeader.setPreferredSize(new Dimension(500, 400));
        dialogHeader.pack();
        dialogHeader.setLocationRelativeTo( null );
        dialogHeader.setResizable(false);
        dialogHeader.setVisible(true);
    }
    
    public void showEDIKVDetail(String key, String line) {
        javax.swing.JTextArea ta = new javax.swing.JTextArea();
        
        JScrollPane scroll = new JScrollPane(ta);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
                
        ArrayList<String[]> list = getEDIMetaValueDetail(key, line);
        
        if (list == null || list.isEmpty()) {
            bsmf.MainFrame.show("no detail level edi kv data to show");
            return;
        }
        
        ta.setText("  " + "\n\n");
        for (String[] s : list) {
          ta.append("Key: " + s[2] + " \t\t  Value: " + s[3] + "  \n");
        }
        
        ta.setCaretPosition(0);
        ta.setEditable(false);
        
        JDialog dialog = new JDialog();
        dialog.setTitle("Item level Key/Value Pair Information : " + key);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints panelGBC = new GridBagConstraints();

        panelGBC.weightx = 1;                    //I want to fill whole panel with JTextArea
        panelGBC.weighty = 1;                    //so both weights =1
        panelGBC.fill = GridBagConstraints.BOTH; //and fill is set to BOTH
        
        panel.add(scroll, panelGBC);
        dialog.add(panel);
        dialog.setPreferredSize(new Dimension(500, 400));
        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setResizable(false);
        dialog.setVisible(true);
    }
    
    public static void createExportFile(String data) {
       FileDialog fDialog;
        fDialog = new FileDialog(new Frame(), "Save", FileDialog.SAVE);
        fDialog.setVisible(true);
       // fDialog.setFile("data.csv");
        String path = fDialog.getDirectory() + fDialog.getFile();
        File f = new File(path);
        BufferedWriter output = null;
        
        String[] dar = data.split("=_=");
        try {
            output = new BufferedWriter(new FileWriter(f));
            for (String d : dar) {
                   output.write(d);
                   output.write("\n");
            }
        } catch (IOException ex) {
               bslog(ex);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ex) {
                    bslog(ex);
                }
            }
        }
        
        
    }
        
    public String[] serverPostExportOrderChange() throws IOException {
        String[] x = new String[2];
      
        String fromcust = "";
        String tocust = "";
        if (ddfromcust.getSelectedItem() == null || ddfromcust.getSelectedItem().toString().isEmpty()) {
                    fromcust = bsmf.MainFrame.lowchar;
        } else {
            fromcust = ddfromcust.getSelectedItem().toString();
        }
         if (ddtocust.getSelectedItem() == null || ddtocust.getSelectedItem().toString().isEmpty()) {
            tocust = bsmf.MainFrame.hichar;
        } else {
            tocust = ddtocust.getSelectedItem().toString();
        }
        ArrayList<String[]> list = new ArrayList<String[]>();
        list.add(new String[]{"id","exportOrderChange"});
        list.add(new String[]{"fromdate",setDateDB(dcfrom.getDate())});
        list.add(new String[]{"todate",setDateDB(dcto.getDate())});
        list.add(new String[]{"fromcust", fromcust});
        list.add(new String[]{"tocust",tocust});
        list.add(new String[]{"site",ddsite.getSelectedItem().toString()});
        
      //  rData = sendServerPost(list, postData, null, "dataServORD");
        rsData = sendServerPost(list, "", null, "dataServORD");
        
        x[0] = "0";
        x[1] = "Processing complete";
       
        return x;
    }
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        tablepanel = new javax.swing.JPanel();
        summarypanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tablereport = new javax.swing.JTable();
        detailpanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        tabledetail = new javax.swing.JTable();
        jPanel2 = new javax.swing.JPanel();
        btdetail = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        btRun = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        ddtocust = new javax.swing.JComboBox();
        ddfromcust = new javax.swing.JComboBox();
        ddsite = new javax.swing.JComboBox();
        cbclose = new javax.swing.JCheckBox();
        cbopen = new javax.swing.JCheckBox();
        btprint = new javax.swing.JButton();
        cbapplied = new javax.swing.JCheckBox();
        btexport = new javax.swing.JButton();
        tbsearch = new javax.swing.JTextField();
        tbsearch.putClientProperty("JTextField.placeholderText", "Search...");
        tbsearch.putClientProperty("JTextField.showClearButton", true);
        jLabel2 = new javax.swing.JLabel();
        btclear = new javax.swing.JButton();
        dcfrom = new com.toedter.calendar.JDateChooser();
        jLabel7 = new javax.swing.JLabel();
        dcto = new com.toedter.calendar.JDateChooser();
        jLabel9 = new javax.swing.JLabel();
        cbdetached = new javax.swing.JCheckBox();
        jPanel3 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        lbltotrecs = new javax.swing.JLabel();
        labeldettotal = new javax.swing.JLabel();

        setBackground(new java.awt.Color(0, 102, 204));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("EDI Order Change Browse"));
        jPanel1.setName("panelmain"); // NOI18N

        tablepanel.setLayout(new javax.swing.BoxLayout(tablepanel, javax.swing.BoxLayout.LINE_AXIS));

        summarypanel.setLayout(new java.awt.BorderLayout());

        tablereport.setAutoCreateRowSorter(true);
        tablereport.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tablereport.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tablereportMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(tablereport);

        summarypanel.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        tablepanel.add(summarypanel);

        detailpanel.setLayout(new java.awt.BorderLayout());

        tabledetail.setAutoCreateRowSorter(true);
        tabledetail.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tabledetail.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tabledetailMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(tabledetail);

        detailpanel.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        tablepanel.add(detailpanel);

        btdetail.setText("Hide Detail");
        btdetail.setName("bthidedetail"); // NOI18N
        btdetail.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btdetailActionPerformed(evt);
            }
        });

        jLabel4.setText("To Customer");
        jLabel4.setName("lbltovend"); // NOI18N

        btRun.setText("Run");
        btRun.setName("btrun"); // NOI18N
        btRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btRunActionPerformed(evt);
            }
        });

        jLabel5.setText("Site");
        jLabel5.setName("lblsite"); // NOI18N

        jLabel1.setText("From Customer");
        jLabel1.setName("lblfromvend"); // NOI18N

        cbclose.setText("Closed");
        cbclose.setName("cbclosed"); // NOI18N

        cbopen.setText("Open");
        cbopen.setName("cbopen"); // NOI18N

        btprint.setText("Print/PDF");
        btprint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btprintActionPerformed(evt);
            }
        });

        cbapplied.setText("Applied");

        btexport.setText("CSV Export");
        btexport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btexportActionPerformed(evt);
            }
        });

        jLabel2.setText("PO Search");

        btclear.setText("Clear");
        btclear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btclearActionPerformed(evt);
            }
        });

        dcfrom.setDateFormatString("yyyy-MM-dd");

        jLabel7.setText("From Date");

        dcto.setDateFormatString("yyyy-MM-dd");

        jLabel9.setText("To Date");

        cbdetached.setText("detached");
        cbdetached.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbdetachedActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel7)
                            .addComponent(jLabel9))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dcto, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addComponent(dcfrom, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(68, 68, 68)))
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel2)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ddfromcust, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ddtocust, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(cbdetached)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(cbopen)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(cbclose)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(cbapplied))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(ddsite, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btRun)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btclear)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btdetail)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btprint)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btexport))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(tbsearch, javax.swing.GroupLayout.PREFERRED_SIZE, 94, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(ddfromcust, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(btRun)
                        .addComponent(btdetail)
                        .addComponent(ddsite, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel5)
                        .addComponent(btprint)
                        .addComponent(btexport)
                        .addComponent(btclear))
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jLabel7)
                        .addComponent(dcfrom, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel4)
                        .addComponent(ddtocust, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(cbdetached))
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cbclose)
                        .addComponent(cbopen)
                        .addComponent(cbapplied))
                    .addComponent(dcto, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbsearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel8.setText("Total Records:");
        jLabel8.setName("lbltotalqty"); // NOI18N

        lbltotrecs.setText("0");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(83, 83, 83)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbltotrecs, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbltotrecs, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addGap(70, 70, 70))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 26, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(labeldettotal, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(tablepanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(labeldettotal, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tablepanel, javax.swing.GroupLayout.DEFAULT_SIZE, 371, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btRunActionPerformed
       executeTask("getOrderChangeBrowseView", null);
    }//GEN-LAST:event_btRunActionPerformed

    private void btdetailActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btdetailActionPerformed
       detailpanel.setVisible(false);
       labeldettotal.setText("");
       btdetail.setEnabled(false);
    }//GEN-LAST:event_btdetailActionPerformed

    private void tablereportMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tablereportMouseClicked
        
        int row = tablereport.rowAtPoint(evt.getPoint());
        int col = tablereport.columnAtPoint(evt.getPoint());
        if ( col == 0) {
               // getdetail(tablereport.getValueAt(row, 1).toString(), tablereport.getValueAt(row, 3).toString());  // soc_id, soc_po
                executeTask("getOrderChangeBrowseDetail", new String[]{tablereport.getValueAt(row, 1).toString(), tablereport.getValueAt(row, 3).toString(), BlueSeerUtils.boolToString(cbdetached.isSelected())});
                btdetail.setEnabled(true);
                detailpanel.setVisible(true);
                currentid = tablereport.getValueAt(row, 1).toString();
              
        }
        
        if (! tablereport.getValueAt(row, 9).toString().equals("detached")) {
            if ( col == 10 && ! tablereport.getValueAt(row, 9).toString().equals("closed") &&
                     ! tablereport.getValueAt(row, 9).toString().equals("applied")) {
                    applyOrderChange(tablereport.getValueAt(row, 1).toString(), tablereport.getValueAt(row, 3).toString());
                    bsmf.MainFrame.show("Order Change Committed");
            }
            if ( col == 11 && ! tablereport.getValueAt(row, 9).toString().equals("closed") &&
                    ! tablereport.getValueAt(row, 9).toString().equals("applied")) {
                    updateOrderChangeStatus(tablereport.getValueAt(row, 1).toString(), "closed");
                    bsmf.MainFrame.show("Order Change Closed");
            }
            
        }
        
        if ( col == 12 ) {
                    showEDIKVHeader(tablereport.getValueAt(row, 1).toString());
            }
       
    }//GEN-LAST:event_tablereportMouseClicked

    private void btprintActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btprintActionPerformed
        
        if (tablereport != null && modeltable.getRowCount() > 0) {
          // OVData.printJTableToJasper("Order Change Report", tablereport, "genericJTableL9.jasper" );
            String[] rec;
            String[] columnnames = new String[11];
            List<Object[]> list = new ArrayList<>();
            for (int j = 0; j < tablereport.getRowCount(); j++) {
                 rec = new String[]{tablereport.getValueAt(j, 2).toString(),
                   tablereport.getValueAt(j, 3).toString(),
                   tablereport.getValueAt(j, 4).toString(),
                   tablereport.getValueAt(j, 5).toString(),
                   tablereport.getValueAt(j, 6).toString(),
                   tablereport.getValueAt(j, 7).toString(),
                   tablereport.getValueAt(j, 8).toString(),
                   tablereport.getValueAt(j, 9).toString(),
                   tablereport.getValueAt(j, 10).toString(),
                   tablereport.getValueAt(j, 11).toString()}; 
                 list.add(rec);
             }
            HashMap hm = new HashMap();
            hm.put("REPORT_TITLE", "Sales Order Browse Report");
            hm.put("REPORT_RESOURCE_BUNDLE", bsmf.MainFrame.tags);
            for (int j = 1; j < tablereport.getColumnCount() - 2; j++) {
               hm.put("d" + (j - 1),  tablereport.getColumnName(j));
               columnnames[j - 1] = "COLUMN_" + (j - 1);
            }
            JRDataSource datasource = new ListOfArrayDataSource(list, columnnames);
            // assumes explicit jasper file name is larger than 3 chars.....if 3 chars or less...then must be key based L8, L8C, etc
            // type = "L8C";  ...or type = genericJTableL8.jasper
            // String jasperfile = (type.length() > 3) ? jasperfile = type  : OVData.getCodeValueByCodeKey("jasper", type)  ;
            Path template = FileSystems.getDefault().getPath(cleanDirString(getSystemJasperDirectory()) + "genericJTableL10.jasper");
            JasperPrint jasperPrint; 
            try {
             jasperPrint = JasperFillManager.fillReport(template.toString(), hm, datasource );
             JasperViewer jasperViewer = new JasperViewer(jasperPrint, false);
               jasperViewer.setVisible(true);
                    jasperViewer.setTitle("Viewer");
                    jasperViewer.setIconImage(null);
                    jasperViewer.setFitPageZoomRatio();
               //  JasperExportManager.exportReportToPdfFile(jasperPrint,"temp/ivprt.pdf");
           } catch (JRException ex) {
               MainFrame.bslog(ex);
           }
        }
    }//GEN-LAST:event_btprintActionPerformed

    private void btexportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btexportActionPerformed
        if (tablereport != null && modeltable.getRowCount() > 0) { // still necessary to click run if only for the exportOrderDetail (local grab)
            if (bsmf.MainFrame.remoteDB) {
                disableAll(); 
                executeTask("exportOrderChange", null);
            } else {
               OVData.exportOrderChange(tablereport);
            }
       }
        
    }//GEN-LAST:event_btexportActionPerformed

    private void tabledetailMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tabledetailMouseClicked
        int row = tabledetail.rowAtPoint(evt.getPoint());
        int col = tabledetail.columnAtPoint(evt.getPoint());
        if ( col == 0) {
          showEDIKVDetail(currentid, tabledetail.getValueAt(row, 1).toString());
        }
    }//GEN-LAST:event_tabledetailMouseClicked

    private void cbdetachedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbdetachedActionPerformed
        if (cbdetached.isSelected()) {
            btexport.setEnabled(false);
        } else {
            btexport.setEnabled(true);
        }
    }//GEN-LAST:event_cbdetachedActionPerformed

    private void btclearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btclearActionPerformed
        modeldetail.setNumRows(0);
        tbsearch.setText("");
        cbdetached.setSelected(false);
    }//GEN-LAST:event_btclearActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btRun;
    private javax.swing.JButton btclear;
    private javax.swing.JButton btdetail;
    private javax.swing.JButton btexport;
    private javax.swing.JButton btprint;
    private javax.swing.JCheckBox cbapplied;
    private javax.swing.JCheckBox cbclose;
    private javax.swing.JCheckBox cbdetached;
    private javax.swing.JCheckBox cbopen;
    private com.toedter.calendar.JDateChooser dcfrom;
    private com.toedter.calendar.JDateChooser dcto;
    private javax.swing.JComboBox ddfromcust;
    private javax.swing.JComboBox ddsite;
    private javax.swing.JComboBox ddtocust;
    private javax.swing.JPanel detailpanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel labeldettotal;
    private javax.swing.JLabel lbltotrecs;
    private javax.swing.JPanel summarypanel;
    private javax.swing.JTable tabledetail;
    private javax.swing.JPanel tablepanel;
    private javax.swing.JTable tablereport;
    private javax.swing.JTextField tbsearch;
    // End of variables declaration//GEN-END:variables
}
