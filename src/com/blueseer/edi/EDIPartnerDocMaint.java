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

package com.blueseer.edi;

import bsmf.MainFrame;
import com.blueseer.utl.OVData;
import com.blueseer.utl.BlueSeerUtils;
import static bsmf.MainFrame.tags;
import static com.blueseer.edi.ediData.addEdiMstr;
import static com.blueseer.edi.ediData.deleteEdiMstr;
import com.blueseer.edi.ediData.edi_mstr;
import static com.blueseer.edi.ediData.getEdiMstr;
import static com.blueseer.edi.ediData.getMapMstr;
import static com.blueseer.edi.ediData.updateEdiMstr;
import static com.blueseer.utl.BlueSeerUtils.asciivalues;
import static com.blueseer.utl.BlueSeerUtils.callDialog;
import static com.blueseer.utl.BlueSeerUtils.getClassLabelTag;
import static com.blueseer.utl.BlueSeerUtils.getMessageTag;
import static com.blueseer.utl.BlueSeerUtils.luModel;
import static com.blueseer.utl.BlueSeerUtils.luTable;
import static com.blueseer.utl.BlueSeerUtils.lual;
import static com.blueseer.utl.BlueSeerUtils.ludialog;
import static com.blueseer.utl.BlueSeerUtils.luinput;
import static com.blueseer.utl.BlueSeerUtils.luml;
import static com.blueseer.utl.BlueSeerUtils.lurb1;
import static com.blueseer.utl.BlueSeerUtils.lurb2;
import com.blueseer.utl.DTData;
import com.blueseer.utl.EDData;
import static com.blueseer.utl.EDData.deleteEDIAttributeRecord;
import static com.blueseer.utl.EDData.getEDIPartnerDesc;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingWorker;
import org.kordamp.ikonli.swing.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

/**
 *
 * @author vaughnte
 */
public class EDIPartnerDocMaint extends javax.swing.JPanel {

    DefaultListModel listmodel = new DefaultListModel();
    boolean isLoad = false;
    public static edi_mstr em = null;
    public static ArrayList<String> attrlist = new ArrayList<>();
    String defaultsite = "";
    
    /**
     * Creates new form CarrierMaintPanel
     */
    public EDIPartnerDocMaint() {
        initComponents();
        setLanguageTags(this);
    }
    
