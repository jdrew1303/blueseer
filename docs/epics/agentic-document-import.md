# Epic: Agentic Document Import

**Status:** Proposed — not yet started
**Owner:** TBD
**Depends on:** nothing shipped-but-unmerged; independent of the FIC/nutrition labeling work

## 1. Problem statement

Getting paper into BlueSeer is manual today. A supplier invoice, packing
slip, or ingredient spec sheet arrives on paper (or as a PDF/photo from a
supplier's email), and someone re-types every line — item, quantity, unit
cost, lot number, allergen/E-number data — into Receiver Maintenance, PO
Maintenance, or the Ingredient Data tab. For a small operation like a
bakery this is a real, recurring time cost and a source of transcription
errors (wrong quantity, mis-typed lot number, a missed allergen on a new
ingredient's spec sheet).

The idea: let a user photograph or drop in a document, have a locally-run
LLM read it and propose structured field values, show the user a
side-by-side review screen to confirm/correct before anything touches the
database, and commit only what's approved. Nothing is auto-committed
without a human looking at it — this is an assistive data-entry tool, not
an autonomous agent making inventory decisions.

## 2. Why Koog + a local LLM (not a cloud API)

- **Data never leaves the building.** Supplier pricing, recipes, and
  ingredient specs are commercially sensitive; a bakery-scale customer is
  not going to want that going to a third-party cloud API, and BlueSeer
  itself is normally self-hosted (SQLite/MySQL on-prem). A local model
  keeps the whole stack on the customer's own hardware, consistent with
  how BlueSeer is deployed today.
- **No new operating cost.** No per-token API bill, no API key management,
  no "the vendor changed their pricing" risk for a feature aimed at small
  manufacturers.
- **Koog is JVM-native with a documented Java-friendly API** (not just a
  Kotlin DSL), so it drops into BlueSeer's existing Java/Maven/Swing stack
  without a language rewrite. It ships `executeStructured` for typed
  output (with a `fixingParser` auto-retry when the model returns
  malformed JSON), explicit Vision/Image model capabilities for reading
  scanned documents, and SwingWorker-compatible synchronous or async
  execution.
- **LM Studio is the confirmed runtime for v1** (the user's preferred
  tool — "nice UI and very user friendly") and, like Ollama, serves an
  OpenAI-compatible REST API. Koog's `OpenAILLMClient` takes an
  `OpenAIClientSettings(baseUrl, chatCompletionsPath, ...)` — this is
  Koog's documented mechanism for pointing the OpenAI client at
  non-OpenAI endpoints (shown for Azure OpenAI and other OpenAI-compatible
  providers), and LM Studio's own default endpoint
  (`http://localhost:1234/v1/chat/completions`) fits it directly:
  `OpenAIClientSettings(baseUrl = "http://localhost:1234",
  chatCompletionsPath = "v1/chat/completions")`. (Note for whoever
  implements this: Koog has no first-class `lmStudio { }` provider block
  the way it does for Ollama, so this is wired as a plain OpenAI client
  pointed at a local URL rather than a named provider — a config detail,
  not an open question.)
  - Ollama is offered as a second runtime option from day one, since it
    has first-class Koog support and some installs may already run it —
    but LM Studio is the primary, expected path.

## 3. What "agentic" means here (and what it deliberately doesn't)

This is a **single-shot extraction assistant**, not a multi-step
autonomous agent making changes on its own:

capture → preprocess → one structured-extraction LLM call → human review
→ explicit commit.

No tool-calling loop, no autonomous multi-turn planning, no agent deciding
*what* to do — just Koog's structured-output extraction, once per
document. This is deliberately the smallest useful slice: it gets the
"paper in, form pre-filled" win without the failure modes (runaway loops,
unpredictable tool use) of a fuller agent. Multi-step agentic workflows
(e.g., "reconcile this invoice against the open PO and flag discrepancies
automatically") are a plausible **future phase**, explicitly out of scope
for this epic — see §8.

## 4. Industry patterns considered, and which apply to BlueSeer

Research into how others do agentic document extraction (Koog-specific
examples are scarce; drew from the broader IDP/agentic-extraction space —
the MADP research pipeline, LandingAI's Agentic Document Extraction
product, LlamaIndex's structured extraction writeups):

| Pattern | Applies to BlueSeer? |
|---|---|
| Per-document-type JSON schema driving structured extraction | **Yes** — maps directly onto Koog's `executeStructured`; we need one schema per document type we support (invoice/packing-slip line items first). |
| Human-in-the-loop review gate before commit to system of record | **Yes, mandatory** — non-negotiable given lot/allergen data quality stakes (see §6). The MADP paper's headline result (98.5% document-level accuracy) came specifically *from* the HITL step, not from the model alone. |
| Field-level confidence scoring with threshold-based routing (auto-accept / review / flag) | **Yes, in a limited form** — Koog can be asked to return a confidence-per-field alongside the structured payload; use it to visually flag low-confidence fields on the review screen rather than to silently auto-accept anything. Full auto-accept is explicitly out of scope for v1 (see §6). |
| Image preprocessing (deskew, crop, contrast) before the vision call | **Yes** — phone-photographed paper is skewed/tilted far more often than scanner output; do basic preprocessing before the image ever reaches the model. |
| Multi-stage pipeline decomposition (classify → split pages → parse → extract line items → validate) | **Partially** — BlueSeer's v1 documents are single-page, single-vendor-format-agnostic (we're not parsing a fixed template, we're relying on the vision model's general document understanding), so full page-splitting/classification is overkill for v1. Revisit if multi-page supplier statements become a real use case. |
| Visual grounding (bounding boxes tying an extracted field back to its source-image region) | **Vendor-specific pattern (LandingAI), not a generic standard yet** — a good UX idea worth stealing conceptually (show the source image next to the review form so the user can eyeball it), but building actual bounding-box grounding is a bigger lift than v1 needs. Note as a future UX enhancement, not a v1 requirement. |

## 5. Architecture

### 5.1 New package: `com.blueseer.doc`

Follows BlueSeer's existing package-per-feature-area convention (`ing`,
`lbl`, `eng`, etc.):

- **`docData.java`** — data access for import history/audit trail
  (following `ingData.java`'s `PreparedStatement`-everywhere convention).
- **`DocumentExtractionService.java`** — wraps Koog: builds the
  `OpenAILLMClient` with the configured runtime's `baseUrl`, defines the
  `LLModel` (with `LLMCapability.Vision.Image`), and exposes
  `extract(DocumentType type, byte[] imageBytes) -> ExtractionResult`
  using `executeStructured` against a per-`DocumentType` schema. Pure
  service class, no UI/DB coupling beyond reading LLM-runtime config.
- **`schema/` sub-package** — one Java record per supported document type
  (`InvoiceExtraction`, `PackingSlipExtraction`, `IngredientSpecExtraction`,
  each with nested line-item records), the structured-output targets for
  `executeStructured`.
- **`ImagePreprocessor.java`** — deskew/crop/contrast normalization before
  the image is handed to the model (candidate: a small, permissively-
  licensed pure-Java library rather than pulling in OpenCV's native
  bindings, to avoid another native-library packaging problem like the
  JBR/jpackage issues already fought this project — needs a short spike to
  pick one, see DOC-3).
- **`DocumentImportPanel`** — the new top-level screen (see §7).
- **`ExtractionReviewPanel`** — the side-by-side review/confirm screen
  (see §7).

### 5.2 Dependency

Add Koog's Maven artifact to `pom.xml` (`ai.koog:koog-agents`, exact
version TBD at implementation time) alongside the existing dependency
block. Koog ships compiled JVM bytecode with a documented Java API, so —
unlike a Kotlin *source* dependency — this does **not** require adding a
Kotlin compiler/toolchain to BlueSeer's Maven build; it's added the same
way `commons-collections4` or any other third-party jar already is.
**Confirm this assumption in DOC-1** (Koog's actual published artifacts
may still transitively pull in `kotlin-stdlib`, which is fine as a runtime
jar but should be checked against BlueSeer's `maven.compiler.release=25`
setting and jpackage/jlink bundling for size/module-path implications).

### 5.3 Runtime configuration (admin-configurable, not hardcoded)

New `ov_ctrl` columns (via a new `.patchsqlv_docimport` patch file,
following the existing idempotent `information_schema.columns`-checked
pattern): `ov_llm_provider` (`LMSTUDIO` / `OLLAMA`), `ov_llm_baseurl`,
`ov_llm_model`, `ov_llm_enabled`. Surfaced as a small new section in
System Control (the same screen that already holds `ov_image_directory`
and the background-color RGB fields), so an admin can point BlueSeer at
whichever local LLM runtime and model the customer has running — no code
change needed to swap models or switch from LM Studio to Ollama.

### 5.4 Keeping the LLM call off the EDT

Every extraction call is a network request to a local server that may
take several seconds for a vision model — this **must** go through a
`SwingWorker`, following the existing pattern already used elsewhere
(`ECNMaint`, `TaskMaint`, `LabelAddrMaint`), with the review panel
disabled and a progress indicator shown for the duration of the call.

## 6. Data quality principle (non-negotiable for v1)

Nothing extracted is written to `recv_det`, `item_mstr`, `ing_mstr`, or any
other real table until the user hits the same **Add**/**Save** button they
already use for manual entry today — the extracted data just arrives
pre-filled in that same familiar form instead of blank. A wrong read is
exactly as easy to fix as a typo would be, no harder — this matters more
here than in most ERP features because bad data entering `ing_mstr`/
allergen fields is a food-safety issue, not just an inventory nuisance.
That said, given the users are non-technical shop staff, not QA
reviewers, the review step should stay as close as possible to "glance at
it, fix anything obviously wrong, save" — see §7 for how the design leans
into that rather than adding extra confirmation ceremony.

## 7. UX and UI integration

**Design principle:** the users are non-technical bakery-shop staff, not
IT people, and BlueSeer is installed per-customer rather than run as a
hosted product — there's no analytics pipeline or support feedback loop
to lean on, so the interface has to be self-evidently simple the first
time, not tuned over many releases. Every design choice below is aimed at
cutting clicks and jargon for the person actually doing the data entry:

- **No separate "accept" step per field or per line.** The extracted data
  lands directly in the same editable grid/form fields staff already use
  for manual entry. There's nothing new to learn — it behaves exactly
  like someone already filled the form in for you, and you just check it
  over before hitting the **Add** button you already know.
- **Remember item mappings so the same correction is never made twice.**
  The first time a supplier's line text (e.g. "STRONG WHITE FLR 25KG")
  is matched to a BlueSeer item, that mapping is remembered per supplier
  (a new `doc_item_alias` table: supplier + raw line text → item code).
  The next invoice from the same supplier with the same wording
  auto-fills the item instead of asking again — most of a small bakery's
  receiving is the same handful of suppliers and items on repeat, so this
  is where the real time savings compound. See DOC-12/DOC-21.
- **No technical settings in the day-to-day screens.** Model names,
  base URLs, and provider choice live only in System Control, set up once
  (by whoever installs/supports BlueSeer for that customer) and never
  touched again by shop staff. The Import buttons on Receiver Maintenance
  and the Ingredient Data tab show no provider/model/confidence-score
  language at all.
- **Plain visual cues, not jargon.** A field the model wasn't sure about
  gets a simple highlight so it catches the eye — no "confidence: 62%"
  text, no technical explanation. If something's not clearly readable
  from the photo, the field is just left blank/highlighted rather than
  guessed at, which is itself a simpler rule than exposing a score.
- **One capture step, not a wizard.** A single button — take or choose a
  photo — leads straight into the pre-filled form. No multi-page wizard,
  no separate "review" screen to navigate to; the form the extraction
  populates *is* the same Receiver Maintenance / Ingredient Data screen
  staff already use.

### 7.1 End-user flow (the bakery-shop scenario)

1. A supplier's paper invoice arrives with today's flour delivery.
2. From **Receiving → Receiver Maintenance**, the user clicks
   **"Import from Document"** next to the existing manual-entry line grid.
3. **"Take Photo"** (if a webcam is available) or **"Choose File"**
   (a photo already on the phone/disk — reuses the same `JFileChooser`
   pattern already used elsewhere in BlueSeer, e.g. `ItemMaint`'s
   image/attachment pickers).
4. The image is preprocessed (deskew/crop) and sent to the configured
   local LLM in the background (progress spinner, screen stays
   responsive) — no extra screen, this happens right on the Receiver
   Maintenance panel.
5. The line grid fills in — supplier, invoice number, date, and each line
   (item, quantity, unit cost). Known suppliers' items auto-map from
   memory (see the item-alias point above); anything new or unclear is
   highlighted so it's obvious what to check before saving.
6. The user glances it over, fixes anything wrong exactly like fixing a
   typo, and clicks **Add** — the same button, same save path, same
   `rvd_lot`/`rvd_serial` capture as manual entry today. Nothing about
   the underlying save/validation logic changes.
7. The source photo and what was extracted are kept in the background
   (`docData`/`doc_import_log`) purely as a traceability record for
   food-safety audit purposes — not shown as a workflow step staff have
   to think about.

### 7.2 Second integration point: Ingredient Data tab

Same pattern, launched from `ItemMaint`'s **Ingredient Data** tab,
targeting a supplier ingredient spec sheet: legal name, allergen
declarations, E-numbers. Smaller/simpler schema than the invoice case but
arguably the highest food-safety-value target, since spec sheets are
exactly where allergen data originates today (typed by hand from paper).
Its own story group since it reuses the capture UI but has a different
extraction schema and commit target (`ing_mstr`/`ing_allergen` instead of
`recv_det`).

### 7.3 One-time setup (not a staff-facing screen)

A **"Document Import"** section in System Control: provider (LM Studio /
Ollama), base URL, model name, and an enable/disable toggle. This is
configured once, by whoever sets the customer up (following a
`docs/user-guide` page, same as the existing site-branding/background-
color setup), and shop staff never see or need to understand it — if it's
off or unreachable, the Import buttons simply don't appear.

## 8. Out of scope for this epic (explicitly deferred)

- Cloud LLM providers (OpenAI/Anthropic/etc.) — local-only for v1, per the
  data-sensitivity rationale in §2. The `OpenAIClientSettings`-based
  architecture makes adding a cloud provider later a config option, not a
  rearchitecture, if a customer ever asks for it.
- Multi-step/autonomous agent workflows (e.g., auto-reconciling against
  an open PO, auto-flagging price variances) — see §3.
- Multi-page document splitting/classification pipelines.
- Visual bounding-box grounding on the review screen.
- A dedicated "review and accept" screen separate from the real data-entry
  form — see §7, the extraction fills the same form staff already use,
  by design.
- OCR-only (non-LLM) fallback path for sites with no local LLM runtime at
  all — the feature is simply unavailable there for v1 (toggle stays off).

## 9. User stories

### Foundation

- **DOC-1 — Add Koog dependency and confirm Java-callable, no Kotlin
  toolchain needed.** Add `ai.koog:koog-agents` to `pom.xml`, write a
  throwaway `main()` smoke test calling a trivial Koog agent from plain
  Java, confirm `mvn package` and the jpackage/jlink installer build still
  succeed with the new (and any transitive `kotlin-stdlib`) jars bundled.
  *Acceptance:* a Java class in `com.blueseer.doc` successfully invokes a
  Koog `executeStructured` call and the full existing build (`mvn package
  -Pjpackage`) still completes.

- **DOC-2 — Wire up LM Studio as the default runtime.** Configure Koog's
  `OpenAILLMClient` with `baseUrl = "http://localhost:1234"` /
  `chatCompletionsPath = "v1/chat/completions"`, pick a small
  vision-capable local model (e.g. Qwen2-VL or Llama-3.2-Vision GGUF) as
  the recommended default, and get one real `executeStructured`
  image-extraction call round-tripping end-to-end. Also wire the Ollama
  path (`baseUrl = "http://localhost:11434"`) as the second supported
  runtime. *Acceptance:* both runtimes produce a working structured
  extraction from a real test image.

- **DOC-3 — Pick and integrate an image preprocessing approach.** Spike
  a small deskew/crop/contrast-normalization step for phone-photographed
  documents. Prefer a pure-Java library over native bindings (OpenCV) to
  avoid repeating the native-library packaging pain already hit with JBR/
  jpackage. *Acceptance:* a test photo taken at a deliberate angle/skew
  is normalized before being handed to the extraction call, verified by
  visual inspection of the preprocessed output.

- **DOC-4 — Schema patch: `ov_ctrl` LLM runtime columns,
  `doc_import_log` audit table, `doc_item_alias` learning table.** New
  `.patchsqlv_docimport` file, same idempotent
  `information_schema.columns` pattern as existing patches.

- **DOC-5 — `docData.java` CRUD** for the runtime config, the import
  audit log, and the item-alias lookups, following `ingData.java`'s
  conventions.

### Extraction engine

- **DOC-6 — Define the invoice/packing-slip extraction schema** as Java
  records (header: supplier, invoice #, date; repeated line items: raw
  description, quantity, unit, unit cost) and wire `executeStructured`
  against it in `DocumentExtractionService`.

- **DOC-7 — Field-level confidence in the extraction output.** Extend the
  schema/prompt so each field (or line item) carries a confidence
  indicator, used only to decide whether to leave a field blank/highlighted
  versus fill it in — never shown to the user as a number or technical
  term (§7).

- **DOC-8 — Define the ingredient-spec extraction schema** (legal name,
  declared allergens, E-numbers) for the Ingredient Data tab integration.

- **DOC-9 — Error handling for unreachable/misconfigured LLM runtime.**
  Clear, plain-language message ("Couldn't reach the local AI service —
  check it's running" — not a stack trace, not technical jargon) when the
  configured `baseUrl` isn't reachable, the model isn't loaded, or the
  response can't be parsed even after Koog's `fixingParser` retry.

### Capture & review UX

- **DOC-10 — Capture entry point:** a single "Import from Document"
  button — "Take Photo" (webcam, if available) / "Choose File" — reusable
  across both integration points (§7.1 and §7.2), landing straight back on
  the same screen the button was clicked from. SwingWorker-backed
  preprocessing + extraction call with a progress indicator.

- **DOC-11 — Fill the existing grid/form directly** with extracted values
  rather than building a separate review screen — the source data-entry
  screen (Receiver Maintenance line grid / Ingredient Data tab fields) *is*
  the review UI. Low-confidence fields get a simple visual highlight only.

- **DOC-12 — Item lookup with auto-map from memory:** each extracted line
  description is matched against `doc_item_alias` (see DOC-21) first; a
  known match fills the item silently, an unknown one opens the existing
  item autocomplete/lookup so staff pick it once. Never silently attaches
  an unfamiliar description to the wrong SKU (§7.1 step 5).

- **DOC-21 — `doc_item_alias` learning table:** when a user picks/corrects
  an item for a given supplier + raw line-description pair, remember it
  (`doc_item_alias`: supplier, raw text, item code). Future imports from
  that supplier auto-fill the same item without asking again — this is
  the main lever for cutting repeat work, since most receiving is the
  same suppliers/items on repeat (§7).

### Receiver Maintenance integration

- **DOC-14 — "Import from Document" button on `RecvMaint`,** landing
  extracted values directly in the existing line grid exactly as manual
  entry would (§7.1) — no bypass of existing `rvd_lot`/`rvd_serial`
  capture or save validation, same **Add** button as today.

### Item Maintenance / Ingredient Data integration

- **DOC-15 — "Import from Document" button on the Ingredient Data tab,**
  landing extracted values directly into the existing tab fields,
  committed through the existing `ingData` save path (§7.2).

### Admin / settings

- **DOC-16 — System Control "Document Import" section:** provider
  dropdown, base URL, model name, enable/disable toggle (§7.3) — a
  one-time setup screen, not part of any staff workflow.

- **DOC-17 — Feature-flagged rollout:** confirm the Import buttons on
  `RecvMaint` and the Ingredient Data tab are hidden entirely when
  `ov_llm_enabled` is off, so sites without a local LLM runtime see no
  change to existing screens.

### Verification

- **DOC-18 — Unit tests** for the extraction-schema mapping and
  confidence-flagging logic against canned model responses (no live LLM
  call required for CI).

- **DOC-19 — End-to-end verification** under the existing Xvfb harness:
  a real (or realistic mock) LM Studio/Ollama instance, a sample invoice
  photo, full capture → review → accept → Receiver Maintenance flow,
  screenshotted before/after per this project's established PR test-plan
  convention.

- **DOC-20 — Documentation:** a new `docs/user-guide/11-document-import.md`
  page, following the existing user-guide format, covering both the admin
  setup (installing LM Studio, pointing System Control at it) and the
  end-user capture/review workflow.

## 10. Suggested sequencing

Purely dependency order — there's no staged rollout or feedback-gathering
period to plan around (BlueSeer is installed per-customer, not run as a
hosted product with usage analytics), so each phase below just unblocks
the next:

1. **DOC-1 → DOC-2** (Koog dependency + LM Studio/Ollama wired up).
2. DOC-3 → DOC-4 → DOC-5 (image preprocessing + schema/data layer, can run
   alongside DOC-2).
3. DOC-6 → DOC-7 → DOC-9 (extraction engine for the invoice case).
4. DOC-10 → DOC-11 → DOC-12 → DOC-21 (capture flow, filling the existing
   grid directly, item-alias memory).
5. DOC-14 (Receiver Maintenance integration — the first fully usable
   slice).
6. DOC-8 → DOC-15 (Ingredient Data tab integration, reusing the same
   capture shell).
7. DOC-16 → DOC-17 (one-time setup screen + hide-when-disabled).
8. DOC-18 → DOC-19 → DOC-20 throughout, not saved for the end.
