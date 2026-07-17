# Running BlueSeer in the browser (CheerpJ) — for demo-video capture

**Superseded.** This path turned out to be a dead end (CheerpJ's Java-17
bytecode ceiling and JNI limitations, then Webswing's licensing requirements
- see the git history/PR discussion for the full trail) and is kept here only
for the record. What actually worked instead: running the real desktop app
headless (Xvfb + fluxbox for a proper maximized window) and driving it
directly via AssertJ-Swing, with ffmpeg recording the display. See
[`tools/ui-regression/README.md`](../tools/ui-regression/README.md#demo-video-capture)
for the working demo-video pipeline.

This is a first pass at getting BlueSeer's existing Swing UI to boot inside a
browser tab via [CheerpJ](https://cheerpj.com/docs/overview), so that browser
automation tools that record web pages (e.g.
[testreel](https://github.com/greentfrapp/testreel)) can be pointed at it to
generate product demo videos without a human recording them or a native
desktop capture pipeline.

**Status: unverified end-to-end.** The build compiles cleanly and the launcher
page below is written to CheerpJ 3's documented API, but this was developed in
a sandboxed environment whose network policy blocks
`cjrtnc.leaningtech.com` (CheerpJ's runtime CDN) at the proxy layer — every
request to it returns `403` before it even reaches CheerpJ. That means the
actual boot has **not** been visually confirmed here. Run `browser/serve.sh`
from a machine (or a Claude Code environment) with unrestricted internet
access and open the printed URL to find out how far it actually gets — see
"How to test it" below.

## What this adds

- A `browser` Maven profile in `pom.xml`. `mvn package -Pbrowser -DskipTests`
  produces the normal `target/dist` layout, but compiled at
  `--release 17` instead of the project's usual 25 — CheerpJ's JVM only
  understands Java 8/11/17 class files, so bytecode compiled for anything
  newer won't load. It also excludes `utilities/bsCommVT.java` from
  compilation, see "Compatibility findings" below for why.
- `browser/index.html` — a CheerpJ loader page: `cheerpjInit()`, creates a
  display canvas, then `cheerpjRunMain("com.blueseer.utl.mf", "/app/dist/*")`
  (CheerpJ mounts the directory the page is served from at `/app` by
  default).
- `browser/serve.sh` — builds with the `browser` profile, drops the loader
  page into `target/`, and serves `target/` over plain HTTP so `/app/dist/*`
  resolves.

## How to test it

```
./browser/serve.sh          # builds + serves on :8934
```

Then open `http://localhost:8934/index.html` in a real browser (not this
sandbox) and watch the console. First boot will be slow — CheerpJ is
downloading and JIT-compiling a JVM plus this app's ~100MB of jars.

If the page loads but nothing renders, check the browser console first —
`cheerpjRunMain`'s exact signature and the default mount point are recalled
from documentation, not verified live, so if either is wrong the console
error should say so directly (e.g. a 404 for a jar, or a "class not found").

## Compatibility findings (static analysis, not yet confirmed live)

**Looks fine:**
- The GUI is genuinely Swing/AWT (`com.blueseer.utl.mf` → `bsmf.MainFrame`),
  which CheerpJ explicitly supports. No source file imports SWT — it's
  declared as a Maven dependency (for `jfreechart-swt`, unused elsewhere) but
  never referenced from Java code, so it costs nothing to leave out of a
  browser build.
- `MainFrame`'s constructor just parses `bs.cfg` and builds the UI; the DB
  connection only happens later, inside `executeLoginTask()` /
  `btloginActionPerformed()`. So the splash screen, branding, and login form
  should render even if nothing downstream of "click Login" works yet — that
  alone may be enough for an intro/branding shot in a demo video.

**Confirmed blockers:**
- `utilities/bsCommVT.java` uses Java 21 virtual threads
  (`Executors.newVirtualThreadPerTaskExecutor()`, `Thread.threadId()`), which
  don't exist in class files below Java 21 — incompatible with CheerpJ's
  Java-17 ceiling regardless of compiler flags. It's a standalone CLI
  file-traffic service with its own `main()`, not reachable from the Swing menu
  system, so the `browser` profile just excludes it from compilation. Nothing
  is lost for a GUI demo.
- The default `bs.cfg` uses `DBTYPE=sqlite`, and `sqlite-jdbc` is a JNI
  wrapper around a native library. CheerpJ doesn't support JNI, so logging in
  against SQLite will not work in the browser build as configured.
- `oshi-core` (system hardware info, via JNA/native code) is imported by
  `com/blueseer/adm/License.java` and `com/blueseer/adm/About.java` only —
  triggered by opening the License or About dialogs, not by normal
  navigation. Likely to throw once CheerpJ tries to load the native JNA stub,
  but it's avoidable in a scripted demo by just not opening those two
  screens.

**Open question, needs live testing to answer:** whether *any* JDBC backend
can reach a real database from inside CheerpJ at all. Browsers have no raw
TCP socket API — CheerpJ's networking support is proxied through
fetch/XHR/WebSocket, which is HTTP(S)-oriented. Switching `bs.cfg` to
`DBTYPE=mysql` (mysql-connector-j is pure Java, unlike sqlite-jdbc) sidesteps
the JNI problem specifically, but it's unconfirmed whether CheerpJ can tunnel
the raw MySQL wire protocol at all. If it can't, no JDBC backend will work
un-modified, and getting past the login screen would require either a
CheerpJ-side TCP relay (check current CheerpJ docs/support for this) or
swapping the app's data layer for something HTTP-based for the browser build
specifically. **This is the main thing to verify before scripting testreel
demos that need real data on screen** — worth doing before investing further
here, since it determines whether the achievable demo is "click through live
screens with data" or "walk through UI shell only."

## Suggested next steps

1. Run `browser/serve.sh` somewhere with real internet access and see what
   actually happens — confirm the loader syntax is right and see how far
   boot gets before erroring.
2. If it boots to the login screen: try `DBTYPE=mysql` against a real MySQL
   server and see whether the connection attempt even gets a chance to fail
   with a socket-related error vs. succeeding — that answers the open
   question above.
3. Once at least one screen renders reliably, wire up
   [testreel](https://github.com/greentfrapp/testreel) against
   `browser/index.html` for the actual video capture — that part is
   unaffected by anything here, testreel just drives a URL like any other
   web page.
