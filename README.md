![ScreenShot](/src/images/bs65image.png)
<a href="https://foojay.io/today/works-with-openjdk"><img align="right" src="https://github.com/foojayio/badges/raw/main/works_with_openjdk/Works-with-OpenJDK.png" width="100"></a>
<h3>Developer: Terry Vaughn</h3>
<h3>latest release version: 8.0</h3>
<h3>latest release date: 2026-06-15</h3>
<h3>programming language: Java programming language</h3> 
<h3>operating system: Cross-Platform</h3>
<h3>genre:  Enterprise Resource Planning (ERP), EDI, Accounting, Personal Finance</h3> 
<h3>languages supported: English, French, Spanish, Turkish, German, Romanian, Arabic, Chinese</h3>
<h3>license: MIT License</h3>
<h3>website: www.blueseer.com</h3>



![ScreenShot](/src/images/market2.png)
'''BlueSeer ERP''' is a Free open source multilingual ERP software package.  It was designed to meet the needs of
the manufacturing community for an ERP system that is easily customizable and
extendable while providing generic functionality that is typically observed in
most manufacturing environments.  BlueSeer also provides a fully functional EDI mapping tool for EDI translations and file traffic monitoring. 
BlueSeer is released for free use under the MIT License.   The application and source code
are available for download at github.com. BlueSeer was originally launched in 2017 and continues to evolve to meet user demands.
The latest 'stable' release of version 8.0 was released on 2026-06-15.</br>

<h1>Functionality</h1>

BlueSeer provides modules for the following generic set of business concepts : 
* Double Entry General Ledger
* Cost Accounting
* Accounts Receivable Processing and Aging
* Accounts Payable Processing and Aging
* PayRoll
* APIs for system to system integration
* Inventory Control
* Job Tracking
* Job / Operation Scanning
* Lot Traceability
* Purchasing
* Order Management
* Recurrable Service Billing
* Service Order and Quoting Management
* Freight Management
* Electronic Data Interchange (EDI)
* EDI Mapping tool (supports: X12, EDIFACT, CSV, FlatFile [IDOC, etc], XML, JSON )
* EDI Communications (FTP, AS2 server/client)
* Automated Task/Cron Scheduler
* UCC Label Generation
* Materials Resource Planning (MRP)
* Human Resources (HR)

<h1>Technology</h1>
BlueSeer ERP is written entirely in Java.  The application is a non-web based
desktop application that relies heavily on the Java Swing widget
toolkit/library.  There are currently two database engines available for
BlueSeer. 
For single client deployment, The relational database SQLite is used for
it's deployment ease and server-less design.  For multi-client
deployment scenarios, the open-source relational database MySQL is used as the
back-end database server.  The MySQL backend can be hosted on a local network or in
the Cloud for a remote DB deployment configuration. 
</br>
BlueSeer is a menu-driven application.  It's composition is a collection of Java Swing
JPanel widgets.  Each business function, i.e. Order Entry, Item Master
Maintenance, etc is a stand-alone JPanel widget.  Each JPanel widget is loaded
at runtime using Reflection to 'inject' the JPanel
into the JFrame on user
demand.  JPanel class names are stored in the database and associated
with  menu options which are further associated with user permissions.  This
archtitecture increases the capability of customization and extension by
engaging BlueSeer as
a Desktop Application Framework.  Applications independent of the core
software can be quickly deployed 
given the menu/class management and
permissions functionality that's built into the BlueSeer framework.
</br>

<h1>Look and Feel / Runtime</h1>

