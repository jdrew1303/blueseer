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
- **LM Studio is the user's preferred runtime** (his words: "nice UI and
  very user friendly") and, like Ollama, serves an OpenAI-compatible REST
  API. Koog's `OpenAILLMClient` takes an `OpenAIClientSettings(baseUrl,
  chatCompletionsPath, ...)` — this is Koog's documented mechanism for
  pointing the OpenAI client at non-OpenAI endpoints (shown for Azure
  OpenAI and other OpenAI-compatible providers). LM Studio's own default
  endpoint (`http://localhost:1234/v1/chat/completions`) fits this shape
  directly: `OpenAIClientSettings(baseUrl = "http://localhost:1234",
  chatCompletionsPath = "v1/chat/completions")`.
  - **Caveat, stated plainly:** there is no first-class `lmStudio { }`
    provider block in Koog the way there is for Ollama
    (`baseUrl = "http://localhost:11434"`) — [JetBrains/koog issue #139
    "LM Studio Support"](https://github.com/JetBrains/koog/issues/139) is
    still open. The generic-`OpenAIClientSettings` route above is the
    correct integration path by direct analogy to Koog's documented Azure/
    MiniMax examples, but it hasn't been proven end-to-end against a real
    LM Studio server yet. **Story DOC-2 below is a short spike to confirm
    this before any other story depends on it.**
  - Ollama has confirmed first-class Koog support today and should be
    offered as the alternative runtime from day one — some users will
    prefer Ollama's CLI-first workflow, and it de-risks the "LM Studio
    might not work cleanly" case for v1.

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
other real table without an explicit user "Accept" action per field group.
The review screen always shows extracted values pre-filled into ordinary
editable form fields (not a locked/read-only preview) so a wrong read is
just as easy to fix as it would be to type correctly the first time. This
matters more here than in most ERP features because bad data entering
`ing_mstr`/allergen fields is a food-safety issue, not just an inventory
nuisance — the same seriousness already applied to the FIC labeling work
applies here.

## 7. UX and UI integration

### 7.1 End-user flow (the bakery-shop scenario)

1. A supplier's paper invoice arrives with today's flour delivery.
2. From **Receiving → Receiver Maintenance**, the user clicks a new
   **"Import from Document"** button next to the existing manual-entry
   line grid.
3. A small capture dialog opens: **"Take Photo"** (if a webcam is
   available) or **"Choose File"** (existing PDF/photo already on disk —
   reuses the same `JFileChooser` pattern already used elsewhere in
   BlueSeer, e.g. `ItemMaint`'s image/attachment pickers).
4. The image is preprocessed (deskew/crop) and sent to the configured
   local LLM in the background (SwingWorker, progress spinner, screen
   stays responsive/cancelable).
5. The **Extraction Review** panel opens: the source image on the left,
   a pre-filled editable form on the right — supplier, invoice number,
   date, and a line-item grid (item description as read, quantity, unit
   cost). Fields the model flagged low-confidence are highlighted (e.g. a
   yellow left-border on that field) so the user knows to double-check
   them, not just trust the whole form.
6. For each line, the user maps the read item description to an actual
   BlueSeer `item_mstr` row via the existing item lookup/autocomplete
   (never auto-matched blind — a misread "Flour 25kg" must not silently
   attach to the wrong SKU).
7. User clicks **Accept & Populate** — the confirmed values flow into the
   normal Receiver Maintenance grid exactly as if typed by hand, ready for
   the existing lot/serial capture (`rvd_lot`/`rvd_serial`) and the
   existing **Add**/save flow. The import doesn't bypass any existing
   validation — it's a faster way to fill the same form.
8. A record of the import (source image, extracted JSON, what was
   accepted/edited/rejected) is kept for audit, in the new `docData`
   table.

### 7.2 Second integration point: Ingredient Data tab

Same capture/review pattern, launched from `ItemMaint`'s **Ingredient
Data** tab, targeting a supplier ingredient spec sheet: legal name,
allergen declarations, E-numbers. This is a smaller/simpler schema than
the invoice case but arguably the highest food-safety-value target,
since spec sheets are exactly where allergen data originates today (typed
by hand from paper). Treated as its own story group since it reuses the
capture/review UI shell but has a different extraction schema and a
different commit target (`ing_mstr`/`ing_allergen` instead of
`recv_det`).

### 7.3 Settings/admin UX

A **"Document Import"** section added to System Control: provider
dropdown (LM Studio / Ollama), base URL field, model name field, a
**"Test Connection"** button (round-trips a trivial prompt to confirm the
configured runtime is reachable before a user ever hits it mid-workflow),
and an enable/disable toggle (the feature is fully optional — sites
without a local LLM runtime set up simply don't see the Import buttons).

## 8. Out of scope for this epic (explicitly deferred)

- Cloud LLM providers (OpenAI/Anthropic/etc.) — local-only for v1, per the
  data-sensitivity rationale in §2. The `OpenAIClientSettings`-based
  architecture makes adding a cloud provider later a config option, not a
  rearchitecture, if a customer ever asks for it.
- Multi-step/autonomous agent workflows (e.g., auto-reconciling against
  an open PO, auto-flagging price variances) — see §3.
- Multi-page document splitting/classification pipelines.
- Visual bounding-box grounding on the review screen.
- Auto-accept of any field without human confirmation, regardless of
  confidence score.
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

- **DOC-2 — Spike: confirm LM Studio via `OpenAIClientSettings`.** Stand
  up a local LM Studio instance with a small vision-capable model
  (e.g. a Qwen2-VL or Llama-3.2-Vision GGUF), configure Koog's
  `OpenAILLMClient` with `baseUrl = "http://localhost:1234"` /
  `chatCompletionsPath = "v1/chat/completions"`, and confirm a real
  `executeStructured` image-extraction call round-trips correctly. Also
  smoke-test the same against Ollama, to confirm the alternate-runtime
  path. *Acceptance:* a short written note (or an addendum to this doc)
  confirming which runtime(s) actually work, with the exact working
  config, before any UI work begins. If LM Studio doesn't work cleanly,
  this is the checkpoint to re-scope to Ollama-first for v1.

- **DOC-3 — Pick and integrate an image preprocessing approach.** Spike
  a small deskew/crop/contrast-normalization step for phone-photographed
  documents. Prefer a pure-Java library over native bindings (OpenCV) to
  avoid repeating the native-library packaging pain already hit with JBR/
  jpackage. *Acceptance:* a test photo taken at a deliberate angle/skew
  is normalized before being handed to the extraction call, verified by
  visual inspection of the preprocessed output.

- **DOC-4 — Schema patch: `ov_ctrl` LLM runtime columns +
  `doc_import_log` audit table.** New `.patchsqlv_docimport` file, same
  idempotent `information_schema.columns` pattern as existing patches.

- **DOC-5 — `docData.java` CRUD** for the runtime config and the import
  audit log, following `ingData.java`'s conventions.

### Extraction engine

- **DOC-6 — Define the invoice/packing-slip extraction schema** as Java
  records (header: supplier, invoice #, date; repeated line items: raw
  description, quantity, unit, unit cost) and wire `executeStructured`
  against it in `DocumentExtractionService`.

- **DOC-7 — Field-level confidence in the extraction output.** Extend the
  schema/prompt so each field (or line item) carries a confidence
  indicator the review UI can use to highlight uncertain reads, per the
  routing pattern in §4 (review-flagging only — no auto-accept path).

- **DOC-8 — Define the ingredient-spec extraction schema** (legal name,
  declared allergens, E-numbers) for the Ingredient Data tab integration.

- **DOC-9 — Error handling for unreachable/misconfigured LLM runtime.**
  Clear, actionable error message (not a stack trace) when the configured
  `baseUrl` isn't reachable, model isn't loaded, or the response can't be
  parsed even after Koog's `fixingParser` retry — with a link/pointer back
  to the System Control "Test Connection" button.

### Capture & review UX

- **DOC-10 — Capture dialog:** "Take Photo" (webcam, if available) /
  "Choose File" entry point, reusable across both integration points (§7.1
  and §7.2). SwingWorker-backed preprocessing + extraction call with a
  cancelable progress indicator.

- **DOC-11 — `ExtractionReviewPanel`:** side-by-side source-image +
  editable pre-filled form, low-confidence field highlighting, per §7.1
  step 5. Built with MigLayout (consistent with the in-progress GroupLayout
  → MigLayout migration convention).

- **DOC-12 — Item-lookup mapping step** for invoice/packing-slip line
  items: each extracted line description must be explicitly matched to a
  real `item_mstr` row via the existing item autocomplete before it can be
  accepted — no blind auto-matching (§7.1 step 6).

- **DOC-13 — Import audit trail UI:** a simple browse screen (mirroring
  existing `*Browse.java` patterns) listing past imports with source
  image, extracted JSON, and what was actually accepted, for traceability.

### Receiver Maintenance integration

- **DOC-14 — "Import from Document" button on `RecvMaint`,** launching
  the capture/review flow scoped to the invoice/packing-slip schema, with
  **Accept & Populate** flowing confirmed values into the existing
  Receiver Maintenance line grid exactly as manual entry would (§7.1 step
  7) — no bypass of existing `rvd_lot`/`rvd_serial` capture or save
  validation.

### Item Maintenance / Ingredient Data integration

- **DOC-15 — "Import from Document" button on the Ingredient Data tab,**
  launching the capture/review flow scoped to the ingredient-spec schema,
  committing confirmed values to `ing_mstr`/`ing_allergen` through the
  existing `ingData` save path (§7.2).

### Admin / settings

- **DOC-16 — System Control "Document Import" section:** provider
  dropdown, base URL, model name, enable/disable toggle, **Test
  Connection** button (§7.3).

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

1. **DOC-1 → DOC-2** (foundation + the LM Studio spike) — do these first
   and don't commit to anything else until DOC-2's answer is in, since it
   determines whether v1 ships LM Studio-first or Ollama-first.
2. DOC-3 → DOC-4 → DOC-5 (remaining foundation, can run alongside DOC-2).
3. DOC-6 → DOC-7 → DOC-9 (extraction engine for the invoice case).
4. DOC-10 → DOC-11 → DOC-12 → DOC-13 (capture/review UI shell).
5. DOC-14 (first real integration: Receiver Maintenance) — ship and get
   real user feedback here before building the second integration point,
   the same "ship one thing, learn, then extend" pattern already used for
   the site-logo branding work this project did earlier.
6. DOC-8 → DOC-15 (Ingredient Data tab integration, once the shell is
   proven).
7. DOC-16 → DOC-17 (admin polish, can land any time after DOC-2).
8. DOC-18 → DOC-19 → DOC-20 throughout, not saved for the end.
