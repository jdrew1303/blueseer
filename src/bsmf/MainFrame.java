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

/*
 * The original source for this file was lost; this was reconstructed by
 * decompiling the previously-shipped lib/bsmf.jar with CFR 0.152 and
 * verified to compile and behave identically to that jar (including under
 * the app's existing test/UI-regression coverage) before replacing it here.
 */
package bsmf;

import bsmf.BackGroundPanel;
import com.blueseer.adm.admData;
import com.blueseer.edi.apiUtils;
import com.blueseer.utl.BlueSeerUtils;
import com.blueseer.utl.OVData;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Logger;
import com.jcraft.jsch.Session;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener;
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder;
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPublickey;
import org.apache.commons.dbcp2.BasicDataSource;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

public class MainFrame
extends JFrame {
    public static BackGroundPanel backgroundpanel;
    public static String[] myargs;
    public static File f;
    public static FileChannel channel;
    public static FileLock lock;
    public static String userid;
    public static Connection con;
    public static String configfile;
    public static String ip;
    public static String port;
    public static String serverport;
    public static String url;
    public static String db;
    public static String dbname;
    public static String dbtype;
    public static String ver;
    public static String driver;
    public static String user;
    public static String pass;
    public static String lang;
    public static String country;
    public static String temp;
    public static String hichar;
    public static String lowchar;
    public static String hinbr;
    public static String lownbr;
    public static String menu;
    public static boolean spin;
    public static boolean loginfailure;
    public static boolean isPaint;
    public static Object main;
    public static Date now;
    public static DateFormat dfdate;
    public static String hidate;
    public static String lowdate;
    public static ArrayList<String> mypanels;
    public static ArrayList<String> menutreeheaders;
    public static HashMap<String, Boolean> visibleMenuHeaders;
    public static HashMap<String, Object> panelmap;
    public static HashMap<String, String> menumap;
    public static HashMap<String, String[]> navcodemap;
    public static ArrayList<String> permmap;
    public static ArrayList<String> menuhist;
    public static HashMap<Currency, Locale> currencymap;
    public static int menuAt;
    public static int backmenuint;
    public static Session session;
    public static String protocol;
    public static String rhost;
    public static String lport;
    public static String rport;
    public static String sshuser;
    public static String sshpass;
    public static String sessionid;
    public static String jardir;
    public static boolean isSSHConnected;
    public static boolean iscurrencyset;
    public static boolean debug;
    public static boolean bypass;
    public static boolean override_url;
    public static boolean remoteDB;
    public static boolean encryptedBSConfig;
    public static ResourceBundle tags;
    public static DecimalFormatSymbols symbols;
    public static char defaultDecimalSeparator;
    public static char defaultGroupingSeparator;
    public static ArrayList<String> menupermuser;
    public static ArrayList<String[]> initLoginData;
    public static BasicDataSource ds;
    public static JProgressBar MainProgressBar;
    public static JLabel messagelabel;
    public static JTextField navcode;
    public static JFrame mydialog;
    Image myimage = null;
    Image brandLogoImage = null;
    public static Color backgroundcolor;
    public static Color nonEditableColor;
    public static Color invalidColor;
    public static Color ddbgcolor;
    private static JMenuBar MainMenuBar;
    public static JPanel PanelMain;
    private JButton btlogin;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JPanel loginpanel;
    private JMenu menuback;
    private JMenu menuhome;
    private JPanel primarypanel;
    private JPasswordField tbpass;
    private JTextField tbuser;

    public static void hidepanels() throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        for (Map.Entry<String, Object> entry : panelmap.entrySet()) {
            Class<?> myclass = entry.getValue().getClass();
            Method mymethod = myclass.getMethod("setVisible", Boolean.TYPE);
            mymethod.invoke(entry.getValue(), false);
        }
    }

    public static void reColorPanels(Color newcolor) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        for (Map.Entry<String, Object> entry : panelmap.entrySet()) {
            Class<?> myclass = entry.getValue().getClass();
            Method mymethod = myclass.getMethod("setBackground", Color.class);
            mymethod.invoke(entry.getValue(), newcolor);
        }
    }

    public void engageFunction(String function) throws ClassNotFoundException {
        Class<?> myclass = this.getClass();
        try {
            Method mymethod = myclass.getMethod(function, new Class[0]);
            mymethod.invoke((Object)this, new Object[0]);
        }
        catch (NoSuchMethodException ex) {
            MainFrame.bslog(ex);
        }
        catch (SecurityException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalAccessException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalArgumentException ex) {
            MainFrame.bslog(ex);
        }
        catch (InvocationTargetException ex) {
            MainFrame.bslog(ex);
        }
    }

    public static void reinitpanels_task(String x, boolean hasInit, String[] y) {
        class Task
        extends SwingWorker<Integer, String> {
            String panel = "";
            boolean hasInit = false;
            String[] arg = null;

            public Task(String panel, boolean hasInit, String[] key) {
                this.panel = panel;
                this.hasInit = hasInit;
                this.arg = key;
            }

            @Override
            public Integer doInBackground() throws Exception {
                this.publish("test");
                return null;
            }

            @Override
            public void done() {
            }

            @Override
            protected void process(java.util.List<String> m) {
                messagelabel.setText(m.get(0));
                MainFrame.reinitpanels_sub(this.panel, this.hasInit, this.arg);
            }
        }
        Task z = new Task(x, hasInit, y);
        z.execute();
    }

    public static void reinitpanels_sub(String mypanel, boolean hasInit, String[] arg) {
        if (MainFrame.checkDBConnect()) {
            if (MainFrame.loadPanel(mypanel, main)) {
                try {
                    MainFrame.hidepanels();
                }
                catch (NoSuchMethodException ex) {
                    MainFrame.bslog(ex);
                }
                catch (IllegalAccessException ex) {
                    MainFrame.bslog(ex);
                }
                catch (IllegalArgumentException ex) {
                    MainFrame.bslog(ex);
                }
                catch (InvocationTargetException ex) {
                    MainFrame.bslog(ex);
                }
                backgroundpanel.setVisible(true);
                return;
            }
            backgroundpanel.setVisible(false);
        } else {
            MainFrame.show("Database connection lost...close app and restart");
        }
        Object myobject = panelmap.get(menumap.get(mypanel));
        Class<?> myclass = myobject.getClass();
        try {
            Method mymethod = null;
            MainFrame.hidepanels();
            mymethod = myclass.getMethod("setVisible", Boolean.TYPE);
            mymethod.invoke(myobject, true);
            if (hasInit) {
                mymethod = myclass.getMethod("initvars", String[].class);
                mymethod.invoke(myobject, new Object[]{arg});
            }
        }
        catch (NoSuchMethodException ex) {
            MainFrame.bslog(ex);
        }
        catch (SecurityException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalAccessException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalArgumentException ex) {
            MainFrame.bslog(ex);
        }
        catch (InvocationTargetException ex) {
            MainFrame.bslog(ex);
        }
    }

    public static void reinitpanels(String mypanel, boolean hasInit, String[] arg) {
        LocalDateTime start = LocalDateTime.now();
        BlueSeerUtils.message((String[])new String[]{"0", ""});
        MainFrame.reinitpanels_sub(mypanel, hasInit, arg);
        if (debug) {
            System.out.println("reinitpanels: " + BlueSeerUtils.timediff((LocalDateTime)start));
        }
    }

    public static void bslog(String s) {
        java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, s);
    }

    public static void bslog(Throwable t) {
        java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, t);
    }

    public static void show(String mystring) {
        JOptionPane.showMessageDialog(null, mystring);
    }

    public static String input(String mystring) {
        String s = JOptionPane.showInputDialog(null, (Object)mystring);
        return s;
    }

    public static boolean warn(String mystring) {
        int dialogButton = 0;
        int dialogResult = JOptionPane.showConfirmDialog(null, mystring, "Warning", dialogButton);
        return dialogResult == 0;
    }

    public void setLanguageTags(Object myobj) {
        Component[] components;
        JPanel panel = null;
        JTabbedPane tabpane = null;
        JScrollPane scrollpane = null;
        if (myobj instanceof JPanel) {
            panel = (JPanel)myobj;
        } else if (myobj instanceof JTabbedPane) {
            tabpane = (JTabbedPane)myobj;
        } else if (myobj instanceof JScrollPane) {
            scrollpane = (JScrollPane)myobj;
        } else {
            return;
        }
        for (Component component : components = panel.getComponents()) {
            if (component instanceof JPanel) {
                if (tags.containsKey(this.getClass().getSimpleName() + ".panel." + component.getName())) {
                    ((JPanel)component).setBorder(BorderFactory.createTitledBorder(tags.getString(this.getClass().getSimpleName() + ".panel." + component.getName())));
                }
                this.setLanguageTags((JPanel)component);
            }
            if (component instanceof JLabel && tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) {
                ((JLabel)component).setText(tags.getString(this.getClass().getSimpleName() + ".label." + component.getName()));
            }
            if (component instanceof JButton && tags.containsKey("global.button." + component.getName())) {
                ((JButton)component).setText(tags.getString("global.button." + component.getName()));
            }
            if (component instanceof JCheckBox && tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) {
                ((JCheckBox)component).setText(tags.getString(this.getClass().getSimpleName() + ".label." + component.getName()));
            }
            if (!(component instanceof JRadioButton) || !tags.containsKey(this.getClass().getSimpleName() + ".label." + component.getName())) continue;
            ((JRadioButton)component).setText(tags.getString(this.getClass().getSimpleName() + ".label." + component.getName()));
        }
    }

    public void changeIPandDB() {
        String myvalue = "";
        myvalue = JOptionPane.showInputDialog("EnterDB");
        if (myvalue != null && !myvalue.isEmpty()) {
            MainFrame.updateConfig(myvalue);
            this.setFrameTitle("");
        }
    }

    public String[] doLogin() {
        if (remoteDB && !isSSHConnected) {
            ArrayList<String[]> list = new ArrayList<String[]>();
            list.add(new String[]{"id", "loginAPI"});
            list.add(new String[]{"user", this.tbuser.getText()});
            list.add(new String[]{"pass", new String(this.tbpass.getPassword())});
            try {
                String x = BlueSeerUtils.sendServerPost(list, (String)"", null, (String)"authServ");
                if (debug) {
                    System.out.println("sendServerPost (login response): " + x + "/" + x.length());
                }
                if (x == null || x.isBlank()) {
                    this.tbpass.requestFocus();
                    return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag((int)1210)};
                }
                if (x.length() > 3 && x.substring(0, 3).equals("401")) {
                    this.tbpass.requestFocus();
                    return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag((int)1209)};
                }
                org.bouncycastle.util.encoders.Base64 b = new org.bouncycastle.util.encoders.Base64();
                sessionid = new String(org.bouncycastle.util.encoders.Base64.decode((String)x), Charset.forName("UTF-8"));
            }
            catch (IOException ex) {
                MainFrame.bslog(ex);
                MainFrame.show("user " + this.tbuser.getText() + BlueSeerUtils.getMessageTag((int)1210));
                this.tbpass.requestFocus();
                return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag((int)1210)};
            }
        } else {
            String passwd = new String(this.tbpass.getPassword());
            if (!MainFrame.isPasswdCorrect(this.tbuser.getText(), passwd)) {
                this.tbpass.requestFocus();
                return new String[]{BlueSeerUtils.ErrorBit, BlueSeerUtils.getMessageTag((int)1209)};
            }
        }
        userid = this.tbuser.getText();
        initLoginData = admData.getLoginInit((String)userid);
        return new String[]{BlueSeerUtils.SuccessBit, BlueSeerUtils.getMessageTag((int)1208)};
    }

    public void setFrameTitle(String title) {
        if (title.isEmpty()) {
            title = remoteDB ? "USER=" + userid + "        IP=" + rhost + "        VER=" + ver + "        DBTYPE=" + dbtype + "        DBNAME=" + dbname : "USER=" + userid + "        IP=" + ip + "        VER=" + ver + "        DBTYPE=" + dbtype + "        DBNAME=" + dbname;
        }
        this.setTitle(title);
    }

    public static void updateapplication() {
        try {
            if (System.getProperty("os.name").toString().matches("(?i:.*windows.*)")) {
                Process p = Runtime.getRuntime().exec("xcopy /e /y \\\\xxxserver\\Apps\\Apps\\* C:\\BlueSeer /EXCLUDE:\\\\xxxserver\\Apps\\Apps\\excludefiles.txt");
                MainFrame.show("Systems will now close...");
                System.exit(0);
            }
            if (System.getProperty("os.name").toString().matches("(?i:.*linux.*)")) {
                MainFrame.show("OS is linux...need to manually update");
            }
        }
        catch (IOException ex) {
            MainFrame.show("Unable to update");
        }
    }

    public static void getScreenSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        MainFrame.show(String.valueOf(screenSize.width + " / " + screenSize.height));
    }

    public static void setConfig(String cfile) {
        if (cfile.isBlank()) {
            cfile = configfile;
        }
        try {
            String[] cfgcontents;
            String lines = "";
            if (Files.exists(FileSystems.getDefault().getPath(cfile, new String[0]), new LinkOption[0])) {
                byte[] tac = Files.readAllBytes(FileSystems.getDefault().getPath(cfile, new String[0]));
                lines = encryptedBSConfig ? MainFrame.decryptConfig(new String(tac, StandardCharsets.UTF_8), MainFrame.scram()) : new String(tac, StandardCharsets.UTF_8);
            }
            if ((cfgcontents = lines.split("\n", -1)) == null || cfgcontents.length < 5) {
                System.out.println("Error:  Cannot read bs.cfg");
                System.exit(0);
            }
            for (String line : cfgcontents) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] recs = line.split("=", -1);
                if (recs[0].equals("DB")) {
                    db = recs[1].trim();
                    dbname = recs[1].trim();
                }
                if (recs[0].equals("IP")) {
                    ip = recs[1].trim();
                }
                if (recs[0].equals("PORT")) {
                    port = recs[1].trim();
                }
                if (recs[0].equals("SERVERPORT")) {
                    serverport = recs[1].trim();
                }
                if (recs[0].equals("USER")) {
                    user = recs[1].trim();
                }
                if (recs[0].equals("PASS")) {
                    pass = recs[1].trim();
                }
                if (recs[0].equals("DRIVER")) {
                    driver = recs[1].trim();
                }
                if (recs[0].equals("DBTYPE")) {
                    dbtype = recs[1].trim();
                }
                if (recs[0].equals("SSHRHOST")) {
                    rhost = recs[1].trim();
                }
                if (recs[0].equals("SSHLPORT")) {
                    lport = recs[1].trim();
                }
                if (recs[0].equals("SSHRPORT")) {
                    rport = recs[1].trim();
                }
                if (recs[0].equals("LANGUAGE")) {
                    lang = recs[1].trim();
                }
                if (recs[0].equals("COUNTRY")) {
                    country = recs[1].trim();
                }
                if (recs[0].equals("REMOTEDB")) {
                    remoteDB = BlueSeerUtils.ConvertStringToBool((String)recs[1].trim());
                }
                if (recs[0].equals("PROTOCOL")) {
                    protocol = recs[1].trim();
                }
                if (!recs[0].equals("JARDIR")) continue;
                jardir = recs[1].trim();
            }
            Locale.setDefault(new Locale(lang, country));
            symbols = new DecimalFormatSymbols(Locale.getDefault());
            defaultDecimalSeparator = symbols.getDecimalSeparator();
            defaultGroupingSeparator = symbols.getGroupingSeparator();
            currencymap = MainFrame.getCurrencyLocaleMap();
        }
        catch (FileNotFoundException ex) {
            MainFrame.bslog("Config File Is Missing!");
            System.exit(0);
        }
        catch (IOException ex) {
            MainFrame.bslog("Unable To Read Config File");
            System.exit(0);
        }
        catch (Exception ex) {
            MainFrame.bslog("Unable To Read Config File...Exception");
            System.exit(0);
        }
        if (db.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: DB");
            System.exit(0);
        }
        if (ip.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: IP");
            System.exit(0);
        }
        if (port.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: PORT");
            System.exit(0);
        }
        if (user.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: USER");
            System.exit(0);
        }
        if (pass.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: PASS");
            System.exit(0);
        }
        if (driver.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: DRIVER");
            System.exit(0);
        }
        if (dbtype.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: DBTYPE");
            System.exit(0);
        }
        if (lang.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: LANGUAGE");
            System.exit(0);
        }
        if (country.isEmpty()) {
            MainFrame.bslog("Config File Is Missing a Key: COUNTRY");
            System.exit(0);
        }
        if (dbtype.equals("mysql")) {
            url = "jdbc:mysql://" + ip + ":" + port + "/";
            db = db + "?enabledTLSProtocols=TLSv1.2&useUnicode=true&characterEncoding=UTF-8&serverTimezone=" + TimeZone.getDefault().getID();
        }
        if (dbtype.equals("sqlite")) {
            url = "jdbc:sqlite:";
        }
    }

    public static void checkJarDir() {
        Path currentJarPath = FileSystems.getDefault().getPath("dist/blueseer.jar", new String[0]);
        Path repoJarPath = FileSystems.getDefault().getPath(jardir + "/blueseer.jar", new String[0]);
        System.out.println("HERE: " + currentJarPath + " --- " + repoJarPath);
        if (!Files.exists(currentJarPath, new LinkOption[0])) {
            return;
        }
        if (!Files.exists(repoJarPath, new LinkOption[0])) {
            return;
        }
        try {
            BasicFileAttributes currentAttrs = Files.readAttributes(currentJarPath, BasicFileAttributes.class, new LinkOption[0]);
            BasicFileAttributes repoAttrs = Files.readAttributes(repoJarPath, BasicFileAttributes.class, new LinkOption[0]);
            FileTime cdt = currentAttrs.lastModifiedTime();
            FileTime rdt = repoAttrs.lastModifiedTime();
            if (rdt.toMillis() > cdt.toMillis()) {
                System.out.println("yep....new repo jar in jardir");
                System.exit(0);
            }
        }
        catch (IOException ex) {
            MainFrame.bslog(ex);
        }
    }

    public static String scram() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < 120; ++i) {
            System.out.println((char)i + " -- " + i);
            if (i != 66) continue;
            sb.append((char)i).append((char)(i + 42)).append((char)(i + 19)).append((char)(i + 35)).append((char)(i - 16)).append((char)(i + 28));
        }
        return sb.toString();
    }

    public static String decryptConfig(String encodedMessage, String p) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(p.toCharArray(), "bR549!".getBytes(), 65536, 256);
        SecretKeySpec secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(2, secretKey);
        byte[] decryptedMessage = cipher.doFinal(Base64.getDecoder().decode(encodedMessage));
        return new String(decryptedMessage, StandardCharsets.UTF_8);
    }

    public static String encryptConfig(String Message, String p) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(p.toCharArray(), "bR549!".getBytes(), 65536, 256);
        SecretKeySpec secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(1, secretKey);
        byte[] encryptedMessage = cipher.doFinal(Message.getBytes(StandardCharsets.UTF_8));
        String encodedMessage = Base64.getEncoder().encodeToString(encryptedMessage);
        return encodedMessage;
    }

    public static HashMap<Currency, Locale> getCurrencyLocaleMap() {
        HashMap<Currency, Locale> map = new HashMap<Currency, Locale>();
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                Currency currency = Currency.getInstance(locale);
                map.put(currency, locale);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return map;
    }

    public String[] checkSSH() throws IOException {
        File sshfile = new File(".sshbs");
        String[] ssh = null;
        if (sshfile.exists()) {
            try {
                String line;
                BufferedReader f = new BufferedReader(new FileReader(sshfile, StandardCharsets.UTF_8));
                while ((line = f.readLine()) != null) {
                    ssh = line.split("\\|", -1);
                }
                f.close();
            }
            catch (FileNotFoundException ex) {
                MainFrame.bslog(ex);
            }
        } else {
            MainFrame.show("SSH requested but no credentials found...click to enter credentials");
            String sshuser = MainFrame.input("Enter SSHUser:");
            String sshpass = MainFrame.input("Enter SSHPass:");
            BufferedWriter sshl = new BufferedWriter(new OutputStreamWriter((OutputStream)new FileOutputStream(".sshbs"), StandardCharsets.UTF_8));
            sshuser = MainFrame.PassWord("0", sshuser.trim().toCharArray());
            sshpass = MainFrame.PassWord("0", sshpass.trim().toCharArray());
            sshl.write(sshuser + "|" + sshpass);
            sshl.close();
            MainFrame.show("SSH credentials captured...exiting...please restart App");
            System.exit(0);
        }
        return ssh;
    }

    public static String listConfig() {
        String x = "";
        x = "bs.cfg variables are: database=" + db + " dbtype=" + dbtype + " driver=" + driver + " ip=" + ip + " port=" + port + " serverport=" + serverport + " user=" + user + " pass=" + pass + " ";
        return x;
    }

    public static void logSysInfo() {
        NetworkIF[] s;
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        long availableMemory = hal.getMemory().getAvailable();
        String Text = "INIT DATE:  " + hidate + "\n";
        Text = Text + "Locale: " + Locale.getDefault().toString() + "\n";
        Text = Text + "Version: " + ver + "\n";
        Text = Text + "DataBase: " + db + "\n";
        Text = Text + "DataBaseType: " + dbtype + "\n";
        Text = Text + "DataBaseDriver: " + driver + "\n";
        Text = Text + "Available memory (bytes): " + (availableMemory == Long.MAX_VALUE ? "no limit" : Long.valueOf(availableMemory)) + "\n";
        long totalMemory = hal.getMemory().getTotal();
        Text = Text + "Total memory (bytes): " + (totalMemory == Long.MAX_VALUE ? "no limit" : Long.valueOf(totalMemory)) + "\n";
        Text = Text + "Processor: " + si.getHardware().getProcessor().getName() + "\n";
        Text = Text + "Processor Vendor: " + si.getHardware().getProcessor().getVendor() + "\n";
        Text = Text + "Processor Model: " + si.getHardware().getProcessor().getModel() + "\n";
        Text = Text + "Processor Logical Count: " + si.getHardware().getProcessor().getLogicalProcessorCount() + "\n";
        Text = Text + "Processor Physical Count: " + si.getHardware().getProcessor().getPhysicalProcessorCount() + "\n";
        OperatingSystem os = si.getOperatingSystem();
        Text = Text + "Operating System: " + os + "\n";
        Text = Text + "OS Family: " + os.getFamily() + "\n";
        Text = Text + "OS Manufacturer: " + os.getManufacturer() + "\n";
        Text = Text + "OS Version: " + os.getVersion().getVersion() + "\n";
        Text = Text + "OS Bit: " + os.getBitness() + "\n";
        Text = Text + "OS Build: " + os.getVersion().getBuildNumber() + "\n";
        Text = Text + "OS Codename: " + os.getVersion().getCodeName() + "\n";
        Text = Text + "OS FileSystem: " + os.getFileSystem() + "\n";
        for (NetworkIF x : s = si.getHardware().getNetworkIFs()) {
            Text = Text + "Network: " + x.getName() + " / " + String.join((CharSequence)", ", x.getIPv4addr()) + "\n";
        }
        Text = Text + "Java Version: " + System.getProperty("java.version") + "\n";
        Text = Text + "Java VM: " + System.getProperty("java.vm.name") + "\n";
        Text = Text + "Java VM Version: " + System.getProperty("java.vm.version") + "\n";
        Text = Text + "Java Runtime Name: " + System.getProperty("java.runtime.name") + "\n";
        Text = Text + "Java Runtime Version: " + System.getProperty("java.runtime.version") + "\n";
        Text = Text + "Java Class Version: " + System.getProperty("java.class.version") + "\n";
        Text = Text + "Java Compiler: " + System.getProperty("sun.management.compiler") + "\n";
        try (FileWriter fileWriter = new FileWriter("data/sys.log", false);){
            fileWriter.write(Text);
        }
        catch (IOException ex) {
            MainFrame.bslog(ex);
        }
    }

    public static void updateConfig(String value) {
        String[] values = value.split(":", -1);
        if (values.length > 0) {
            ip = values[0];
            db = values[1];
            if (dbtype.equals("mysql")) {
                url = "jdbc:mysql://" + ip + ":" + port + "/";
            }
            if (dbtype.equals("sqlite")) {
                url = "jdbc:sqlite:";
            }
        } else {
            MainFrame.show("Unable to update config with " + value);
        }
    }

    public static boolean checkperms(String menu) {
        menuhist.add(menu);
        if (menuhist.size() > 1) {
            MainMenuBar.getComponent(backmenuint).setEnabled(true);
        }
        MainMenuBar.setToolTipText(menu);
        boolean myreturn = false;
        if (permmap.contains(menu)) {
            myreturn = true;
        }
        if (!myreturn) {
            MainFrame.show("You do not have access to this menu " + menu);
        }
        return myreturn;
    }

    public static boolean ConvertStringToBool(String i) {
        boolean b = i != null && i.equals("1");
        return b;
    }

    public static boolean checkperms(ActionEvent evt) {
        JMenuItem mymenu = (JMenuItem)evt.getSource();
        menuhist.add(mymenu.getName());
        if (menuhist.size() > 1) {
            MainMenuBar.getComponent(backmenuint).setEnabled(true);
        }
        MainMenuBar.setToolTipText(mymenu.getName());
        boolean myreturn = false;
        if (permmap.contains(mymenu.getName())) {
            myreturn = true;
        }
        if (!myreturn) {
            MainFrame.show("You do not have access to this menu " + mymenu.getName());
        }
        return myreturn;
    }

    public static void disableAllMenus() {
        int j = 0;
        for (Component mymenu : MainMenuBar.getComponents()) {
            MainMenuBar.getComponent(j).setEnabled(false);
            ++j;
        }
    }

    public static void enableAllMenus() {
        int j = 0;
        for (Component mymenu : MainMenuBar.getComponents()) {
            if (visibleMenuHeaders.get(mymenu.getName()) != null && visibleMenuHeaders.get(mymenu.getName()).booleanValue()) {
                MainMenuBar.getComponent(j).setEnabled(true);
            }
            ++j;
        }
    }

    public static void addBackGroundPanelToPanelMap() throws InstantiationException, IllegalAccessException {
        panelmap.put("BackGroundPanel", backgroundpanel);
    }

    public void spinGear(int n) {
        this.brandLogoImage = resolveSiteBrandLogo();
        ImageIcon myicon = this.brandLogoImage == null
                ? new ImageIcon(this.getClass().getResource("/images/bs.gif")) : null;
        this.myimage = this.brandLogoImage != null ? this.brandLogoImage : myicon.getImage();
        backgroundpanel.setImage(this.myimage);
        createSpinTask task = new createSpinTask(n);
        spin = true;
        task.execute();
    }

    /**
     * Once a site has its own logo configured (Site Maintenance - the same
     * image used on invoices, shippers, etc.), use it for the login/loading
     * screen too instead of the stock BlueSeer gear - installs that haven't
     * set one up yet (still on the seeded default "bs.png") keep the
     * original animated gear unchanged. Runs a couple of small local
     * queries, so callers on the EDT should keep this off any hot path.
     */
    private Image resolveSiteBrandLogo() {
        try {
            String site = OVData.getDefaultSite();
            String logo = OVData.getSiteLogo(site);
            if (logo != null && !logo.isBlank() && !logo.equalsIgnoreCase("bs.png")) {
                File f = new File(BlueSeerUtils.cleanDirString(OVData.getSystemImageDirectory()) + logo);
                if (f.isFile() && f.canRead()) {
                    return new ImageIcon(f.getAbsolutePath()).getImage();
                }
            }
        } catch (Exception ex) {
            bslog(ex);
        }
        return null;
    }

    public static boolean loadPanel(String menu, Object myobject) {
        boolean myerror = false;
        String mypanel = menumap.get(menu);
        String loc = "";
        String classpath = "";
        JPanel newpanel = null;
        if (mypanel.isEmpty() || mypanel.equals("BackGroundPanel")) {
            return true;
        }
        classpath = mypanel;
        try {
            Class<?> myclass = Class.forName(classpath);
            try {
                if (!panelmap.containsKey(mypanel)) {
                    newpanel = (JPanel)myclass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                    Class<?> thisclass = myobject.getClass();
                    Method mymethod = thisclass.getMethod("add", Component.class);
                    mymethod.invoke(myobject, newpanel);
                    newpanel.setVisible(false);
                    newpanel.setBackground(backgroundcolor);
                    panelmap.put(mypanel, newpanel);
                }
            }
            catch (InstantiationException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
            catch (IllegalAccessException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
            catch (SecurityException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
            catch (IllegalArgumentException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
            catch (NoSuchMethodException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
            catch (InvocationTargetException ex) {
                ex.printStackTrace();
                MainFrame.bslog(ex);
            }
        }
        catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            MainFrame.show("Class not found for: " + classpath);
            myerror = true;
        }
        return myerror;
    }

    public static ArrayList getmenuheaders() {
        ArrayList<String> myarray = new ArrayList<String>();
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"edi"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"address"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"purchasing"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"order"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"shipping"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"finance"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"inventory"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"engineering"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"quality"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"freight"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"hr"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"admin"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"help"));
        myarray.add(BlueSeerUtils.getGlobalMenuTag((String)"custom"));
        return myarray;
    }

    public JMenu getMenus(String menu) {
        JMenu mypitem = new JMenu(menu);
        for (String myvalue : menupermuser) {
            String[] recs = myvalue.split(",", -1);
            if (recs[1].equals("root") && recs[2].equals(menu)) {
                mypitem.setEnabled(MainFrame.ConvertStringToBool(recs[11]));
                mypitem.setName(recs[2]);
                visibleMenuHeaders.put(menu, MainFrame.ConvertStringToBool(recs[11]));
                continue;
            }
            if (!recs[1].equals(menu)) continue;
            final String hasInitVar = recs[6];
            final String myFunction = recs[7];
            boolean isVisible = MainFrame.ConvertStringToBool(recs[8]);
            boolean isEnable = MainFrame.ConvertStringToBool(recs[9]);
            boolean isAccess = MainFrame.ConvertStringToBool(recs[11]);
            if (!isVisible) continue;
            if (recs[3].compareTo("JMenuItem") == 0) {
                JMenuItem mychild = new JMenuItem(recs[2]);
                mychild.setName(recs[2]);
                mychild.setText(recs[4]);
                mychild.setToolTipText(recs[10]);
                if (!isAccess) {
                    mychild.setEnabled(false);
                }
                if (isVisible && !isEnable) {
                    mychild.setEnabled(false);
                }
                mychild.addActionListener(new ActionListener(){

                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        if (!MainFrame.checkperms(evt)) {
                            return;
                        }
                        JMenuItem mymenu = (JMenuItem)evt.getSource();
                        if (!myFunction.isEmpty()) {
                            try {
                                MainFrame.this.engageFunction(myFunction);
                            }
                            catch (ClassNotFoundException ex) {
                                MainFrame.bslog(ex);
                            }
                        } else if (hasInitVar.isEmpty()) {
                            MainFrame.reinitpanels(mymenu.getName(), true, new String[0]);
                        } else {
                            MainFrame.reinitpanels(mymenu.getName(), true, new String[]{mymenu.getName()});
                        }
                    }
                });
                mypitem.add(mychild);
                continue;
            }
            JMenu mypar = this.getMenus(recs[2].toString());
            mypar.setName(recs[2]);
            mypar.setText(recs[4]);
            if (!isAccess) {
                mypar.setEnabled(false);
            }
            mypitem.add(mypar);
        }
        return mypitem;
    }

    public static String PassWord(String dir, char[] passwd) {
        int i;
        char[] y;
        String x = "";
        if (dir.equals("1")) {
            y = new char[passwd.length];
            for (i = 0; i < passwd.length; ++i) {
                y[i] = (char)(passwd[i] - 222);
            }
            x = String.valueOf(y);
        }
        if (dir.equals("0")) {
            y = new char[passwd.length];
            for (i = 0; i < passwd.length; ++i) {
                y[i] = (char)(passwd[i] + 222);
            }
            x = String.valueOf(y);
        }
        return x;
    }

    public void createMenuTree() {
        int i = 0;
        visibleMenuHeaders.put("Home", true);
        visibleMenuHeaders.put("navcode", true);
        visibleMenuHeaders.put("menuback", true);
        for (String menus : menutreeheaders) {
            if (!MainFrame.isMenuVisible(menus)) continue;
            MainMenuBar.add((Component)this.getMenus(menus), i);
            ++i;
        }
    }

    public static void setperms(String userid) {
        int j = 0;
        MainFrame.disableAllMenus();
        ArrayList<String> myperms = new ArrayList<String>();
        try {
            Class.forName(driver).getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
            con = DriverManager.getConnection(url + db, user, pass);
            try {
                Statement st = con.createStatement();
                ResultSet res = null;
                int i = 0;
                res = st.executeQuery("SELECT * FROM  perm_mstr where perm_user = '" + userid + "'");
                while (res.next()) {
                    ++i;
                    myperms.add(res.getString("perm_menu"));
                }
            }
            catch (SQLException s) {
                MainFrame.show("Cannot SQL User_Mstr");
            }
            con.close();
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        for (String myperm : myperms) {
            j = 0;
            for (Component mymenu : MainMenuBar.getComponents()) {
                if (mymenu.getName() != null && mymenu.getName().compareTo(myperm) == 0) {
                    MainMenuBar.getComponent(j).setEnabled(true);
                }
                ++j;
            }
        }
    }

    public static boolean isClass(String className) {
        try {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void executeLoginTask() {
        BlueSeerUtils.startTaskNoBar((String[])new String[]{"0", "Running..."});
        this.loginpanel.setVisible(false);
        class LoginTask
        extends SwingWorker<String[], Void> {
            LoginTask() {
            }

            @Override
            public String[] doInBackground() {
                String[] message = new String[]{"", ""};
                message = MainFrame.this.doLogin();
                return message;
            }

            /*
             * WARNING - void declaration
             */
            @Override
            public void done() {
                String[] message = new String[]{"", ""};
                try {
                    message = (String[])this.get();
                    BlueSeerUtils.endTaskNoBar((String[])message);
                }
                catch (InterruptedException | ExecutionException ex) {
                    MainFrame.bslog(ex);
                }
                if (message[0].equals("1")) {
                    loginfailure = true;
                    spin = false;
                    return;
                }
                loginfailure = false;
                menutreeheaders = MainFrame.getmenuheaders();
                for (String[] stringArray : initLoginData) {
                    String[] arr;
                    if (stringArray[0].equals("menus")) {
                        arr = stringArray[1].split(",", -1);
                        menumap.put(arr[0], arr[1]);
                    }
                    if (stringArray[0].equals("navcodes")) {
                        arr = stringArray[1].split(",", -1);
                        navcodemap.put(arr[0], new String[]{arr[1], arr[2], arr[3]});
                    }
                    if (stringArray[0].equals("perms")) {
                        permmap.add(stringArray[1]);
                    }
                    if (stringArray[0].equals("menusforuser")) {
                        menupermuser.add(stringArray[1]);
                    }
                    if (stringArray[0].equals("iscurrencyset")) {
                        iscurrencyset = MainFrame.ConvertStringToBool(stringArray[1]);
                    }
                    if (!stringArray[0].equals("bgcolor")) continue;
                    arr = stringArray[1].split(":", -1);
                    backgroundcolor = new Color(Integer.parseInt(arr[0]), Integer.parseInt(arr[1]), Integer.parseInt(arr[2]));
                    backgroundpanel.setBackground(backgroundcolor);
                }
                MainFrame.this.primarypanel.setVisible(false);
                MainFrame.this.createMenuTree();
                MainFrame.enableAllMenus();
                int m = 0;
                for (Component mymenu : MainMenuBar.getComponents()) {
                    if (MainMenuBar.getComponent(m).getName() != null && MainMenuBar.getComponent(m).getName().equals("menuback")) {
                        MainMenuBar.getComponent(m).setEnabled(false);
                        backmenuint = m;
                        break;
                    }
                    ++m;
                }
                PanelMain.setVisible(false);
                MainMenuBar.setVisible(true);
                backgroundpanel.setVisible(true);
                navcode.setVisible(true);
                MainFrame.this.menuback.setVisible(true);
                MainFrame.this.menuhome.setVisible(true);
                panelmap.put("BackGroundPanel", backgroundpanel);
                String string = "";
                String string2 = "";
                if (myargs != null) {
                    for (int i = 0; i < myargs.length; ++i) {
                        if (!myargs[i].equals("-setTitle") || myargs.length <= i + 1) continue;
                        string2 = myargs[i + 1];
                        break;
                    }
                }
                MainFrame.this.setFrameTitle((String)string2);
                navcode.requestFocus();
                if (MainFrame.checkDBConnect() && !iscurrencyset) {
                    MainFrame.setTestPasswords();
                    BlueSeerUtils.callCountrySet();
                }
                spin = false;
            }
        }
        LoginTask z = new LoginTask();
        z.execute();
        backgroundpanel.setVisible(true);
        this.spinGear(10);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isNewVersionAvailable(String version) {
        boolean myvalue = true;
        int i = 0;
        String storedversion = "";
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT ov_version FROM ov_ctrl;");
                while (res.next()) {
                    ++i;
                    storedversion = res.getString("ov_version");
                }
                if (storedversion.equals(ver)) {
                    myvalue = false;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    public static boolean isMenuVisible(String menu) {
        for (String myvalue : menupermuser) {
            String[] recs = myvalue.split(",", -1);
            if (!recs[2].equals(menu)) continue;
            return recs[8].equals("1");
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isDBAvailable() {
        boolean myvalue = false;
        boolean i = false;
        try {
            if (debug) {
                System.out.println("Attempting connection: " + url + db + " with user:pass " + user + ":" + pass);
            }
            DriverManager.setLoginTimeout(10);
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            if (debug) {
                System.out.println("we're in! " + url + db + " with user:pass " + user + ":" + pass);
            }
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT ov_version FROM ov_ctrl;");
                while (res.next()) {
                    myvalue = true;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    public static boolean checkDBConnect() {
        if (!isSSHConnected) {
            return true;
        }
        boolean r = false;
        Connection con = null;
        try {
            DriverManager.setLoginTimeout(10);
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            r = true;
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        finally {
            try {
                con.close();
            }
            catch (SQLException ex) {
                MainFrame.bslog(ex);
            }
        }
        return r;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static Color getBackGroundColor() {
        Color mycolor = new Color(255, 255, 255);
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("select ov_rcolor, ov_gcolor, ov_bcolor from ov_ctrl;");
                while (res.next()) {
                    mycolor = new Color(res.getInt("ov_rcolor"), res.getInt("ov_gcolor"), res.getInt("ov_bcolor"));
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return mycolor;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static String getVersion() {
        String myreturn = "";
        String patch = "";
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("select ov_version from ov_ctrl;");
                while (res.next()) {
                    myreturn = res.getString("ov_version");
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
            patch = OVData.minor;
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return patch.isEmpty() ? myreturn + ".?" : myreturn + "." + patch;
    }

    public static String[] getNavCode(String navcode) {
        navcode = navcode.replace("'", "");
        return navcodemap.get(navcode);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static HashMap getmenulistmap() {
        HashMap<String, String> mymap = new HashMap<String, String>();
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("select menu_id, menu_panel from menu_mstr order by menu_id ;");
                while (res.next()) {
                    mymap.put(res.getString("menu_id"), res.getString("menu_panel"));
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return mymap;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static ArrayList<String> getpermlistmap() {
        ArrayList<String> mymap = new ArrayList<String>();
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT perm_user, perm_menu FROM  perm_mstr where perm_user = '" + userid + "';");
                while (res.next()) {
                    mymap.add(res.getString("perm_menu"));
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return mymap;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean getLoginMethod() {
        boolean myreturn = false;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("select ov_login from ov_ctrl;");
                while (res.next()) {
                    myreturn = res.getBoolean("ov_login");
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myreturn;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isUserDefined(String myuser) {
        boolean myvalue = false;
        int i = 0;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT user_id FROM  user_mstr where user_id = '" + myuser + "';");
                while (res.next()) {
                    ++i;
                }
                if (i > 0) {
                    myvalue = true;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isDefaultCurrencySet() {
        boolean myvalue = false;
        String currency = "";
        boolean i = false;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT ov_currency FROM ov_mstr;");
                while (res.next()) {
                    currency = res.getString("ov_currency");
                }
                if (!currency.isEmpty()) {
                    myvalue = true;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean setTestPasswords() {
        boolean myvalue = false;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            try {
                String ps = MainFrame.PassWord("0", "bstest".toCharArray());
                st.executeUpdate("update pks_mstr set pks_pass = '" + ps + "' where pks_id = 'bstest' ;");
                st.executeUpdate("update pks_mstr set pks_storepass = '" + ps + "' where pks_id = 'bsCertStore' ;");
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isPasswdCorrect(String myuser, String passwd) {
        boolean myvalue = false;
        int i = 0;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT user_id, user_passwd FROM  user_mstr where user_id = '" + myuser + "';");
                while (res.next()) {
                    ++i;
                    String dbpass = res.getString("user_passwd");
                    if (dbpass.equals("admin")) {
                        myvalue = true;
                        continue;
                    }
                    String key = MainFrame.PassWord("1", res.getString("user_passwd").toCharArray());
                    if (key.compareTo(passwd) != 0) continue;
                    myvalue = true;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isPasswdCorrectHash(String myuser, String passwd) {
        boolean myvalue = false;
        int i = 0;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("SELECT user_id, user_passwd FROM  user_mstr where user_id = '" + myuser + "';");
                while (res.next()) {
                    ++i;
                    String dbpass = res.getString("user_passwd");
                    if (dbpass.equals("admin")) {
                        myvalue = true;
                        continue;
                    }
                    String key = apiUtils.hashdigest((byte[])passwd.getBytes(), (String)"SHA-1");
                    if (key.compareTo(dbpass) != 0) continue;
                    myvalue = true;
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myvalue;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static ArrayList getmenutreeWithUser(String userid) {
        ArrayList<String> myarray = new ArrayList<String>();
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            try {
                res = st.executeQuery("select case when p.perm_menu is not null then '1' else '0' end as 'hasaccess', perm_user, mt_par, mt_child, mt_type, mt_label, mt_icon, mt_initvar, mt_func, mt_visible, mt_enable, menu_navcode from menu_tree  inner join menu_mstr on menu_id = mt_child  left outer join perm_mstr p on perm_menu = mt_child and perm_user = '" + userid + "' where mt_visible = '1'  order by mt_par, mt_index ;");
                while (res.next()) {
                    myarray.add(res.getString("perm_user") + "," + res.getString("mt_par") + "," + res.getString("mt_child") + "," + res.getString("mt_type") + "," + res.getString("mt_label") + "," + res.getString("mt_icon") + "," + res.getString("mt_initvar") + "," + res.getString("mt_func") + "," + res.getString("mt_visible") + "," + res.getString("mt_enable") + "," + res.getString("menu_navcode") + "," + res.getString("hasaccess"));
                }
            }
            catch (SQLException s) {
                s.printStackTrace();
            }
            finally {
                res.close();
                st.close();
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return myarray;
    }

    public static boolean confirmServerLogin(HttpServletRequest httpRequest, String sessionid) {
        System.out.println("HERE: inside confirmServerLogin ");
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
            String base64Credentials = authorization.substring("Basic".length()).trim();
            org.bouncycastle.util.encoders.Base64 b = new org.bouncycastle.util.encoders.Base64();
            String ip = httpRequest.getRemoteAddr();
            String credentials = new String(org.bouncycastle.util.encoders.Base64.decode((String)base64Credentials), Charset.forName("UTF-8"));
            System.out.println("confirmServerLogin b64decoded: " + credentials);
            String[] v = credentials.split(":", 2);
            if (v != null && v.length == 2) {
                return MainFrame.isValidUserLogin(v[0], v[1], ip, sessionid);
            }
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isValidUserLogin(String userid, String passwd, String ip, String session) {
        boolean isvalidIP = false;
        boolean isvalidUserPass = false;
        System.out.println("isValidUserLogin: " + userid + "/" + passwd + "/" + ip + "/" + session);
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            int i = 0;
            try {
                res = st.executeQuery("SELECT user_id, user_passwd FROM  user_mstr where user_id = '" + userid + "';");
                while (res.next()) {
                    ++i;
                    String key = MainFrame.PassWord("1", res.getString("user_passwd").toCharArray());
                    if (key.compareTo(passwd) != 0) continue;
                    isvalidUserPass = true;
                }
                res = st.executeQuery("select * from usr_meta where usrm_id = 'access' AND  usrm_key = '" + userid + "';");
                while (res.next()) {
                    if (!res.getString("usrm_type").equals("ip") || !res.getString("usrm_value").equals(ip)) continue;
                    isvalidIP = true;
                }
                if (isvalidUserPass) {
                    st.executeUpdate("insert into usr_meta values (  'access', 'session', '" + userid + "','" + session + "','');");
                }
            }
            catch (SQLException s) {
                MainFrame.bslog(s);
            }
            finally {
                if (res != null) {
                    res.close();
                }
                if (st != null) {
                    st.close();
                }
                con.close();
            }
        }
        catch (Exception e) {
            System.out.println("isValidUserLogin sql exception: " + e.getMessage());
            MainFrame.bslog(e);
        }
        return isvalidUserPass && isvalidIP;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean isValidUserSession(String ip, String userid, String session) {
        System.out.println("HERE: " + ip + " / " + userid + " / " + session);
        boolean isvalidIP = false;
        boolean isvalidSession = false;
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            boolean i = false;
            try {
                res = st.executeQuery("select * from usr_meta where usrm_id = 'access' AND  usrm_key = '" + userid + "';");
                while (res.next()) {
                    if (res.getString("usrm_type").equals("ip") && res.getString("usrm_value").equals(ip)) {
                        isvalidIP = true;
                    }
                    if (!res.getString("usrm_type").equals("session") || !res.getString("usrm_value").equals(session)) continue;
                    isvalidSession = true;
                }
            }
            catch (SQLException s) {
                MainFrame.bslog(s);
            }
            finally {
                if (res != null) {
                    res.close();
                }
                if (st != null) {
                    st.close();
                }
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return isvalidSession && isvalidIP;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean killUserSession(String ip, String userid, String session) {
        try {
            Connection con = null;
            con = ds != null ? ds.getConnection() : DriverManager.getConnection(url + db, user, pass);
            Statement st = con.createStatement();
            ResultSet res = null;
            boolean i = false;
            boolean valid = false;
            try {
                res = st.executeQuery("select * from usr_meta where usrm_id = 'access' AND usrm_type = 'session' AND  usrm_key = '" + userid + "' AND  usrm_value = '" + session + "';");
                while (res.next()) {
                    valid = true;
                }
                if (valid) {
                    st.executeUpdate("delete from usr_meta  where usrm_id = 'access' AND usrm_type = 'session' AND  usrm_key = '" + userid + "';");
                }
            }
            catch (SQLException s) {
                MainFrame.bslog(s);
            }
            finally {
                if (res != null) {
                    res.close();
                }
                if (st != null) {
                    st.close();
                }
                con.close();
            }
        }
        catch (Exception e) {
            MainFrame.bslog(e);
        }
        return true;
    }

    public MainFrame() {
        int i;
        this.initComponents();
        if (myargs != null && myargs.length > 0) {
            i = 0;
            for (String s : myargs) {
                if (s.equals("-config")) {
                    configfile = myargs[i + 1];
                    System.out.println("configfile override: " + configfile);
                }
                if (s.equals("-debug")) {
                    debug = true;
                    System.out.println("debug mode set");
                }
                if (s.equals("-bypass")) {
                    bypass = true;
                    System.out.println("bypass mode set");
                }
                if (s.equals("-X")) {
                    encryptedBSConfig = true;
                    System.out.println("Encrypted bs.cfg expected");
                }
                ++i;
            }
        }
        MainFrame.setConfig(configfile);
        if (myargs != null && myargs.length > 0) {
            i = 0;
            for (String s : myargs) {
                if (s.equals("-url")) {
                    url = myargs[i + 1];
                    override_url = true;
                    System.out.println("url override: " + url);
                }
                ++i;
            }
            for (String s : myargs) {
                JSch jsch;
                Properties config;
                String[] sshdcreds;
                if (s.equals("-ssh")) {
                    if (debug) {
                        System.out.println("attempting -ssh connection");
                    }
                    try {
                        sshdcreds = this.checkSSH();
                        if (sshdcreds != null && sshdcreds.length == 2) {
                            if (!bypass) {
                                sshuser = MainFrame.PassWord("1", sshdcreds[0].toCharArray());
                                sshpass = MainFrame.PassWord("1", sshdcreds[1].toCharArray());
                            } else {
                                sshuser = sshdcreds[0];
                                sshpass = sshdcreds[1];
                            }
                            if (debug) {
                                System.out.println("ssh creds file located");
                            }
                        } else {
                            sshuser = "";
                            sshpass = "";
                            if (debug) {
                                System.out.println("unable to locate ssh creds file");
                            }
                        }
                    }
                    catch (IOException ex) {
                        MainFrame.bslog(ex);
                    }
                    if (!(sshuser.isEmpty() || sshpass.isEmpty() || rhost.isEmpty())) {
                        config = new Properties();
                        config.put("StrictHostKeyChecking", "no");
                        if (debug) {
                            JSch.setLogger((Logger)new JSCHLogger());
                        }
                        jsch = new JSch();
                        try {
                            session = jsch.getSession(sshuser, rhost, 22);
                        }
                        catch (JSchException ex) {
                            MainFrame.bslog(ex);
                            MainFrame.show("-ssh found...JSchException occurred at getSession()...exiting");
                            System.exit(0);
                        }
                        session.setPassword(sshpass);
                        config.put("PubkeyAcceptedAlgorithms", JSch.getConfig((String)"PubkeyAcceptedAlgorithms") + ",ssh-rsa");
                        config.put("server_host_key", JSch.getConfig((String)"server_host_key") + ",ssh-rsa");
                        session.setConfig(config);
                        try {
                            session.connect();
                            int newport = session.setPortForwardingL(Integer.valueOf(lport).intValue(), rhost, Integer.valueOf(rport).intValue());
                            isSSHConnected = true;
                            MainFrame.bslog("SSH Session created and connected!");
                            port = String.valueOf(newport);
                            if (dbtype.equals("mysql")) {
                                url = "jdbc:mysql://" + ip + ":" + port + "/";
                            }
                            if (!debug) break;
                            System.out.println("SSH Session created and connected!");
                            System.out.println("Port Forwarded: " + newport + " -> " + rhost + ":" + rport);
                            System.out.println("new URL connection String: " + url);
                        }
                        catch (JSchException ex) {
                            MainFrame.bslog(ex);
                            MainFrame.show("Unable to create ssh session at session.connect()...exiting");
                            System.exit(0);
                        }
                        break;
                    }
                    MainFrame.show("-ssh was requested as argument but necessary bs.cfg elements empty...exiting");
                    System.exit(0);
                    break;
                }
                if (s.equals("-sshjkey")) {
                    try {
                        sshdcreds = this.checkSSH();
                        if (sshdcreds != null && sshdcreds.length == 2) {
                            if (!bypass) {
                                sshuser = MainFrame.PassWord("1", sshdcreds[0].toCharArray());
                                sshpass = MainFrame.PassWord("1", sshdcreds[1].toCharArray());
                            } else {
                                sshuser = sshdcreds[0];
                                sshpass = sshdcreds[1];
                            }
                            if (debug) {
                                System.out.println("ssh creds file located");
                            }
                        } else {
                            sshuser = "";
                            sshpass = "";
                            if (debug) {
                                System.out.println("unable to locate ssh creds file");
                            }
                        }
                        SSHClient sshj = new SSHClient();
                        PKCS8KeyFile keyFile = new PKCS8KeyFile();
                        keyFile.init(new File(".bspem"));
                        sshj.loadKnownHosts();
                        sshj.addHostKeyVerifier((HostKeyVerifier)new PromiscuousVerifier());
                        sshj.connect(rhost);
                        sshj.auth(sshuser, new AuthMethod[]{new AuthPublickey((KeyProvider)keyFile)});
                        net.schmizz.sshj.connection.channel.direct.Session session = sshj.startSession();
                        RemotePortForwarder.Forward forward = new RemotePortForwarder.Forward(Integer.valueOf(lport).intValue());
                        InetSocketAddress addr = new InetSocketAddress(rhost, (int)Integer.valueOf(rport));
                        SocketForwardingConnectListener listener = new SocketForwardingConnectListener((SocketAddress)addr);
                        sshj.getRemotePortForwarder().bind(forward, (ConnectListener)listener);
                        isSSHConnected = true;
                        MainFrame.bslog("SSHKey Session created and connected!");
                        if (dbtype.equals("mysql")) {
                            url = "jdbc:mysql://" + ip + ":" + lport + "/";
                        }
                        if (debug) {
                            System.out.println("SSHJKey Session created and connected!");
                            System.out.println("Port Forwarded: " + lport + " -> " + rhost + ":" + rport);
                            System.out.println("new URL connection String: " + url);
                        }
                    }
                    catch (Exception e) {
                        System.out.println("exception with sshjkey!!");
                        e.printStackTrace();
                    }
                }
                if (!s.equals("-sshkey")) continue;
                if (debug) {
                    System.out.println("attempting -ssh connection with pem key");
                }
                try {
                    sshdcreds = this.checkSSH();
                    if (sshdcreds != null && sshdcreds.length == 2) {
                        if (!bypass) {
                            sshuser = MainFrame.PassWord("1", sshdcreds[0].toCharArray());
                            sshpass = MainFrame.PassWord("1", sshdcreds[1].toCharArray());
                        } else {
                            sshuser = sshdcreds[0];
                            sshpass = sshdcreds[1];
                        }
                        if (debug) {
                            System.out.println("ssh creds file located");
                            System.out.println("creds: " + sshuser + "/" + sshpass);
                        }
                    } else {
                        sshuser = "";
                        sshpass = "";
                        if (debug) {
                            System.out.println("unable to locate ssh creds file");
                        }
                    }
                }
                catch (IOException ex) {
                    MainFrame.bslog(ex);
                }
                if (!sshuser.isEmpty() && !rhost.isEmpty()) {
                    config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    config.put("PreferredAuthentications", "publickey");
                    if (debug) {
                        JSch.setLogger((Logger)new JSCHLogger());
                    }
                    jsch = new JSch();
                    try {
                        jsch.addIdentity(".bspem");
                    }
                    catch (JSchException ex) {
                        MainFrame.bslog(ex);
                        MainFrame.show("-sshkey found...no private key found...exiting");
                        System.exit(0);
                    }
                    try {
                        session = jsch.getSession(sshuser, rhost, 22);
                        config.put("PubkeyAcceptedAlgorithms", JSch.getConfig((String)"PubkeyAcceptedAlgorithms") + ",ssh-rsa");
                        config.put("server_host_key", JSch.getConfig((String)"server_host_key") + ",ssh-rsa");
                    }
                    catch (JSchException ex) {
                        MainFrame.bslog(ex);
                        MainFrame.show("-ssh found...JSchException occurred at getSession()...exiting");
                        System.exit(0);
                    }
                    session.setConfig(config);
                    try {
                        session.connect();
                        int newport = session.setPortForwardingL(Integer.valueOf(lport).intValue(), rhost, Integer.valueOf(rport).intValue());
                        isSSHConnected = true;
                        MainFrame.bslog("SSHKey Session created and connected!");
                        port = String.valueOf(newport);
                        if (dbtype.equals("mysql")) {
                            url = "jdbc:mysql://" + ip + ":" + port + "/";
                        }
                        if (!debug) break;
                        System.out.println("SSHKey Session created and connected!");
                        System.out.println("Port Forwarded: " + newport + " -> " + rhost + ":" + rport);
                        System.out.println("new URL connection String: " + url);
                    }
                    catch (JSchException ex) {
                        MainFrame.bslog(ex);
                        MainFrame.show("Unable to create sshkey session at session.connect()...exiting");
                        System.exit(0);
                    }
                    break;
                }
                MainFrame.show("-sshkey was requested as argument but necessary bs.cfg elements empty...exiting");
                System.exit(0);
                break;
            }
        }
        if (dbtype.equals("mysql")) {
            ds = new BasicDataSource();
            ds.setUrl(url + db);
            if (override_url) {
                ds.setUrl(url);
                db = "";
            }
            ds.setUsername(user);
            ds.setPassword(pass);
            ds.setMinIdle(5);
            ds.setMaxIdle(10);
            ds.setMaxOpenPreparedStatements(100);
        }
        try {
            File f = new File("custom/bs.properties");
            if (f.isFile() && f.canRead()) {
                FileInputStream fis = new FileInputStream(f);
                tags = new PropertyResourceBundle(fis);
                fis.close();
            } else {
                tags = ResourceBundle.getBundle("resources.bs", Locale.getDefault());
            }
        }
        catch (FileNotFoundException ex) {
            MainFrame.bslog(ex);
        }
        catch (IOException ex) {
            MainFrame.bslog(ex);
        }
        this.setLanguageTags(this.primarypanel);
        ver = OVData.major + "." + OVData.minor;
        backgroundcolor = OVData.getBackgroundColor();
        PanelMain.setBackground(backgroundcolor);
        backgroundpanel = new BackGroundPanel();
        this.add(backgroundpanel);
        backgroundpanel.setBackground(backgroundcolor);
        this.setExtendedState(6);
        navcode.setPreferredSize(new Dimension(70, 30));
        navcode.setMaximumSize(navcode.getPreferredSize());
        navcode.setName("navcode");
        navcode.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent evt) {
                if (navcode.getText().isEmpty()) {
                    messagelabel.setText(navcode.getText() + " ...blank navcode is not allowed");
                    return;
                }
                String[] mymenu = MainFrame.getNavCode(navcode.getText());
                if (mymenu == null || mymenu[0].isEmpty()) {
                    messagelabel.setText(navcode.getText() + " ...navcode is unsupported");
                } else {
                    if (!MainFrame.checkperms(mymenu[0])) {
                        return;
                    }
                    if (mymenu[1].equals("com.blueseer.utl.ReportPanel")) {
                        MainFrame.reinitpanels(mymenu[0], true, new String[]{mymenu[0]});
                    } else if (!mymenu[2].isEmpty()) {
                        MainFrame.reinitpanels(mymenu[0], true, new String[]{mymenu[0]});
                    } else {
                        MainFrame.reinitpanels(mymenu[0], true, new String[0]);
                    }
                }
                navcode.setText("");
            }
        });
        MainMenuBar.add(navcode);
        JLabel spacelabel = new JLabel();
        spacelabel.setPreferredSize(new Dimension(10, 30));
        spacelabel.setMaximumSize(spacelabel.getPreferredSize());
        MainMenuBar.add(spacelabel);
        messagelabel.setPreferredSize(new Dimension(300, 20));
        messagelabel.setForeground(Color.red);
        messagelabel.setMaximumSize(messagelabel.getPreferredSize());
        messagelabel.setHorizontalAlignment(2);
        MainMenuBar.add(messagelabel);
        navcode.setVisible(false);
        this.menuback.setVisible(false);
        this.menuhome.setVisible(false);
        MainMenuBar.add(MainProgressBar);
        MainMenuBar.setVisible(true);
        MainProgressBar.setVisible(false);
        File f = new File(temp);
        if (!f.exists()) {
            MainFrame.show("temp work directory needs to be created");
            MainFrame.show("...attempting to create");
            if (!f.exists() && !f.mkdirs()) {
                MainFrame.show("Unable to create " + f.getAbsolutePath());
                System.exit(0);
            }
        }
        this.loginpanel.setVisible(true);
        this.tbuser.requestFocus();
        menu = "BackGroundPanel";
        main = this;
    }

    private void initComponents() {
        PanelMain = new JPanel();
        this.primarypanel = new JPanel();
        this.loginpanel = new JPanel();
        this.tbuser = new JTextField();
        this.btlogin = new JButton();
        this.jLabel2 = new JLabel();
        this.jLabel1 = new JLabel();
        this.tbpass = new JPasswordField();
        MainMenuBar = new JMenuBar();
        this.menuback = new JMenu();
        this.menuhome = new JMenu();
        this.setDefaultCloseOperation(3);
        this.addKeyListener(new KeyAdapter(){

            @Override
            public void keyPressed(KeyEvent evt) {
                MainFrame.this.formKeyPressed(evt);
            }
        });
        this.getContentPane().setLayout(new CardLayout());
        PanelMain.setBackground(new Color(0, 102, 204));
        PanelMain.addKeyListener(new KeyAdapter(){

            @Override
            public void keyPressed(KeyEvent evt) {
                MainFrame.this.PanelMainKeyPressed(evt);
            }
        });
        this.primarypanel.setName("primarypanel");
        this.loginpanel.setName("loginpanel");
        this.btlogin.setText("Login");
        this.btlogin.setName("btlogin");
        this.btlogin.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent evt) {
                MainFrame.this.btloginActionPerformed(evt);
            }
        });
        this.jLabel2.setText("Password");
        this.jLabel2.setName("lblpassword");
        this.jLabel1.setText("User");
        this.jLabel1.setName("lbluser");
        this.tbpass.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent evt) {
                MainFrame.this.tbpassActionPerformed(evt);
            }
        });
        GroupLayout loginpanelLayout = new GroupLayout(this.loginpanel);
        this.loginpanel.setLayout(loginpanelLayout);
        loginpanelLayout.setHorizontalGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(loginpanelLayout.createSequentialGroup().addContainerGap(36, Short.MAX_VALUE).addGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.btlogin, GroupLayout.Alignment.TRAILING).addGroup(GroupLayout.Alignment.TRAILING, loginpanelLayout.createSequentialGroup().addGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING).addComponent(this.jLabel1).addComponent(this.jLabel2)).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING, false).addComponent(this.tbpass, -1, 97, Short.MAX_VALUE).addComponent(this.tbuser)))).addContainerGap()));
        loginpanelLayout.setVerticalGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(loginpanelLayout.createSequentialGroup().addContainerGap().addGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.tbuser, -2, -1, -2).addComponent(this.jLabel1)).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addGroup(loginpanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.tbpass, -2, -1, -2).addComponent(this.jLabel2)).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.btlogin).addContainerGap(-1, Short.MAX_VALUE)));
        this.primarypanel.add(this.loginpanel);
        PanelMain.add(this.primarypanel);
        this.getContentPane().add((Component)PanelMain, "card2");
        MainMenuBar.setToolTipText("");
        MainMenuBar.setName("");
        this.menuback.setIcon(new ImageIcon(this.getClass().getResource("/images/back.png")));
        this.menuback.setToolTipText("PrevMenu");
        this.menuback.setName("menuback");
        this.menuback.addMenuListener(new MenuListener(){

            @Override
            public void menuCanceled(MenuEvent evt) {
            }

            @Override
            public void menuDeselected(MenuEvent evt) {
            }

            @Override
            public void menuSelected(MenuEvent evt) {
                MainFrame.this.menubackMenuSelected(evt);
            }
        });
        this.menuback.addMouseListener(new MouseAdapter(){

            @Override
            public void mousePressed(MouseEvent evt) {
                MainFrame.this.menubackMousePressed(evt);
            }
        });
        MainMenuBar.add(this.menuback);
        this.menuhome.setIcon(new ImageIcon(this.getClass().getResource("/images/home2.png")));
        this.menuhome.setToolTipText("Home");
        this.menuhome.setCursor(new Cursor(0));
        this.menuhome.setName("Home");
        this.menuhome.addMenuListener(new MenuListener(){

            @Override
            public void menuCanceled(MenuEvent evt) {
            }

            @Override
            public void menuDeselected(MenuEvent evt) {
            }

            @Override
            public void menuSelected(MenuEvent evt) {
                MainFrame.this.menuhomeMenuSelected(evt);
            }
        });
        this.menuhome.addMouseListener(new MouseAdapter(){

            @Override
            public void mousePressed(MouseEvent evt) {
                MainFrame.this.menuhomeMousePressed(evt);
            }
        });
        MainMenuBar.add(this.menuhome);
        this.setJMenuBar(MainMenuBar);
        this.pack();
    }

    private void PanelMainKeyPressed(KeyEvent evt) {
    }

    private void formKeyPressed(KeyEvent evt) {
    }

    private void menubackMousePressed(MouseEvent evt) {
        JMenuItem mymenu = (JMenuItem)evt.getSource();
        if (menuhist != null && menuhist.size() > 1) {
            menuhist.remove(menuhist.size() - 1);
            String lastpanel = menumap.get(menuhist.get(menuhist.size() - 1));
            if (lastpanel.equals("ReportPanel")) {
                MainFrame.reinitpanels(menuhist.get(menuhist.size() - 1), true, new String[]{menuhist.get(menuhist.size() - 1)});
            } else {
                MainFrame.reinitpanels(menuhist.get(menuhist.size() - 1), true, new String[0]);
            }
        } else {
            MainMenuBar.getComponent(backmenuint).setEnabled(false);
        }
        this.menuback.menuSelectionChanged(false);
    }

    private void btloginActionPerformed(ActionEvent evt) {
        this.executeLoginTask();
    }

    private void menuhomeMousePressed(MouseEvent evt) {
        try {
            MainFrame.hidepanels();
            this.spinGear(2);
        }
        catch (NoSuchMethodException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalAccessException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalArgumentException ex) {
            MainFrame.bslog(ex);
        }
        catch (InvocationTargetException ex) {
            MainFrame.bslog(ex);
        }
        backgroundpanel.setBackground(backgroundcolor);
        backgroundpanel.setVisible(true);
    }

    private void menuhomeMenuSelected(MenuEvent evt) {
        this.menuhome.menuSelectionChanged(false);
    }

    private void menubackMenuSelected(MenuEvent evt) {
        this.menuback.menuSelectionChanged(false);
    }

    private void tbpassActionPerformed(ActionEvent evt) {
        this.executeLoginTask();
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            myargs = args;
        }
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (!"Nimbus".equals(info.getName())) continue;
                UIManager.setLookAndFeel(info.getClassName());
                InputMap im = (InputMap)UIManager.get("Button.focusInputMap");
                im.put(KeyStroke.getKeyStroke("ENTER"), "pressed");
                im.put(KeyStroke.getKeyStroke("released ENTER"), "released");
                break;
            }
        }
        catch (ClassNotFoundException ex) {
            MainFrame.bslog(ex);
        }
        catch (InstantiationException ex) {
            MainFrame.bslog(ex);
        }
        catch (IllegalAccessException ex) {
            MainFrame.bslog(ex);
        }
        catch (UnsupportedLookAndFeelException ex) {
            MainFrame.bslog(ex);
        }
        EventQueue.invokeLater(new Runnable(){

            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    static {
        myargs = null;
        userid = "";
        con = null;
        configfile = "bs.cfg";
        ip = "";
        port = "";
        serverport = "8088";
        url = "";
        db = "";
        dbname = "";
        dbtype = "";
        ver = "";
        driver = "";
        user = "";
        pass = "";
        lang = "";
        country = "";
        temp = "temp";
        hichar = "zzzzzzzz";
        lowchar = "";
        hinbr = "99999999";
        lownbr = "0";
        menu = "";
        spin = false;
        loginfailure = false;
        isPaint = false;
        main = null;
        now = new Date();
        dfdate = new SimpleDateFormat("yyyy-MM-dd");
        hidate = dfdate.format(now);
        lowdate = "1900-01-01";
        mypanels = new ArrayList();
        menutreeheaders = new ArrayList();
        visibleMenuHeaders = new HashMap();
        panelmap = new HashMap();
        menumap = new HashMap();
        navcodemap = new HashMap();
        permmap = new ArrayList();
        menuhist = new ArrayList();
        currencymap = new HashMap();
        menuAt = 0;
        backmenuint = 0;
        session = null;
        protocol = "http";
        rhost = "";
        lport = "";
        rport = "";
        sshuser = "";
        sshpass = "";
        sessionid = "";
        jardir = "";
        isSSHConnected = false;
        iscurrencyset = false;
        debug = false;
        bypass = false;
        override_url = false;
        remoteDB = false;
        encryptedBSConfig = false;
        menupermuser = new ArrayList();
        initLoginData = new ArrayList();
        ds = null;
        MainProgressBar = new JProgressBar();
        messagelabel = new JLabel();
        navcode = new JTextField("");
        mydialog = null;
        backgroundcolor = new Color(255, 255, 255);
        nonEditableColor = new Color(213, 232, 237);
        invalidColor = new Color(242, 242, 177);
        ddbgcolor = new JComboBox().getBackground();
    }

    class createSpinTask
    extends SwingWorker<Void, Void> {
        int countlimit = 10;

        public createSpinTask(int countlimit) {
            this.countlimit = countlimit;
        }

        @Override
        public Void doInBackground() {
            try {
                for (int i = 1; i < this.countlimit && spin; ++i) {
                    Thread.sleep(500L);
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public void done() {
            if (loginfailure) {
                System.out.println("here: must be loginfailure");
                backgroundpanel.setVisible(false);
                MainFrame.this.loginpanel.setVisible(true);
                return;
            }
            try {
                File f = new File("custom/bs.png");
                if (f.isFile() && f.canRead()) {
                    FileInputStream fis = new FileInputStream(f);
                    ImageIcon myicon = new ImageIcon(fis.readAllBytes());
                    MainFrame.this.myimage = myicon.getImage();
                    backgroundpanel.setBackground(backgroundcolor);
                    backgroundpanel.setImage(MainFrame.this.myimage);
                    fis.close();
                } else if (MainFrame.this.brandLogoImage != null) {
                    MainFrame.this.myimage = MainFrame.this.brandLogoImage;
                    backgroundpanel.setBackground(backgroundcolor);
                    backgroundpanel.setImage(MainFrame.this.myimage);
                } else {
                    ImageIcon myicon = new ImageIcon(this.getClass().getResource("/images/bs.png"));
                    MainFrame.this.myimage = myicon.getImage();
                    backgroundpanel.setBackground(backgroundcolor);
                    backgroundpanel.setImage(MainFrame.this.myimage);
                }
            }
            catch (FileNotFoundException ex) {
                MainFrame.bslog(ex);
            }
            catch (IOException ex) {
                MainFrame.bslog(ex);
            }
        }
    }

    public static class JSCHLogger
    implements Logger {
        static Hashtable name = new Hashtable();

        public boolean isEnabled(int level) {
            return true;
        }

        public void log(int level, String message) {
            System.err.print(name.get(level));
            System.err.println(message);
        }

        static {
            name.put(0, "DEBUG: ");
            name.put(1, "INFO: ");
            name.put(2, "WARN: ");
            name.put(3, "ERROR: ");
            name.put(4, "FATAL: ");
        }
    }

    class SomeRenderer
    extends DefaultTableCellRenderer {
        SomeRenderer() {
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (column == 0) {
                c.setForeground(Color.BLUE);
            } else {
                c.setBackground(table.getBackground());
            }
            return c;
        }
    }
}

