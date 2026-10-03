# Book Generator

Book Generator is a Java 21 + Spring Boot application that analyzes a GitHub repository or ZIP project, indexes project evidence in ChromaDB, recommends sections from a predefined global student-report format, and generates validated DOCX and PDF documentation.

## Core flow

1. Submit GitHub URL or ZIP.
2. Run deterministic project analysis.
3. Index repository evidence in ChromaDB.
4. Review and modify recommended chapters, sections, content, images, and diagrams on the same page.
5. Generate one complete chapter per LLM call using the available worker pool.
6. Resume from persisted chapter checkpoints after a restart.
7. Validate assets, assemble the global document format, validate DOCX, convert to PDF, and expose both files.

The documentation format is predefined in `src/main/resources/templates/global-student-project-v1.json`; users do not upload a template in V1.

## Local services

- PostgreSQL
- ChromaDB on `http://localhost:8000`
- Ollama on `http://localhost:11434`
- LibreOffice for PDF conversion
- Node.js + Mermaid CLI (`mmdc`) when diagrams are enabled

## Run

Configure `.env` from `.env.example`, start the required services, then run `./mvnw spring-boot:run`.

For production, use the `prod` profile and provide all required secrets through environment variables.

### Initial administrator login

To create the first admin account, set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` in the private `.env` file (or deployment environment), then restart the application. Passwords must contain at least 6 characters and be at most 72 UTF-8 bytes. `ADMIN_BOOTSTRAP_NAME` is optional. The bootstrap creates the account if it does not exist, or grants `ROLE_ADMIN` to the matching account without replacing an existing password. After restarting, sign in through the regular login page and open `/#template-admin` or select **Template Admin** in the sidebar. Leave both bootstrap credentials unset after provisioning if you do not want startup to grant admin access to that account again.

## Free Marketplace

Open `/#marketplace` to browse published resources without signing in. Signed-in users can submit a project ZIP, PDF, or DOCX for review, manage their listings, and submit completed DocGen documentation directly from the completion screen. Direct publishing references the generated document rather than asking the user to download and upload it again. The Marketplace includes dedicated `Generated with DocGen AI` and `Community uploads` origin filters.

Marketplace API routes are under `/api/v1/marketplace`. Admins with `ROLE_ADMIN` can use the **Review queue** button in the Marketplace header or open `/#marketplace-moderation` to review pending submissions. A moderator approval is required before a seller can publish a listing; only published listings appear in public browse and download endpoints. Uploads require an explicit rights confirmation and are limited to 20 MB per file. Project ZIPs are checked for unsafe paths, nested archives, executable files, private credential filenames, and likely secrets in scanned text. DOCX packages are bounded and inspected for unsafe embedded paths and likely secrets. These checks are not a substitute for antivirus or a malware sandbox.

New submissions stay private while awaiting moderation. A moderator account with `ROLE_ADMIN` can approve or reject a submission; the seller explicitly publishes an approved listing. Only published listings, their bounded document/file-name previews, and their free downloads are public. Uploaded files are stored under the configured storage root and are not served through raw filesystem URLs.

## Post-generation document editor

Completed DOCX jobs can be opened from the completion screen or My Projects. The editor presents the document as selectable pages, generates exactly two AI alternatives for a selected passage and instruction, and applies a chosen alternative to the DOCX. Edits use an atomic document version check, keep private revision snapshots for undo/redo, and update both DOCX and PDF exports. AI editing uses the configured LLM provider; PDF regeneration requires the configured LibreOffice installation.

## Repository understanding

Project ingestion is content-based, not filename-only. For GitHub URLs and ZIP uploads, the scanner recursively inspects relevant repository files, reads supported text/source/configuration content within safety limits, fingerprints analyzed files with SHA-256, extracts deterministic imports, annotations, classes/functions/method symbols, and indexes the actual file content in Chroma with file/line metadata. Binary/media/model files are retained as factual metadata but are not represented as source code. Generated/vendor/build directories are intentionally excluded. The system does not use an LLM to invent repository facts during analysis.

## Documentation factuality and code understanding

The repository analysis pipeline is content-first and evidence-preserving. It reads relevant source/configuration text instead of treating filenames as proof. The deterministic polyglot analyzer records source fingerprints, line counts, imports, symbols, annotations, routes and language-specific constructs for Java, Python, JavaScript/TypeScript, HTML/CSS, SQL, Go, Rust, Kotlin, Swift, PHP, C/C++, C#, Vue/Svelte and scripting files where applicable.

Generated documentation chapters must cite the exact retrieved source evidence internally. Citations are validated against real repository file/line ranges and stripped before final document assembly. Dependency chapter prose is context only and never becomes authoritative project evidence. Unsupported claims cause the chapter to be rejected rather than silently accepted. The deterministic polyglot analyzer is lexical/structural rather than a compiler-grade AST for every language.

Repository analysis also fails explicitly when a relevant text/source file exceeds the safe content limit; the system does not silently generate documentation from a partial repository. Generated/vendor/build directories remain excluded by design.


## LLM provider configuration

Chapter generation uses the generic LLM worker pool. Up to four provider slots are supported without backend changes.

Configure only the slots you want in `.env`:

- `LLM_PROVIDER_1`, `LLM_MODEL_1`, `LLM_BASE_URL_1`, `LLM_API_KEY_1`
- `LLM_PROVIDER_2`, `LLM_MODEL_2`, `LLM_BASE_URL_2`, `LLM_API_KEY_2`
- `LLM_PROVIDER_3`, `LLM_MODEL_3`, `LLM_BASE_URL_3`, `LLM_API_KEY_3`
- `LLM_PROVIDER_4`, `LLM_MODEL_4`, `LLM_BASE_URL_4`, `LLM_API_KEY_4`

Empty provider slots are detected and ignored automatically. The supported provider types are `ollama`, `openai`, `gemini`, and `huggingface`.

Each configured provider receives one worker. The existing retry, cooldown, failure isolation, and recovery behavior is shared by all configured providers. Provider selection for chapter generation is therefore controlled by `.env`; changing from one to multiple supported providers does not require changing chapter-generation code.


## Current V8 security defaults

- Google login is disabled by default until valid OAuth credentials are supplied.
- Password reset is disabled by default in V8; SMTP configuration is intentionally not required.
- `.env` is ignored by Git. Do not commit real API keys, OAuth secrets, database passwords, or JWT secrets.
- For production, replace the local JWT secret and provide real credentials through environment variables.
