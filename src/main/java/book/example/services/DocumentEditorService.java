package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.dto.DocumentEditRequest;
import book.example.dto.DocumentTransformRequest;
import book.example.dto.EditorRevisionSnapshot;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentEditorService {
    private static final int PARAGRAPHS_PER_PAGE = 24;
    private static final int MAX_SELECTION_LENGTH = 6000;
    private static final Map<String, Object> ALTERNATIVES_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "alternatives", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "minItems", 2,
                            "maxItems", 2)),
            "required", List.of("alternatives"),
            "additionalProperties", false);

    private final JobRepository jobRepository;
    private final LlmWorkerPool llmWorkerPool;
    private final PdfConversionService pdfConversionService;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;

    public DocumentEditorService(
            JobRepository jobRepository,
            LlmWorkerPool llmWorkerPool,
            PdfConversionService pdfConversionService,
            ObjectMapper objectMapper,
            @Value("${app.storage.root:generated}") String storageRoot) {
        this.jobRepository = jobRepository;
        this.llmWorkerPool = llmWorkerPool;
        this.pdfConversionService = pdfConversionService;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> load(String jobId, AppUser user) {
        DocumentationJob job = getCompletedJob(jobId, user);
        Path documentPath = requiredDocument(job);
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(documentPath))) {
            List<XWPFParagraph> paragraphs = paragraphs(document.getBodyElements());
            List<Map<String, Object>> pages = new ArrayList<>();
            for (int start = 0; start < paragraphs.size(); start += PARAGRAPHS_PER_PAGE) {
                List<Map<String, Object>> pageParagraphs = new ArrayList<>();
                int end = Math.min(start + PARAGRAPHS_PER_PAGE, paragraphs.size());
                for (int index = start; index < end; index++) {
                    XWPFParagraph paragraph = paragraphs.get(index);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("index", index);
                    item.put("text", paragraph.getText());
                    item.put("style", paragraph.getStyle());
                    pageParagraphs.add(item);
                }
                pages.add(Map.of("pageNumber", pages.size() + 1, "paragraphs", pageParagraphs));
            }
            return Map.of(
                    "jobId", jobId,
                    "version", job.getVersion(),
                    "pages", pages,
                    "canUndo", job.getEditorHistoryPosition() > 0,
                    "canRedo", canRedo(job));
        } catch (IOException exception) {
            throw new IllegalStateException("The generated DOCX could not be opened for editing.", exception);
        }
    }

    public List<String> transform(String jobId, AppUser user, DocumentTransformRequest request) {
        if (request == null) throw new IllegalArgumentException("An editing request is required.");
        DocumentationJob job = getCompletedJob(jobId, user);
        validateSelection(request.paragraphIndex(), request.startOffset(), request.endOffset(),
                request.selectedText());
        String paragraphText = readParagraph(job, request.paragraphIndex());
        String selectedText = verifySelection(
                paragraphText, request.startOffset(), request.endOffset(), request.selectedText());
        String instruction = request.instruction() == null ? "" : request.instruction().trim();
        if (instruction.isEmpty() || instruction.length() > 1000) {
            throw new IllegalArgumentException("Enter an editing instruction of 1 to 1,000 characters.");
        }
        String before = paragraphText.substring(0, request.startOffset());
        String after = paragraphText.substring(request.endOffset());
        String prompt = """
                You are editing one selected passage in a generated project document.
                Follow the user's editing instruction and preserve factual meaning unless explicitly asked otherwise.
                Return exactly two distinct, complete replacements for the selected passage as JSON using the required schema.
                Do not include commentary or markdown.

                User instruction:
                %s

                Context before the selection:
                %s

                Selected passage to replace:
                %s

                Context after the selection:
                %s
                """.formatted(instruction, tailContext(before), selectedText, headContext(after));
        try {
            String response = llmWorkerPool.generateWithRetry(prompt, ALTERNATIVES_SCHEMA);
            JsonNode root = objectMapper.readTree(response);
            JsonNode alternatives = root.path("alternatives");
            if (!alternatives.isArray() || alternatives.size() != 2) {
                throw new IllegalStateException("The AI provider did not return exactly two edit alternatives.");
            }
            List<String> values = new ArrayList<>(2);
            for (JsonNode alternative : alternatives) {
                if (!alternative.isTextual() || alternative.asText().isBlank()
                        || alternative.asText().length() > MAX_SELECTION_LENGTH) {
                    throw new IllegalStateException("The AI provider returned an invalid edit alternative.");
                }
                values.add(alternative.asText().trim());
            }
            return List.copyOf(values);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("The AI provider returned an unreadable edit response.", exception);
        }
    }

    @Transactional
    public Map<String, Object> save(String jobId, AppUser user, DocumentEditRequest request) {
        if (request == null) throw new IllegalArgumentException("An edit request is required.");
        DocumentationJob job = getCompletedJob(jobId, user);
        requireCurrentVersion(job, request.version());
        validateSelection(request.paragraphIndex(), request.startOffset(), request.endOffset(),
                request.selectedText());
        if (request.replacement() == null || request.replacement().isBlank()
                || request.replacement().length() > MAX_SELECTION_LENGTH) {
            throw new IllegalArgumentException("Replacement text must contain 1 to 6,000 characters.");
        }

        List<EditorRevisionSnapshot> history = readHistory(job);
        int position = job.getEditorHistoryPosition();
        try {
            if (history.isEmpty()) {
                Path baseDirectory = revisionsDirectory(jobId).resolve("base");
                Files.createDirectories(baseDirectory);
                Path baseDocx = baseDirectory.resolve("documentation.docx");
                Files.copy(requiredDocument(job), baseDocx, StandardCopyOption.REPLACE_EXISTING);
                Path basePdf = baseDirectory.resolve("documentation.pdf");
                Path sourcePdf = job.getPdfPath() == null || job.getPdfPath().isBlank()
                        ? null
                        : Path.of(job.getPdfPath()).toAbsolutePath().normalize();
                if (sourcePdf != null && !sourcePdf.startsWith(storageRoot)) {
                    throw new IllegalStateException("The generated PDF is outside the document storage directory.");
                }
                if (sourcePdf != null && Files.isRegularFile(sourcePdf)) {
                    Files.copy(sourcePdf, basePdf, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    pdfConversionService.convertToPdf(baseDocx);
                }
                history.add(new EditorRevisionSnapshot(baseDocx.toString(), basePdf.toString()));
                position = 0;
            }
            if (position < history.size() - 1) {
                history = new ArrayList<>(history.subList(0, position + 1));
            }

            Path currentDocument = Path.of(history.get(position).getDocumentPath());
            Path outputDirectory = revisionsDirectory(jobId).resolve(UUID.randomUUID().toString());
            Files.createDirectories(outputDirectory);
            Path outputDocument = outputDirectory.resolve("documentation.docx");
            try {
                replaceInDocument(currentDocument, outputDocument, request);
                Path outputPdf = pdfConversionService.convertToPdf(outputDocument);
                history.add(new EditorRevisionSnapshot(outputDocument.toString(), outputPdf.toString()));
                int nextPosition = history.size() - 1;
                updateState(job, user, request.version(), outputDocument, outputPdf, history, nextPosition);
            } catch (RuntimeException exception) {
                deleteQuietly(outputDirectory);
                throw exception;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The document edit could not be saved.", exception);
        }
        return load(jobId, user);
    }

    @Transactional
    public Map<String, Object> moveHistory(String jobId, AppUser user, long expectedVersion, boolean redo) {
        DocumentationJob job = getCompletedJob(jobId, user);
        requireCurrentVersion(job, expectedVersion);
        List<EditorRevisionSnapshot> history = readHistory(job);
        int position = job.getEditorHistoryPosition() + (redo ? 1 : -1);
        if (history.isEmpty() || position < 0 || position >= history.size()) {
            throw new IllegalStateException(redo ? "There is no edit to redo." : "There is no edit to undo.");
        }
        EditorRevisionSnapshot target = history.get(position);
        if (!Files.isRegularFile(Path.of(target.getDocumentPath()))
                || !Files.isRegularFile(Path.of(target.getPdfPath()))) {
            throw new IllegalStateException("The saved editor revision is missing.");
        }
        updateState(job, user, expectedVersion, Path.of(target.getDocumentPath()),
                Path.of(target.getPdfPath()), history, position);
        return load(jobId, user);
    }

    private void updateState(
            DocumentationJob job,
            AppUser user,
            long expectedVersion,
            Path documentPath,
            Path pdfPath,
            List<EditorRevisionSnapshot> history,
            int position) {
        try {
            int updated = jobRepository.updateEditorState(
                    job.getJobId(),
                    user.getId(),
                    expectedVersion,
                    documentPath.toString(),
                    pdfPath.toString(),
                    objectMapper.writeValueAsString(history),
                    position,
                    LocalDateTime.now());
            if (updated != 1) {
                throw new IllegalStateException("This document changed in another session. Reload before saving.");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("The editor revision could not be recorded.", exception);
        }
    }

    private void replaceInDocument(Path source, Path destination, DocumentEditRequest request) {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(source))) {
            List<XWPFParagraph> paragraphs = paragraphs(document.getBodyElements());
            if (request.paragraphIndex() < 0 || request.paragraphIndex() >= paragraphs.size()) {
                throw new IllegalArgumentException("The selected paragraph no longer exists.");
            }
            XWPFParagraph paragraph = paragraphs.get(request.paragraphIndex());
            String text = paragraph.getText();
            verifySelection(text, request.startOffset(), request.endOffset(), request.selectedText());
            String replacement = request.replacement();
            int runStart = 0;
            XWPFRun insertionRun = null;
            int insertionRunEnd = -1;
            List<XWPFRun> runs = new ArrayList<>(paragraph.getRuns());
            for (XWPFRun run : runs) {
                String runText = run.text();
                int runEnd = runStart + runText.length();
                if (insertionRun == null && request.startOffset() <= runEnd) {
                    insertionRun = run;
                    insertionRunEnd = runEnd;
                }
                runStart = runEnd;
            }
            if (insertionRun == null) {
                insertionRun = paragraph.createRun();
                setRunText(insertionRun, replacement);
            } else {
                runStart = 0;
                boolean inserted = false;
                for (XWPFRun run : runs) {
                    String runText = run.text();
                    int runEnd = runStart + runText.length();
                    String prefix = request.startOffset() > runStart
                            ? runText.substring(0, Math.min(request.startOffset() - runStart, runText.length()))
                            : "";
                    String suffix = request.endOffset() < runEnd
                            ? runText.substring(Math.max(0, request.endOffset() - runStart))
                            : "";
                    if (request.startOffset() >= runStart && request.startOffset() <= runEnd && !inserted) {
                        setRunText(run, prefix + replacement + suffix);
                        inserted = true;
                    } else if (runEnd <= request.startOffset() || runStart >= request.endOffset()) {
                        if (runStart >= request.endOffset()) {
                            if (inserted) setRunText(run, suffix);
                        }
                    } else if (runStart < request.startOffset() && runEnd > request.endOffset()) {
                        setRunText(run, prefix + replacement + suffix);
                        inserted = true;
                    } else if (runEnd > request.startOffset() && runStart < request.endOffset()) {
                        if (inserted) setRunText(run, suffix);
                        else {
                            setRunText(run, prefix + replacement);
                            inserted = true;
                        }
                    }
                    runStart = runEnd;
                }
                if (!inserted && insertionRunEnd == 0) setRunText(insertionRun, replacement);
            }
            try (var output = Files.newOutputStream(destination)) {
                document.write(output);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The selected text could not be applied to the DOCX.", exception);
        }
    }

    private void setRunText(XWPFRun run, String value) {
        run.getCTR().getTList().clear();
        if (!value.isEmpty()) run.setText(value);
    }

    private String readParagraph(DocumentationJob job, int index) {
        Path path = requiredDocument(job);
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            List<XWPFParagraph> paragraphs = paragraphs(document.getBodyElements());
            if (index < 0 || index >= paragraphs.size()) {
                throw new IllegalArgumentException("The selected paragraph does not exist.");
            }
            return paragraphs.get(index).getText();
        } catch (IOException exception) {
            throw new IllegalStateException("The generated DOCX could not be opened.", exception);
        }
    }

    private List<EditorRevisionSnapshot> readHistory(DocumentationJob job) {
        if (job.getEditorHistoryJson() == null || job.getEditorHistoryJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<EditorRevisionSnapshot> history = objectMapper.readValue(
                    job.getEditorHistoryJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, EditorRevisionSnapshot.class));
            Path allowedDirectory = revisionsDirectory(job.getJobId());
            for (EditorRevisionSnapshot revision : history) {
                if (revision.getDocumentPath() == null || revision.getPdfPath() == null
                        || !Path.of(revision.getDocumentPath()).toAbsolutePath().normalize().startsWith(allowedDirectory)
                        || !Path.of(revision.getPdfPath()).toAbsolutePath().normalize().startsWith(allowedDirectory)) {
                    throw new IllegalStateException("The saved editor history contains an invalid file path.");
                }
            }
            if (job.getEditorHistoryPosition() >= history.size()) {
                throw new IllegalStateException("The saved editor history position is invalid.");
            }
            return history;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("The saved editor history is invalid.", exception);
        }
    }

    private List<XWPFParagraph> paragraphs(List<IBodyElement> bodyElements) {
        List<XWPFParagraph> result = new ArrayList<>();
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) result.add(paragraph);
            else if (element instanceof XWPFTable table) {
                for (var row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        result.addAll(paragraphs(cell.getBodyElements()));
                    }
                }
            }
        }
        return result;
    }

    private void validateSelection(int paragraphIndex, int startOffset, int endOffset, String selectedText) {
        if (paragraphIndex < 0 || startOffset < 0 || endOffset <= startOffset
                || selectedText == null || selectedText.isBlank() || selectedText.length() > MAX_SELECTION_LENGTH) {
            throw new IllegalArgumentException("Select a text range of 1 to 6,000 characters within one paragraph.");
        }
    }

    private String verifySelection(String paragraph, int start, int end, String expectedText) {
        if (start < 0 || end <= start || end > paragraph.length()
                || !paragraph.substring(start, end).equals(expectedText)) {
            throw new IllegalStateException("The selected text is stale. Reload the document and select it again.");
        }
        return expectedText;
    }

    private DocumentationJob getCompletedJob(String jobId, AppUser user) {
        DocumentationJob job = jobRepository.findByJobIdAndOwnerId(jobId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Documentation job not found."));
        if (!"COMPLETED".equals(job.getStatus())) {
            throw new IllegalStateException("Only completed documents can be edited.");
        }
        return job;
    }

    private Path requiredDocument(DocumentationJob job) {
        if (job.getDocumentPath() == null || job.getDocumentPath().isBlank()) {
            throw new IllegalStateException("The generated DOCX is not available.");
        }
        Path path = Path.of(job.getDocumentPath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("The generated DOCX is missing or outside the document storage directory.");
        }
        return path;
    }

    private Path revisionsDirectory(String jobId) {
        Path directory = storageRoot.resolve("jobs").resolve(jobId).resolve("editor").resolve("revisions")
                .toAbsolutePath().normalize();
        if (!directory.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid documentation job identifier.");
        }
        return directory;
    }

    private void requireCurrentVersion(DocumentationJob job, long expectedVersion) {
        if (job.getVersion() != expectedVersion) {
            throw new IllegalStateException("This document changed in another session. Reload before saving.");
        }
    }

    private boolean canRedo(DocumentationJob job) {
        List<EditorRevisionSnapshot> history = readHistory(job);
        return job.getEditorHistoryPosition() >= 0 && job.getEditorHistoryPosition() < history.size() - 1;
    }

    private String tailContext(String value) {
        return value.length() <= 800 ? value : value.substring(value.length() - 800);
    }

    private String headContext(String value) {
        return value.length() <= 800 ? value : value.substring(0, 800);
    }

    private void deleteQuietly(Path path) {
        try (var files = Files.walk(path)) {
            files.sorted((left, right) -> right.compareTo(left)).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // Preserve the original edit error; orphaned revision files are private to the job.
                }
            });
        } catch (IOException ignored) {
            // Preserve the original edit error.
        }
    }
}