BlueSeer's Swing UI is themed with <a href="https://www.formdev.com/flatlaf/">FlatLaf</a>,
giving the existing JPanel/JFrame widgets a modern, flat appearance (better fonts,
spacing, and HiDPI scaling) with no changes to the underlying panels themselves.
FlatLaf also draws its own window decorations and merges the menu bar into the title
bar (like a modern browser or VS Code). All of this is installed once, in
`com.blueseer.utl.mf` (the application's entry point), before the main frame is
constructed. One caveat: the main window's title bar text doubles as a status line
(`USER=... IP=... VER=...`), which can get truncated on narrower windows now that it
shares the title bar row with the embedded menu — widen/maximize the window if it
looks cut off.
</br>
The application is also intended to be bundled and run with the
<a href="https://github.com/JetBrains/JetBrainsRuntime">JetBrains Runtime (JBR)</a>
instead of a stock OpenJDK build. JBR is a drop-in, source-compatible OpenJDK build
that includes a number of Swing-specific rendering improvements (better subpixel/HiDPI
handling, native window decorations, improved font rendering) that make Swing apps
like BlueSeer feel noticeably more native on modern Windows, macOS, and Linux desktops.
The recommended way to pick this up is `mvn package -Pjpackage -Djbr.home=/path/to/jbr`
(see "Building a native installer" below), which bundles JBR straight into the
installer. If you're using the older manual packaging scripts in `scripts/` instead
(`login.bat`, `login.sh`, `debprep.sh`, the `installJRE*.iss` installers, etc.), download
a JBR release from the <a href="https://github.com/JetBrains/JetBrainsRuntime/releases">
JetBrains Runtime releases page</a> and use it in place of the `jre26` directory those
scripts expect — no other changes are required since JBR ships the same `bin/java` /
`bin/javaw` layout as a standard JDK/JRE.
</br>

The old toolbar icons under `src/images` were fixed-size 16x16 PNGs, which look
soft/blurry on HiDPI displays. They're being replaced, one icon at a time, with
<a href="https://github.com/kordamp/ikonli">Ikonli</a> (`ikonli-swing` +
`ikonli-materialdesign2-pack`) — a vector icon-font library for Swing that renders
crisply at any size/DPI. The shared icons used across most Browse/Maint panels
(magnifying-glass lookup/find, add, delete, save, change, print — ~200 call sites) are
already converted; the pattern for the rest is mechanical:
```java
// before
button.setIcon(new ImageIcon(getClass().getResource("/images/whatever.png")));
// after
button.setIcon(FontIcon.of(MaterialDesignW.WHATEVER, 16));
```
(add the matching `import org.kordamp.ikonli.swing.FontIcon;` and
`import org.kordamp.ikonli.materialdesign2.MaterialDesignW;`, pick the closest icon at
<a href="https://kordamp.org/ikonli/cheat-sheet-materialdesign2.html">the
materialdesign2 cheat sheet</a>) — then the old PNG can be deleted once nothing
references it.
</br>

FlatLaf's <a href="https://www.formdev.com/flatlaf/typography/">typography</a> and
<a href="https://www.formdev.com/flatlaf/components/textfield/">text field</a> client
properties replace a fair amount of hand-written styling code. Section/title labels
that used to hardcode a font (`label.setFont(new Font("Tahoma", Font.BOLD, 18))`)
should instead use a semantic style class, which scales correctly with the user's OS
font/DPI settings instead of a fixed pixel size:
```java
label.putClientProperty("FlatLaf.styleClass", "h3"); // h1 (largest) .. h4 (smallest)
```
(drop the old `setFont` call — the style class fully owns the font once applied). Every
existing large/bold title label in the codebase has already been converted this way.
Likewise, search/filter `JTextField`s get a placeholder and a clear ("x") button instead
of a separate "Search:" label:
```java
searchField.putClientProperty("JTextField.placeholderText", "Search...");
searchField.putClientProperty("JTextField.showClearButton", true);
```
The shared search field in `com.blueseer.utl.Browse` (used by ~30 different
Browse-type screens via reflection) and the other dedicated search fields are already
converted; apply the same two lines to any other `JTextField` that's used for
filtering.
</br>

<h1>Layout</h1>

Every panel in this codebase already uses `javax.swing.GroupLayout` (NetBeans' modern
default layout manager) — there's no `AbsoluteLayout`/fixed-x-y-coordinate code to
migrate away from; the one-time `lib/AbsoluteLayout.jar` that used to sit in this repo
turned out to be unused by both this source tree and the compiled `bsmf.jar`; it was
removed in the Maven cleanup. GroupLayout is a real layout manager (not fixed
coordinates), but it's extremely verbose — a simple toolbar row is 30-40 lines of
`addGroup`/`addComponent` calls — and doesn't reflow as gracefully as modern
alternatives when a window is resized well beyond its designed size.
</br>
<a href="https://www.miglayout.com/">MigLayout</a> (`miglayout-swing`, already added
to `pom.xml`) is a much more concise, actively-maintained replacement that handles
resizing and HiDPI scaling better. Rather than a one-shot rewrite of ~240 panels, adopt
a Boy Scout Rule: whenever you're already touching a panel's `initComponents()` for a
bug fix or new feature, migrate that panel's layout to MigLayout while you're in there.
`com.blueseer.eng.OVDevBrowse` has been converted as a worked example — a GroupLayout
block like:
```java
javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
jPanel1.setLayout(jPanel1Layout);
jPanel1Layout.setHorizontalGroup( /* ~15 lines of addGroup/addComponent/addGap */ );
jPanel1Layout.setVerticalGroup( /* ~15 more */ );
```
becomes:
```java
jPanel1.setLayout(new MigLayout("insets 5 23 5 5", "[]18[]3[]18[]5[]5[]", "[]"));
jPanel1.add(jLabel1);
jPanel1.add(jLabel2);
jPanel1.add(tbtext, "width 80!");
jPanel1.add(rbactive);
jPanel1.add(rbinactive);
jPanel1.add(btview, "wrap");
```
See the <a href="https://www.miglayout.com/QuickStart.pdf">MigLayout quick start
guide</a> for the constraint-string syntax.
</br>

<h1>Build/Compile Instructions (all builds should utilize JDK version 26 or higher)</h1>
</br>

BlueSeer builds with Maven only — there is no Ant build anymore. You'll need the JDK
(version 26 or higher) and Maven installed and on your PATH.

1. Download the source: `git clone https://github.com/BlueSeerERP/blueseer.git`
2. From the `blueseer` directory, run: `mvn package`
   This compiles the source, resolves every third-party dependency from Maven Central,
   and assembles a complete, runnable application under `target/`:
   `target/dist` (the application jar plus every dependency jar), and
   `target/{data,edi,jasper,zebra,images,conf,logs,attachments,temp}` plus
   `target/bs.cfg` (a default SQLite configuration) — everything the app needs to run,
   equivalent to what `test/refresh_test_*.sh` used to assemble by hand for the old
   Ant build.
3. Run it:
   - (linux) `cd target && java -cp "dist/*" com.blueseer.utl.mf`
   - (windows) `cd target && java -classpath "dist/*" com.blueseer.utl.mf`
   - The default login credentials are 'admin' and 'admin'.

Only one dependency isn't on Maven Central: `lib/bsmf.jar`, the compiled main
application frame (see the "Technology" section above) — it's declared as a
`system`-scoped dependency in `pom.xml` pointing at `lib/bsmf.jar` directly.

<h2>Using Apache Netbeans (or any Maven-aware IDE)</h2>

Since the project is plain Maven, open the `blueseer` folder directly in any
Maven-aware IDE (NetBeans, IntelliJ IDEA, Eclipse/m2e, VS Code + Java extensions) —
it will be recognized automatically via `pom.xml`. Set the run/working directory to
`target` (after running `mvn package` once) so the app can find `bs.cfg`, `data/`,
etc., then run `com.blueseer.utl.mf` as the main class.
</br>

<h2>Building a native installer (jpackage)</h2>

`mvn package -Pjpackage` produces a self-contained native installer under
`target/installer` — a `.deb` on Linux, an `.msi` on Windows, a `.dmg` on macOS
(jpackage only builds an installer for the OS you run it on) — with the app, all its
dependency jars, its runtime resources (`bs.cfg`, `data/`, `jasper/`, etc.), and a
bundled Java runtime, so end users just install and run it like any other desktop
application; no separately-installed JDK required.

By default the build points jlink at whatever JDK is running Maven. To bundle
<a href="https://github.com/JetBrains/JetBrainsRuntime">JetBrains Runtime</a> instead
(recommended — see "Look and Feel / Runtime" above), download a JBR release, extract
it, and point at it:
```
mvn package -Pjpackage -Djbr.home=/path/to/extracted/jbr
```
The installer type/icon are picked automatically based on the OS running the build
(see the `windows` / `linux-x86_64` / `mac` profiles in `pom.xml`); override
`-Dinstaller.type=...` to build a different package type (e.g. `APP_IMAGE` for a
plain, unpackaged app folder, useful for testing before building a real installer).
</br>


<h1>App size</h1>

A few things keep the installer from bloating the way older Swing apps bundling a
`lib/` folder and a full JDK tend to:

<b>A custom jlink runtime instead of the whole JDK.</b> The `jpackage` profile doesn't
bundle a full JDK/JBR (300MB+) — it points jpackage's built-in jlink step at just the
modules this app actually uses (`<modulePaths>`/`<addModules>` in the `jpackage`
profile in `pom.xml`), which on this JDK shrinks the bundled runtime from ~286MB down
to ~90MB. The module list was computed by running jdeps against every jar in
`target/dist`:
```
cd target && for j in dist/*.jar; do
  jdeps --multi-release 21 --ignore-missing-deps --print-module-deps -cp "dist/*" "$j"
done | grep -v '^Warning:' | tr ',' '\n' | sort -u
```
(run each jar individually rather than all together — some jars declare a real
`module-info` and jdeps tries to fully resolve the module graph if you pass several of
those at once, which can fail on version conflicts that don't actually matter for a
classpath app). Two modules were added on top of what jdeps found — `jdk.crypto.ec`
(TLS with EC cipher suites; needed for HTTPS/AS2/SFTP/MySQL but invisible to jdeps'
static analysis) and `jdk.charsets` (non-Latin charsets, given this app's Arabic/
Chinese/etc. language support) — both are classic jlink gotchas that fail silently at
runtime, not at build time. If you add a dependency that needs something else, the
symptom is a `NoClassDefFoundError`/`ClassNotFoundException` for a JDK class at
runtime; regenerate the module list and add whatever's missing.
</br>
Note the jpackage profile explicitly keeps `stripNativeCommands` off (unlike the
plugin's stripped-by-default setting): `com.blueseer.utl.mf` shells out to
`$JAVA_HOME/bin/java` to relaunch itself in the app's own directory
(`relaunchInAppDirectoryIfNeeded`), which needs that binary to still be present in the
bundled runtime.
</br>

<b>JXBrowser and IcePDF were dead weight.</b> `lib/jxbrowser-3.0.jar` (a full bundled
Chromium browser engine) turned out to be entirely unused — not referenced anywhere in
this source tree or in the compiled `bsmf.jar` — and has been removed, along with the
dead commented-out Maven dependency for it. `icepdf-core`/`icepdf-viewer` (used in
exactly one place, `ItemMaint`'s drawing preview) have also been removed:
`OVData.showPDFusingIcePDF`'s embedded PDF viewer window is replaced with
`OVData.openPDF`, which just hands the file to the OS's default PDF viewer via
`java.awt.Desktop`. This is a real, if narrow, behavior change — drawings now open in
an external viewer instead of an in-app window — but it drops IcePDF's large
transitive Apache Batik/xmlgraphics-commons dependency tree for a feature that had a
single call site. (JasperReports also depends on Batik for its own SVG chart support,
so those jars are still present either way — just no longer duplicated by IcePDF's
older, separately-versioned copies.)
</br>
Not done: shrinking tools like ProGuard/R8 weren't applied. This codebase loads
~300 business panels by reflection using class names stored in the database
(see "Technology" above), and libraries like JasperReports and the JDBC drivers
register themselves via reflection/`ServiceLoader`, all of which a bytecode shrinker
can't see from a static call graph. Getting real value out of a shrinker here would
need extensive, carefully-verified keep rules, and a wrong one fails silently at
runtime in a hard-to-fully-test way across an app this size — a worse outcome than a
larger installer.
</br>


<h1>Responsiveness</h1>

Most of the codebase already wraps its database calls in `SwingWorker` (~200 files) —
that pattern is the house style, not something to introduce from scratch. But there
are still panels that run a query directly on the EDT, freezing the UI until it
returns. `com.blueseer.eng.OVDevBrowse` was one of them (`btviewActionPerformed` ran
`DriverManager.getConnection` + a query + a result-set loop straight from the button's
`ActionListener`) and has been converted as a worked example: the query now runs in
`SwingWorker.doInBackground()`, the button disables and an indeterminate
`JProgressBar` (added next to it via MigLayout) shows while it runs, and the table
model is applied back on the EDT in `done()`. While converting it, the hand-built SQL
string (`"...like '%" + tbtext.getText() + "%'"`) was also switched to a
`PreparedStatement` — worth checking for wherever else user input is concatenated
directly into SQL.
</br>
Grepping for files that run `executeQuery`/read a `ResultSet` but don't reference
`SwingWorker` turns up the rest of the list (excluding the `com.blueseer.srv.*`
web-service endpoints, which don't run on the EDT so aren't a UI-freeze risk): the
`*Rpt.java`/`*RptPicker` report generators (`ReworkRpt`, `ScrapRpt`, `ReqRpt`,
`OrderDetRpt`, `OrderSchedRpt`, `TrainingRpt`, `RetailReorderRpt`, `ClockDetRpt`,
`IncomeStatementRptYear`...) are the highest-value next targets since reports tend to
run the heaviest queries; then the `*Browse.java` list screens (`POSBrowse`,
`DOBrowse`, `ComponentDemandBrowse`, `ForecastBrowse`, `ProjectionBrowse`...); then the
`*Maint.java`/`*Control.java` forms. Same recipe as `OVDevBrowse` for each: move the
query into `doInBackground()`, apply the result to the UI in `done()`, and show some
kind of busy indicator in between.
</br>


<h1>Contributing</h1>

Here's a simple guide to contribute to the BlueSeer project:
    
1. Fork the project
2. `git clone` your new fork to local client
3. Create a unique branch (`git checkout -b mybranch/mycode_change`)
4. Make your changes
5. Commit your changes (`git commit -m 'mycode with enhancement/fix/feature.'`)
6. Push to the branch (`git push origin mybranch/mycode_change`)
7. Open a pull request
</br>

<h1>License</h1>

MIT License (see [license.txt](LICENSE))


<h1>Logo and Trademark Policy</h1>

Please read our [Logo and Trademark Policy](TRADEMARK_POLICY.md).
