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
* EU/Irish FIC-compliant ingredient labeling (allergens, QUID, additive warnings)
* Materials Resource Planning (MRP)
* Human Resources (HR)

<h1>Technology</h1>

BlueSeer ERP is written entirely in Java and is a non-web desktop application built on
Java Swing. It supports two database backends: SQLite for single-client, server-less
deployments, and MySQL for multi-client deployments (on a local network or hosted in the
cloud). The UI is themed with <a href="https://www.formdev.com/flatlaf/">FlatLaf</a> for
a modern, flat appearance, and is optionally run on the
<a href="https://github.com/JetBrains/JetBrainsRuntime">JetBrains Runtime (JBR)</a> for
better Swing rendering (see "Building a native installer" below).
</br>
BlueSeer is menu-driven: each business function (Order Entry, Item Master Maintenance,
etc.) is a stand-alone Swing `JPanel` loaded at runtime via reflection and 'injected'
into the main frame on demand. `JPanel` class names are stored in the database and
associated with menu options, which are in turn associated with user permissions. This
architecture makes BlueSeer usable as a Desktop Application Framework — applications
independent of the core software can be quickly deployed given the menu/class
management and permissions functionality already built in.
</br>

<h1>Building from Source</h1>

BlueSeer builds with Maven only — there is no Ant build anymore. You'll need the JDK
(version 26 or higher) and Maven installed and on your PATH.

1. Download the source: `git clone https://github.com/BlueSeerERP/blueseer.git`
2. From the `blueseer` directory, run: `mvn package`
   This compiles the source, resolves every third-party dependency from Maven Central,
   and assembles a complete, runnable application under `target/`:
   `target/dist` (the application jar plus every dependency jar), and
   `target/{data,edi,jasper,zebra,images,conf,logs,attachments,temp}` plus
   `target/bs.cfg` (a default SQLite configuration) — everything the app needs to run.
3. Run it:
   - (linux) `cd target && java -cp "dist/*" com.blueseer.utl.mf`
   - (windows) `cd target && java -classpath "dist/*" com.blueseer.utl.mf`
   - The default login credentials are 'admin' and 'admin'.

<h2>Useful Maven targets</h2>

* `mvn compile` — compile the source only (fastest feedback loop while developing).
* `mvn package` — full build, produces the runnable `target/` layout described above.
* `mvn package -DskipTests` — skip the test suite for a faster package build.
* `mvn test` — run the test suite on its own.
* `mvn package -Pjpackage` — build a native installer (see below).

<h2>Using Apache Netbeans (or any Maven-aware IDE)</h2>

Since the project is plain Maven, open the `blueseer` folder directly in any
Maven-aware IDE (NetBeans, IntelliJ IDEA, Eclipse/m2e, VS Code + Java extensions) —
it will be recognized automatically via `pom.xml`. Set the run/working directory to
`target` (after running `mvn package` once) so the app can find `bs.cfg`, `data/`,
etc., then run `com.blueseer.utl.mf` as the main class.
</br>

<h2>Building a native installer (jpackage)</h2>

`mvn package -Pjpackage` produces a self-contained native installer under
`target/installer` — a `.deb` on Linux, an `.msi` on Windows, an unpacked `.app` on
macOS (jpackage only builds an installer for the OS you run it on) — with the app, all
its dependency jars, its runtime resources (`bs.cfg`, `data/`, `jasper/`, etc.), and a
bundled Java runtime (built with jlink, so it's a fraction of a full JDK install), so
end users just install and run it like any other desktop application; no
separately-installed JDK required.

macOS builds via `APP_IMAGE` under the hood rather than jpackage's own `.dmg` type:
jpackage always ad-hoc-codesigns the app bundle it builds on macOS, and that codesign
step reproducibly fails when bundling `bs.cfg`/`.patch` as loose top-level files (a
real jpackage limitation, not a project-specific misconfiguration). See the
`mac-aarch64`/`mac-x86_64` profiles in `pom.xml` for the full workaround: a post-build
step copies those two files into the built `target/installer/BlueSeer.app` afterward,
re-signs it with `codesign`, then wraps it into a real, ready-to-ship
`target/installer/BlueSeer.dmg` itself via `hdiutil` - so `mvn package -Pjpackage`
still produces one installer artifact directly, same as windows/linux.

By default the build points jlink at whatever JDK is running Maven. To bundle
<a href="https://github.com/JetBrains/JetBrainsRuntime">JetBrains Runtime</a> instead
(recommended for better Swing rendering — see "Technology" above), download a JBR
release, extract it, and point at it:
```
mvn package -Pjpackage -Djbr.home=/path/to/extracted/jbr
```
The installer type/icon are picked automatically based on the OS running the build
(see the `windows` / `linux-x86_64` / `mac-aarch64` / `mac-x86_64` profiles in
`pom.xml`); override `-Dinstaller.type=...` to build a different package type (e.g.
`APP_IMAGE` for a plain, unpackaged app folder, useful for testing before building a
real installer).
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