    public void executeTask(BlueSeerUtils.dbaction x, String[] y) { 
      
        class Task extends SwingWorker<String[], Void> {
       
          String type = "";
          String[] key = null;
          
          public Task(BlueSeerUtils.dbaction type, String[] key) { 
              this.type = type.name();
              this.key = key;
          } 
           
        @Override
        public String[] doInBackground() throws Exception {
            String[] message = new String[2];
            message[0] = "";
            message[1] = "";
            
            
             switch(this.type) {
                case "add":
                    message = addRecord(key);
                    break;
                case "update":
                    message = updateRecord(key);
                    break;
                case "delete":
                    message = deleteRecord(key);    
                    break;
                case "get":
                    message = getRecord(key);    
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
           if (this.type.equals("delete")) {
             initvars(null);  
           } else if (this.type.equals("get")) {
             updateForm();
             ddkey.requestFocus();
           } else if (this.type.equals("add") && message[0].equals("0")) {
             initvars(key);
           } else if (this.type.equals("update") && message[0].equals("0")) {
             initvars(key);    
           } else {
             initvars(null);  
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
      
    public void setPanelComponentState(Object myobj, boolean b) {
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
        
        if (panel != null) {
        panel.setEnabled(b);
        Component[] components = panel.getComponents();
        
            for (Component component : components) {
                if (component instanceof JLabel || component instanceof JTable ) {
                    continue;
                }
                if (component instanceof JPanel) {
                    setPanelComponentState((JPanel) component, b);
                }
                if (component instanceof JTabbedPane) {
                    setPanelComponentState((JTabbedPane) component, b);
                }
                if (component instanceof JScrollPane) {
                    setPanelComponentState((JScrollPane) component, b);
                }
                
                component.setEnabled(b);
            }
        }
            if (tabpane != null) {
                tabpane.setEnabled(b);
                Component[] componentspane = tabpane.getComponents();
                for (Component component : componentspane) {
                    if (component instanceof JLabel || component instanceof JTable ) {
                        continue;
                    }
                    if (component instanceof JPanel) {
                        setPanelComponentState((JPanel) component, b);
                    }
                    
                    component.setEnabled(b);
                    
                }
            }
            if (scrollpane != null) {
                scrollpane.setEnabled(b);
                JViewport viewport = scrollpane.getViewport();
                Component[] componentspane = viewport.getComponents();
                for (Component component : componentspane) {
                    if (component instanceof JLabel || component instanceof JTable ) {
                        continue;
                    }
                    component.setEnabled(b);
                }
            }
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
    
    public void setComponentDefaultValues() {
        isLoad = true;
        
        ArrayList<String[]> initDataSets = ediData.getEDIInit(this.getClass().getName(), bsmf.MainFrame.userid);
        
        lblmessg.setText("");
        lblmessg.setForeground(Color.black);
        lblpartner.setText("");
        
        lblelem.setText("");
        lblseg.setText("");
        lblsub.setText("");
        
         tbrcvisa.setText("");
        tbrcvq.setText("");
        tbrcvgs.setText("");
        tbsndisa.setText("");
        tbsndgs.setText("");
        tbsndq.setText("");
        tbversion.setText("");
        tbsupplier.setText("");
        tbelement.setText("");
        tbsegment.setText("");
        tbsub.setText("");
        tbfilepath.setText("");
        tbIFS.setText("");
        tbOFS.setText("");
        tbfileprefix.setText("");
        tbfilesuffix.setText("");
        ddattributekey.setSelectedIndex(0);
        tbattributevalue.setText("");
        cbenvelopeall.setSelected(false);
        cbuna.setSelected(false);
        cbung.setSelected(false);
        cbemail.setSelected(false);
        
        listAttributes.setModel(listmodel); 
        ddsite.removeAllItems();
        dddoc.removeAllItems();
        ddoutdoctype.removeAllItems();
        
        for (String[] s : initDataSets) {
            if (s[0].equals("site")) {
              defaultsite = s[1];  
            }
                      
            if (s[0].equals("sites")) {
              ddsite.addItem(s[1]); 
            }
            if (s[0].equals("doctypes")) {
              dddoc.addItem(s[1]); 
            }
            if (s[0].equals("doctypes")) {
              ddoutdoctype.addItem(s[1]); 
            }
            if (s[0].equals("maps")) {
              ddmap.addItem(s[1]); 
            }
            if (s[0].equals("partners")) {
              ddkey.addItem(s[1]); 
            }
        }
        
        if (ddsite.getItemCount() > 0) {
            ddsite.setSelectedItem(defaultsite);
        }
        dddoc.insertItemAt("", 0);
        ddoutdoctype.insertItemAt("", 0);
        ddmap.insertItemAt("", 0);
        ddkey.insertItemAt("", 0);
        dddoc.setSelectedIndex(0);
        ddoutdoctype.setSelectedIndex(0);
        ddmap.setSelectedIndex(0);
        ddkey.setSelectedIndex(0);
      
        
       isLoad = false;
    }
    
    public void newAction(String x) {
       setPanelComponentState(this, true);
        setComponentDefaultValues();
        BlueSeerUtils.message(new String[]{"0",BlueSeerUtils.addRecordInit});
        btupdate.setEnabled(false);
        btdelete.setEnabled(false);
        btnew.setEnabled(false);
        ddkey.requestFocus();
    }
    
    public void setAction(String[] x) {
        if (x[0].equals("0")) { 
           setPanelComponentState(this, true);
           btadd.setEnabled(false);
           ddkey.setEnabled(false);
           dddoc.setEnabled(false);
           tbrcvgs.setEnabled(false);
           tbsndgs.setEnabled(false);
           btcopy.setEnabled(true);
        } else {
            btcopy.setEnabled(false);
        } 
    }
    
    public boolean validateInput(BlueSeerUtils.dbaction x) {
        boolean b = true;
               
                if (ddkey.getSelectedItem().toString().isEmpty()) {
                    b = false;
                    bsmf.MainFrame.show(getMessageTag(1024));
                    ddkey.requestFocus();
                    return b;
                }
                
                if (dddoc.getSelectedItem().toString().isEmpty()) {
                    b = false;
                    bsmf.MainFrame.show(getMessageTag(1024));
                    dddoc.requestFocus();
                    return b;
                }
                
                if (tbsndgs.getText().isEmpty()) {
                    b = false;
                    bsmf.MainFrame.show(getMessageTag(1024));
                    tbsndgs.requestFocus();
                    return b;
                }
                
                if (tbrcvgs.getText().isEmpty()) {
                    b = false;
                    bsmf.MainFrame.show(getMessageTag(1024));
                    tbrcvgs.requestFocus();
                    return b;
                }
                
                if (ddmap.getSelectedItem().toString().isEmpty()) {
                    b = false;
                    bsmf.MainFrame.show(getMessageTag(1024));
                    ddmap.requestFocus();
                    return b;
                }
               
        return b;
    }
    
    public String[] addRecord(String[] x) {
        String[] m = addEdiMstr(createRecord());
         return m;
    }
    
    public String[] updateRecord(String[] x) {
        String[] m = updateEdiMstr(createRecord());
         return m;
    }
    
    public String[] deleteRecord(String[] x) {
       String[] m = new String[2];
        boolean proceed = bsmf.MainFrame.warn(getMessageTag(1004));
        if (proceed) {
         m = deleteEdiMstr(createRecord()); 
         initvars(null);
        } else {
           m = new String[] {BlueSeerUtils.ErrorBit, BlueSeerUtils.deleteRecordCanceled}; 
        }
         return m;
    }
       
    public String[] getRecord(String[] key) {
        em = getEdiMstr(key);
        attrlist = EDData.getEDIAttributesList(em.edi_doctypeout(), em.edi_sndgs(), em.edi_rcvgs() );
        return em.m();
    }
     
    public edi_mstr createRecord() { 
        String envelopeall = (cbenvelopeall.isSelected()) ? "1" : "0";
        String una = (cbuna.isSelected()) ? "1" : "0";
        String ung = (cbung.isSelected()) ? "1" : "0";
        String mflag = (cbemail.isSelected()) ? "1" : "0";
        String fa = (cbfa.isSelected()) ? "1" : "0";
                
        edi_mstr x = new edi_mstr(null, 
                ddkey.getSelectedItem().toString(),
                dddoc.getSelectedItem().toString(),
                tbsndisa.getText(),
                tbsndq.getText(),
                tbsndgs.getText(),
                ddmap.getSelectedItem().toString(),
                tbelement.getText(),
                tbsegment.getText(),
                tbsub.getText(),
                tbfileprefix.getText(),
                tbfilesuffix.getText(),
                tbfilepath.getText(),
                tbversion.getText(),
                tbrcvisa.getText(),
                tbrcvgs.getText(),
                tbrcvq.getText(),
                tbsupplier.getText(),
                ddoutdoctype.getSelectedItem().toString(),
                ddoutfiletype.getSelectedItem().toString(),
                tbIFS.getText(),
                tbOFS.getText(),      
                ddinfiletype.getSelectedItem().toString(),      
                fa,
                envelopeall,
                una,
                ung,
                ddsite.getSelectedItem().toString(),
                mflag
                );
        return x;
    }
    
    public void initvars(String[] arg) {
       isLoad = true; 
       jTabbedPane1.removeAll();
       jTabbedPane1.add("Main", panelMain);
       jTabbedPane1.add("Attributes", panelOutbound);
       setPanelComponentState(this, false); 
       setComponentDefaultValues();       
       btnew.setEnabled(true);
       btlookup.setEnabled(true);
       isLoad = false; 
       
        if (arg != null && arg.length > 0) {
            executeTask(BlueSeerUtils.dbaction.get, arg); // 4 args required
        }
       
    }
    
    public void lookUpFrame() {
        
        luinput.removeActionListener(lual);
        lual = new ActionListener() {
        public void actionPerformed(ActionEvent event) {
        if (lurb1.isSelected()) {  
         luModel = DTData.getEDICustBrowseUtil(luinput.getText(),0, "edi_id", defaultsite);
        } else if (lurb2.isSelected()) {
         luModel = DTData.getEDICustBrowseUtil(luinput.getText(),0, "edi_doc", defaultsite);   
        } else {
         luModel = DTData.getEDICustBrowseUtil(luinput.getText(),0, "edi_sndgs", defaultsite);   
        }
        luTable.setModel(luModel);
        luTable.getColumnModel().getColumn(0).setMaxWidth(50);
        if (luModel.getRowCount() < 1) {
            ludialog.setTitle(getMessageTag(1001));
        } else {
            ludialog.setTitle(getMessageTag(1002, String.valueOf(luModel.getRowCount())));
        }
        }
        };
        luinput.addActionListener(lual);
        
        luTable.removeMouseListener(luml);
        luml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable)e.getSource();
                int row = target.getSelectedRow();
                int column = target.getSelectedColumn();
                if ( column == 0) {
                ludialog.dispose();
                initvars(new String[]{target.getValueAt(row,1).toString(), target.getValueAt(row,2).toString(), target.getValueAt(row,4).toString(), target.getValueAt(row,6).toString()});
                }
            }
        };
        luTable.addMouseListener(luml);
      
       
        callDialog(getClassLabelTag("lblcode", this.getClass().getSimpleName()), 
                getClassLabelTag("lbldoctype", this.getClass().getSimpleName()),
                getClassLabelTag("lblsndgs", this.getClass().getSimpleName()));
        
    }

    public void lookUpFrameASCIIEle() {
        luTable.removeMouseListener(luml);
        luModel = DTData.getASCIIChartDT(0, 128);
        luTable.setModel(luModel);
        
        luml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable)e.getSource();
                int row = target.getSelectedRow();
                int column = target.getSelectedColumn();
                if ( column == 0 || column == 1) {
                ludialog.dispose();
                tbelement.setText(target.getValueAt(row,0).toString());
                lblelem.setText(target.getValueAt(row,1).toString());
                }
            }
        };
        luTable.addMouseListener(luml);
        
        callDialog();
        
        
    }

