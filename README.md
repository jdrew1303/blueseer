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

By default the installer bundles whatever JDK is running Maven. To bundle
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