    public void lookUpFrameASCIISeg() {
        luTable.removeMouseListener(luml);
        luModel = DTData.getASCIIChartDT(0, 128);
        luTable.setModel(luModel);
        
        luml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable)e.getSource();
                int row = target.getSelectedRow();
                int column = target.getSelectedColumn();
                if ( column == 0 || column == 1) {
                ludialog.dispose();
                tbsegment.setText(target.getValueAt(row,0).toString());
                lblseg.setText(target.getValueAt(row,1).toString());
                }
            }
        };
        luTable.addMouseListener(luml);
        
        callDialog();
        
        
    }

    public void lookUpFrameASCIISub() {
        luTable.removeMouseListener(luml);
        luModel = DTData.getASCIIChartDT(0, 128);
        luTable.setModel(luModel);
        
        luml = new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable)e.getSource();
                int row = target.getSelectedRow();
                int column = target.getSelectedColumn();
                if ( column == 0 || column == 1) {
                ludialog.dispose();
                tbsub.setText(target.getValueAt(row,0).toString());
                lblsub.setText(target.getValueAt(row,1).toString());
                }
            }
        };
        luTable.addMouseListener(luml);
        
        callDialog();
        
        
    }

    public void updateForm() {
        
        ddkey.setSelectedItem(em.edi_id());
        dddoc.setSelectedItem(em.edi_doc());
        ddoutdoctype.setSelectedItem(em.edi_doctypeout());
        ddoutfiletype.setSelectedItem(em.edi_filetypeout());
        ddinfiletype.setSelectedItem(em.edi_filetype());
        tbIFS.setText(em.edi_ifs());
        tbOFS.setText(em.edi_ofs());
        tbrcvisa.setText(em.edi_rcvisa());
        tbrcvq.setText(em.edi_rcvq());
        tbrcvgs.setText(em.edi_rcvgs());
        ddmap.setSelectedItem(em.edi_map());
        tbelement.setText(em.edi_eledelim());
        tbsegment.setText(em.edi_segdelim());
        tbsub.setText(em.edi_subdelim());
        tbfileprefix.setText(em.edi_fileprefix());
        tbfilesuffix.setText(em.edi_filesuffix());
        tbfilepath.setText(em.edi_filepath());
        tbversion.setText(em.edi_version());
        tbsndisa.setText(em.edi_sndisa());
        tbsndgs.setText(em.edi_sndgs());
        tbsndq.setText(em.edi_sndq());
        tbsupplier.setText(em.edi_supcode());
        cbfa.setSelected(BlueSeerUtils.ConvertStringToBool(em.edi_fa_required()));
        cbenvelopeall.setSelected(BlueSeerUtils.ConvertStringToBool(em.edi_envelopeall()));
        cbuna.setSelected(BlueSeerUtils.ConvertStringToBool(em.edi_una())); 
        cbung.setSelected(BlueSeerUtils.ConvertStringToBool(em.edi_ung()));
        cbemail.setSelected(BlueSeerUtils.ConvertStringToBool(em.edi_mflag()));
        ddsite.setSelectedItem(em.edi_site());
        
        listmodel.removeAllElements();
        for (String x : attrlist) {
            listmodel.addElement(x);
        }
        
        setAction(em.m());
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        panelMain = new javax.swing.JPanel();
        btdelete = new javax.swing.JButton();
        btadd = new javax.swing.JButton();
        btupdate = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        tbsndgs = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        tbrcvgs = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        tbrcvq = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        tbsndisa = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        tbsndq = new javax.swing.JTextField();
        btnew = new javax.swing.JButton();
        dddoc = new javax.swing.JComboBox<>();
        tbrcvisa = new javax.swing.JTextField();
        btlookup = new javax.swing.JButton();
        ddmap = new javax.swing.JComboBox<>();
        ddkey = new javax.swing.JComboBox<>();
        btclear = new javax.swing.JButton();
        lblpartner = new javax.swing.JLabel();
        btcopy = new javax.swing.JButton();
        ddsite = new javax.swing.JComboBox<>();
        jLabel22 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        ddoutdoctype = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();
        ddoutfiletype = new javax.swing.JComboBox<>();
        jLabel19 = new javax.swing.JLabel();
        tbIFS = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        tbOFS = new javax.swing.JTextField();
        ddinfiletype = new javax.swing.JComboBox<>();
        jLabel21 = new javax.swing.JLabel();
        lblmessg = new javax.swing.JLabel();
        panelOutbound = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        tbfilepath = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        tbsupplier = new javax.swing.JTextField();
        tbelement = new javax.swing.JTextField();
        tbfilesuffix = new javax.swing.JTextField();
        tbsegment = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        tbversion = new javax.swing.JTextField();
        tbfileprefix = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        tbsub = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        lblsuffix = new javax.swing.JLabel();
        cbenvelopeall = new javax.swing.JCheckBox();
        cbuna = new javax.swing.JCheckBox();
        cbfa = new javax.swing.JCheckBox();
        cbung = new javax.swing.JCheckBox();
        lblelem = new javax.swing.JLabel();
        lblseg = new javax.swing.JLabel();
        lblsub = new javax.swing.JLabel();
        btlookupElement = new javax.swing.JButton();
        btlookupSegment = new javax.swing.JButton();
        btlookupSubElement = new javax.swing.JButton();
        cbemail = new javax.swing.JCheckBox();
        jPanel4 = new javax.swing.JPanel();
        btdeleteattribute = new javax.swing.JButton();
        btaddattribute = new javax.swing.JButton();
        tbattributevalue = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        listAttributes = new javax.swing.JList<>();
        ddattributekey = new javax.swing.JComboBox<>();

        setBackground(new java.awt.Color(0, 102, 204));
        add(jTabbedPane1);

        panelMain.setBorder(javax.swing.BorderFactory.createTitledBorder("Partner Transaction Maintenance"));
        panelMain.setName("panelmain"); // NOI18N
        panelMain.setPreferredSize(new java.awt.Dimension(752, 543));

        btdelete.setText("delete");
        btdelete.setName("btdelete"); // NOI18N
        btdelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btdeleteActionPerformed(evt);
            }
        });

        btadd.setText("add");
        btadd.setName("btadd"); // NOI18N
        btadd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btaddActionPerformed(evt);
            }
        });

        btupdate.setText("update");
        btupdate.setName("btupdate"); // NOI18N
        btupdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btupdateActionPerformed(evt);
            }
        });

        jLabel3.setText("DocType");
        jLabel3.setName("lbldoctype"); // NOI18N

        jLabel6.setText("Rcv Q");
        jLabel6.setName("lblrcvq"); // NOI18N

        jLabel5.setText("Code:");
        jLabel5.setName("lblcode"); // NOI18N

        jLabel15.setText("Snd GS/UNG");
        jLabel15.setName("lblsndgs"); // NOI18N

        jLabel7.setText("Rcv GS/UNG");
        jLabel7.setName("lblrcvgs"); // NOI18N

        jLabel1.setText("Rcv ISA/UNB");
        jLabel1.setName("lblrcvisa"); // NOI18N

        jLabel13.setText("Snd ISA/UNB");
        jLabel13.setName("lblsndisa"); // NOI18N

        jLabel9.setText("Map");
        jLabel9.setName("lblmap"); // NOI18N

        jLabel16.setText("Snd Q");
        jLabel16.setName("lblsndq"); // NOI18N

        btnew.setText("New");
        btnew.setName("btnew"); // NOI18N
        btnew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnewActionPerformed(evt);
            }
        });

        dddoc.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dddocActionPerformed(evt);
            }
        });

        btlookup.setIcon(FontIcon.of(MaterialDesignM.MAGNIFY, 16)); // NOI18N
        btlookup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btlookupActionPerformed(evt);
            }
        });

        ddmap.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ddmapActionPerformed(evt);
            }
        });

        ddkey.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ddkeyActionPerformed(evt);
            }
        });

        btclear.setText("Clear");
        btclear.setName("btclear"); // NOI18N
        btclear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btclearActionPerformed(evt);
            }
        });

        btcopy.setText("Copy");
        btcopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btcopyActionPerformed(evt);
            }
        });

        jLabel22.setText("Site");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(14, 14, 14)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel7, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel13, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addGap(3, 3, 3))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel6, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel9, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel15, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel16, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(tbrcvq, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jLabel22)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ddsite, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ddmap, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(tbsndq, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addGap(1, 1, 1)
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(ddkey, javax.swing.GroupLayout.PREFERRED_SIZE, 139, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(lblpartner, javax.swing.GroupLayout.PREFERRED_SIZE, 194, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                        .addComponent(tbrcvisa, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 161, Short.MAX_VALUE)
                                        .addComponent(tbsndisa, javax.swing.GroupLayout.Alignment.LEADING))
                                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                        .addComponent(tbsndgs, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
                                        .addComponent(tbrcvgs, javax.swing.GroupLayout.Alignment.LEADING)))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(dddoc, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(28, 28, 28)
                                .addComponent(btlookup, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(9, 9, 9)
                                .addComponent(btnew)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btclear)
                                .addGap(0, 10, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btcopy)
                                .addContainerGap())))))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(lblpartner, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnew)
                        .addComponent(btclear))
                    .addComponent(btlookup)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(ddkey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel5)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btcopy)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel3)
                        .addComponent(dddoc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbsndgs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15))
                .addGap(9, 9, 9)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbrcvgs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ddmap, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbsndq, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(tbsndisa, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbrcvq, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(ddsite, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbrcvisa, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(5, 5, 5))
        );

        jLabel2.setText("outDocType");
        jLabel2.setName("lbloutdoctype"); // NOI18N

        jLabel8.setText("outFileType");
        jLabel8.setName("lbloutfiletype"); // NOI18N

        ddoutfiletype.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "DB", "CSV", "FF", "JSON", "UNE", "X12", "XML" }));

        jLabel19.setText("ISF File");
        jLabel19.setName("lblifs"); // NOI18N

        jLabel20.setText("OSF File");
        jLabel20.setName("lblofs"); // NOI18N

        ddinfiletype.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "DB", "CSV", "FF", "JSON", "UNE", "X12", "XML" }));

        jLabel21.setText("InFileType");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel8)
                    .addComponent(jLabel19)
                    .addComponent(jLabel20)
                    .addComponent(jLabel21))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(ddinfiletype, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ddoutdoctype, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ddoutfiletype, 0, 112, Short.MAX_VALUE))
                    .addComponent(tbIFS, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbOFS))
                .addGap(79, 79, 79))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ddinfiletype, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel21))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(ddoutdoctype, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(ddoutfiletype, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbIFS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel19))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbOFS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel20))
                .addContainerGap(106, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout panelMainLayout = new javax.swing.GroupLayout(panelMain);
        panelMain.setLayout(panelMainLayout);
        panelMainLayout.setHorizontalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lblmessg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(panelMainLayout.createSequentialGroup()
                        .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(panelMainLayout.createSequentialGroup()
                                .addComponent(btdelete)
                                .addGap(6, 6, 6)
                                .addComponent(btupdate)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btadd))
                            .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        panelMainLayout.setVerticalGroup(
            panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblmessg, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 175, Short.MAX_VALUE)
                .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btdelete)
                    .addGroup(panelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btupdate)
                        .addComponent(btadd)))
                .addContainerGap())
        );

        add(panelMain);

        panelOutbound.setBorder(javax.swing.BorderFactory.createTitledBorder("Outbound Document Type Options"));
        panelOutbound.setPreferredSize(new java.awt.Dimension(752, 543));

        jLabel11.setText("Sub Sep");
        jLabel11.setName("lblsubsep"); // NOI18N

        jLabel17.setText("Version");
        jLabel17.setName("lblversion"); // NOI18N

        jLabel18.setText("SupplierCode");
        jLabel18.setName("lblsuppcode"); // NOI18N

        jLabel14.setText("FilePath");
        jLabel14.setName("lblfilepath"); // NOI18N

        tbelement.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                tbelementFocusLost(evt);
            }
        });

        tbsegment.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                tbsegmentFocusLost(evt);
            }
        });

        jLabel4.setText("Elem Sep");
        jLabel4.setName("lblelemsep"); // NOI18N

        jLabel10.setText("Seg Sep");
        jLabel10.setName("lblsegsep"); // NOI18N

        tbsub.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                tbsubFocusLost(evt);
            }
        });

        jLabel12.setText("Prefix");
        jLabel12.setName("lblprefix"); // NOI18N

        lblsuffix.setText("Suffix");
        lblsuffix.setName("lblsuffix"); // NOI18N

        cbenvelopeall.setText("Multi-Envelope");

        cbuna.setText("UNA Segment");

        cbfa.setText("Acknowledgment");
        cbfa.setName("cbfunctional"); // NOI18N

        cbung.setText("UNG Segment");

        btlookupElement.setIcon(FontIcon.of(MaterialDesignM.MAGNIFY, 16)); // NOI18N
        btlookupElement.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btlookupElementActionPerformed(evt);
            }
        });

        btlookupSegment.setIcon(FontIcon.of(MaterialDesignM.MAGNIFY, 16)); // NOI18N
        btlookupSegment.setPreferredSize(new java.awt.Dimension(25, 25));
        btlookupSegment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btlookupSegmentActionPerformed(evt);
            }
        });

        btlookupSubElement.setIcon(FontIcon.of(MaterialDesignM.MAGNIFY, 16)); // NOI18N
        btlookupSubElement.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btlookupSubElementActionPerformed(evt);
            }
        });

        cbemail.setText("Email");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jLabel18)
                    .addComponent(jLabel17)
                    .addComponent(jLabel14)
                    .addComponent(jLabel4)
                    .addComponent(lblsuffix)
                    .addComponent(jLabel12)
                    .addComponent(jLabel11)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbfilepath, javax.swing.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE)
                    .addComponent(tbfilesuffix, javax.swing.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE)
                    .addComponent(tbfileprefix)
                    .addComponent(tbsupplier, javax.swing.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(tbsegment, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(35, 35, 35)
                                .addComponent(lblseg, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(tbversion, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addGroup(jPanel3Layout.createSequentialGroup()
                                            .addComponent(tbelement, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                            .addComponent(btlookupElement, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(btlookupSubElement, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(btlookupSegment, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addComponent(tbsub, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(lblelem, javax.swing.GroupLayout.DEFAULT_SIZE, 29, Short.MAX_VALUE)
                                    .addComponent(lblsub, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addGap(0, 49, Short.MAX_VALUE)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cbemail)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(jPanel3Layout.createSequentialGroup()
                            .addComponent(cbenvelopeall)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(cbung))
                        .addGroup(jPanel3Layout.createSequentialGroup()
                            .addComponent(cbfa)
                            .addGap(27, 27, 27)
                            .addComponent(cbuna))))
                .addGap(28, 28, 28))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblelem, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel4)
                        .addComponent(tbelement, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(btlookupElement, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btlookupSegment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(tbsegment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel10))
                    .addComponent(lblseg, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(tbsub, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel11))
                            .addComponent(lblsub, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tbfileprefix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addComponent(jLabel12)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(tbfilesuffix, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lblsuffix))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(tbfilepath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel14))
                        .addGap(6, 6, 6)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(tbversion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel17)))
                    .addComponent(btlookupSubElement, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbsupplier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel18))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbuna)
                    .addComponent(cbfa))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbung)
                    .addComponent(cbenvelopeall))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cbemail)
                .addContainerGap(172, Short.MAX_VALUE))
        );

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Attributes"));

        btdeleteattribute.setIcon(FontIcon.of(MaterialDesignD.DELETE, 16)); // NOI18N
        btdeleteattribute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btdeleteattributeActionPerformed(evt);
            }
        });

        btaddattribute.setIcon(FontIcon.of(MaterialDesignP.PLUS_CIRCLE, 16)); // NOI18N
        btaddattribute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btaddattributeActionPerformed(evt);
            }
        });

        jScrollPane1.setViewportView(listAttributes);

        ddattributekey.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ISA01", "ISA02", "ISA03", "ISA04", "ISA05", "ISA06", "ISA07", "ISA08", "ISA11", "ISA12", "ISA14", "ISA15", "GS01", "GS02", "GS03", "GS07", "GS08", "UNB01", "UNB02", "UNB03", "UNH02", "envctrlnbr", "grpctrlnbr" }));

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(tbattributevalue, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 168, Short.MAX_VALUE)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(btdeleteattribute, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btaddattribute, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(ddattributekey, 0, 168, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ddattributekey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbattributevalue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btaddattribute, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btdeleteattribute, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 253, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout panelOutboundLayout = new javax.swing.GroupLayout(panelOutbound);
        panelOutbound.setLayout(panelOutboundLayout);
        panelOutboundLayout.setHorizontalGroup(
            panelOutboundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelOutboundLayout.createSequentialGroup()
                .addContainerGap(504, Short.MAX_VALUE)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(36, 36, 36))
            .addGroup(panelOutboundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panelOutboundLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(428, Short.MAX_VALUE)))
        );
        panelOutboundLayout.setVerticalGroup(
            panelOutboundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelOutboundLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(135, Short.MAX_VALUE))
            .addGroup(panelOutboundLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panelOutboundLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        add(panelOutbound);
    }// </editor-fold>//GEN-END:initComponents

    private void btaddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btaddActionPerformed
         if (! validateInput(BlueSeerUtils.dbaction.add)) {
           return;
       }
        setPanelComponentState(this, false);
        executeTask(BlueSeerUtils.dbaction.add, null);
    }//GEN-LAST:event_btaddActionPerformed

    private void btupdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btupdateActionPerformed
        if (! validateInput(BlueSeerUtils.dbaction.update)) {
           return;
       }
        setPanelComponentState(this, false);
        executeTask(BlueSeerUtils.dbaction.update, new String[]{ddkey.getSelectedItem().toString(), dddoc.getSelectedItem().toString(), tbsndgs.getText(), tbrcvgs.getText(), ddmap.getSelectedItem().toString()});
    }//GEN-LAST:event_btupdateActionPerformed

    private void btdeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btdeleteActionPerformed
         
       if (! validateInput(BlueSeerUtils.dbaction.delete)) {
           return;
       }
        setPanelComponentState(this, false);
        executeTask(BlueSeerUtils.dbaction.delete, new String[]{ddkey.getSelectedItem().toString(), dddoc.getSelectedItem().toString(), tbsndgs.getText(), tbrcvgs.getText(), ddmap.getSelectedItem().toString()});   
    }//GEN-LAST:event_btdeleteActionPerformed

    private void btnewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnewActionPerformed
        newAction("");
    }//GEN-LAST:event_btnewActionPerformed

    private void tbelementFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tbelementFocusLost
            String x = BlueSeerUtils.bsformat("", tbelement.getText(), "0");
        if (x.equals("error")) {
            tbelement.setText("");
            tbelement.setBackground(Color.yellow);
            bsmf.MainFrame.show(getMessageTag(1000));
            tbelement.requestFocus();
            lblelem.setText("");
        } else {
            tbelement.setText(x);
            tbelement.setBackground(Color.white);
            int t = Integer.valueOf(x);
            lblelem.setText(asciivalues(t));
        }
    }//GEN-LAST:event_tbelementFocusLost

    private void tbsegmentFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tbsegmentFocusLost
           String x = BlueSeerUtils.bsformat("", tbsegment.getText(), "0");
        if (x.equals("error")) {
            tbsegment.setText("");
            tbsegment.setBackground(Color.yellow);
            bsmf.MainFrame.show(getMessageTag(1000));
            tbsegment.requestFocus();
            lblseg.setText("");
        } else {
            tbsegment.setText(x);
            tbsegment.setBackground(Color.white);
            int t = Integer.valueOf(x);            
            lblseg.setText(asciivalues(t));
        }
    }//GEN-LAST:event_tbsegmentFocusLost

    private void tbsubFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tbsubFocusLost
         String x = BlueSeerUtils.bsformat("", tbsub.getText(), "0");
        if (x.equals("error")) {
            tbsub.setText("");
            tbsub.setBackground(Color.yellow);
            bsmf.MainFrame.show(getMessageTag(1000));
            tbsub.requestFocus();
            lblsub.setText("");
        } else {
            tbsub.setText(x);
            tbsub.setBackground(Color.white);
            int t = Integer.valueOf(x);
            lblsub.setText(asciivalues(t));
        }
    }//GEN-LAST:event_tbsubFocusLost

    private void btaddattributeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btaddattributeActionPerformed
       
        EDData.addEDIAttributeRecord(tbsndgs.getText(), tbrcvgs.getText(), ddoutdoctype.getSelectedItem().toString(), ddattributekey.getSelectedItem().toString(), tbattributevalue.getText());
        attrlist = EDData.getEDIAttributesList(ddoutdoctype.getSelectedItem().toString(), tbsndgs.getText(), tbrcvgs.getText());
        listmodel.removeAllElements();
        for (String x : attrlist) {
            listmodel.addElement(x);
        }
        ddattributekey.setSelectedIndex(0);
        tbattributevalue.setText("");
    }//GEN-LAST:event_btaddattributeActionPerformed

    private void btdeleteattributeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btdeleteattributeActionPerformed
        boolean proceed = true; 
        
        if (listAttributes.isSelectionEmpty()) {
            proceed = false;
            bsmf.MainFrame.show(getMessageTag(1029));
        } else {
           proceed = bsmf.MainFrame.warn(getMessageTag(1004));
        }
        if (proceed) {
            String[] z = listAttributes.getSelectedValue().toString().split(":");
            deleteEDIAttributeRecord(tbsndgs.getText(), tbrcvgs.getText(), ddoutdoctype.getSelectedItem().toString(), z[0]);
            attrlist = EDData.getEDIAttributesList(ddoutdoctype.getSelectedItem().toString(), tbsndgs.getText(), tbrcvgs.getText());
            listmodel.removeAllElements();
            for (String x : attrlist) {
                listmodel.addElement(x);
            }
            ddattributekey.setSelectedIndex(0);
            tbattributevalue.setText("");
            }
    }//GEN-LAST:event_btdeleteattributeActionPerformed

    private void btlookupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btlookupActionPerformed
        lookUpFrame();
    }//GEN-LAST:event_btlookupActionPerformed

    private void ddmapActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ddmapActionPerformed
        if (! isLoad && ddmap.getSelectedItem() != null && ! ddmap.getSelectedItem().toString().isBlank()) {
            ediData.map_mstr x = getMapMstr(new String[]{ddmap.getSelectedItem().toString()});
            ddoutdoctype.setSelectedItem(x.map_outdoctype());
            ddoutfiletype.setSelectedItem(x.map_outfiletype());
            ddinfiletype.setSelectedItem(x.map_infiletype());
            tbIFS.setText(x.map_ifs());
            tbOFS.setText(x.map_ofs());
        }
    }//GEN-LAST:event_ddmapActionPerformed

    private void btclearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btclearActionPerformed
        BlueSeerUtils.messagereset();
        initvars(null);
    }//GEN-LAST:event_btclearActionPerformed

    private void dddocActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dddocActionPerformed
        if (! isLoad && dddoc.getSelectedItem() != null) {            
        isLoad = true;
        ddmap.removeAllItems();
        ArrayList<String> maps = ediData.getMapMstrList(dddoc.getSelectedItem().toString());
        for (int i = 0; i < maps.size(); i++) {
            ddmap.addItem(maps.get(i));
        }
        lblmessg.setText("Map Count for this type: " + ddmap.getItemCount());
        if (ddmap.getItemCount() <= 0) {
            lblmessg.setForeground(Color.red);
        } else {
            lblmessg.setForeground(Color.black);
        }
        
        ddmap.insertItemAt("", 0);
        ddmap.setSelectedIndex(0);
        isLoad = false;
        }
    }//GEN-LAST:event_dddocActionPerformed

    private void btlookupElementActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btlookupElementActionPerformed
        lookUpFrameASCIIEle();
    }//GEN-LAST:event_btlookupElementActionPerformed

    private void ddkeyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ddkeyActionPerformed
        
        if (! isLoad && ddkey.getSelectedItem() != null && ! ddkey.getSelectedItem().toString().isBlank()) {
        lblpartner.setText(getEDIPartnerDesc(ddkey.getSelectedItem().toString()));
        }
        
    }//GEN-LAST:event_ddkeyActionPerformed

    private void btlookupSegmentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btlookupSegmentActionPerformed
        lookUpFrameASCIISeg();
    }//GEN-LAST:event_btlookupSegmentActionPerformed

    private void btlookupSubElementActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btlookupSubElementActionPerformed
       lookUpFrameASCIISub();
    }//GEN-LAST:event_btlookupSubElementActionPerformed

    private void btcopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btcopyActionPerformed
            if (! isLoad) {
            ddkey.setSelectedIndex(0);
            ddkey.setBackground(Color.yellow);
            //bsmf.MainFrame.show("choose new parent key and adjust ISA/GS IDs accordingly");
            ddkey.requestFocus();
            btadd.setEnabled(true);
            ddkey.setEnabled(true);
            tbrcvgs.setEnabled(true);
            tbsndgs.setEnabled(true);
            }
    }//GEN-LAST:event_btcopyActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btadd;
    private javax.swing.JButton btaddattribute;
    private javax.swing.JButton btclear;
    private javax.swing.JButton btcopy;
    private javax.swing.JButton btdelete;
    private javax.swing.JButton btdeleteattribute;
    private javax.swing.JButton btlookup;
    private javax.swing.JButton btlookupElement;
    private javax.swing.JButton btlookupSegment;
    private javax.swing.JButton btlookupSubElement;
    private javax.swing.JButton btnew;
    private javax.swing.JButton btupdate;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JCheckBox cbemail;
    private javax.swing.JCheckBox cbenvelopeall;
    private javax.swing.JCheckBox cbfa;
    private javax.swing.JCheckBox cbuna;
    private javax.swing.JCheckBox cbung;
    private javax.swing.JComboBox<String> ddattributekey;
    private javax.swing.JComboBox<String> dddoc;
    private javax.swing.JComboBox<String> ddinfiletype;
    private javax.swing.JComboBox<String> ddkey;
    private javax.swing.JComboBox<String> ddmap;
    private javax.swing.JComboBox<String> ddoutdoctype;
    private javax.swing.JComboBox<String> ddoutfiletype;
    private javax.swing.JComboBox<String> ddsite;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel lblelem;
    private javax.swing.JLabel lblmessg;
    private javax.swing.JLabel lblpartner;
    private javax.swing.JLabel lblseg;
    private javax.swing.JLabel lblsub;
    private javax.swing.JLabel lblsuffix;
    private javax.swing.JList<String> listAttributes;
    private javax.swing.JPanel panelMain;
    private javax.swing.JPanel panelOutbound;
    private javax.swing.JTextField tbIFS;
    private javax.swing.JTextField tbOFS;
    private javax.swing.JTextField tbattributevalue;
    private javax.swing.JTextField tbelement;
    private javax.swing.JTextField tbfilepath;
    private javax.swing.JTextField tbfileprefix;
    private javax.swing.JTextField tbfilesuffix;
    private javax.swing.JTextField tbrcvgs;
    private javax.swing.JTextField tbrcvisa;
    private javax.swing.JTextField tbrcvq;
    private javax.swing.JTextField tbsegment;
    private javax.swing.JTextField tbsndgs;
    private javax.swing.JTextField tbsndisa;
    private javax.swing.JTextField tbsndq;
    private javax.swing.JTextField tbsub;
    private javax.swing.JTextField tbsupplier;
    private javax.swing.JTextField tbversion;
    // End of variables declaration//GEN-END:variables
}
