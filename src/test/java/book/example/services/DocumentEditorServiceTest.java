package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.DocumentationJob;
import book.example.Repository.JobRepository;
import book.example.dto.DocumentEditRequest;
import book.example.dto.DocumentTransformRequest;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentEditorServiceTest {
    @TempDir
    Path temporaryDirectory;

    private JobRepository jobRepository;
    private LlmWorkerPool llmWorkerPool;
    private PdfConversionService pdfConversionService;
    private DocumentEditorService service;
    private DocumentationJob job;
    private AppUser user;

    @BeforeEach
    void setUp() throws IOException {
        jobRepository = mock(JobRepository.class);
        llmWorkerPool = mock(LlmWorkerPool.class);
        pdfConversionService = mock(PdfConversionService.class);
        user = new AppUser();
        user.setId(UUID.randomUUID());

        Path jobDirectory = temporaryDirectory.resolve("jobs").resolve("editor-test");
        Files.createDirectories(jobDirectory);
        Path docx = jobDirectory.resolve("documentation.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var paragraph = document.createParagraph();
            paragraph.createRun().setText("Start ");
            paragraph.createRun().setText("selected");
            paragraph.createRun().setText(" finish");
            try (var output = Files.newOutputStream(docx)) {
                document.write(output);
            }
        }
        Path pdf = jobDirectory.resolve("documentation.pdf");
        Files.writeString(pdf, "original pdf");

        job = new DocumentationJob();
        job.setJobId("editor-test");
        job.setOwnerId(user.getId());
        job.setStatus("COMPLETED");
        job.setDocumentPath(docx.toString());
        job.setPdfPath(pdf.toString());
        job.setVersion(0);
        when(jobRepository.findByJobIdAndOwnerId(job.getJobId(), user.getId())).thenReturn(Optional.of(job));
        when(jobRepository.updateEditorState(
                eq(job.getJobId()), eq(user.getId()), anyLong(), anyString(), anyString(),
                anyString(), anyInt(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    long expectedVersion = invocation.getArgument(2);
                    if (job.getVersion() != expectedVersion) return 0;
                    job.setDocumentPath(invocation.getArgument(3));
                    job.setPdfPath(invocation.getArgument(4));
                    job.setEditorHistoryJson(invocation.getArgument(5));
                    job.setEditorHistoryPosition(invocation.getArgument(6));
                    job.setVersion(job.getVersion() + 1);
                    return 1;
                });
        when(pdfConversionService.convertToPdf(any(Path.class))).thenAnswer(invocation -> {
            Path docxPath = invocation.getArgument(0);
            Path convertedPdf = docxPath.resolveSibling("documentation.pdf");
            Files.writeString(convertedPdf, "converted pdf");
            return convertedPdf;
        });
        service = new DocumentEditorService(
                jobRepository, llmWorkerPool, pdfConversionService, new ObjectMapper(), temporaryDirectory.toString());
    }

    @Test
    void savesExactRangeAndSupportsUndoRedoWithVersionChecks() throws Exception {
        var result = service.save("editor-test", user,
                new DocumentEditRequest(0, 0, 6, 14, "selected", "updated"));
        assertEquals(1L, result.get("version"));
        assertEquals("Start updated finish", readFirstParagraph(Path.of(job.getDocumentPath())));

        var undone = service.moveHistory("editor-test", user, 1, false);
        assertEquals(2L, undone.get("version"));
        assertEquals("Start selected finish", readFirstParagraph(Path.of(job.getDocumentPath())));

        var redone = service.moveHistory("editor-test", user, 2, true);
        assertEquals(3L, redone.get("version"));
        assertEquals("Start updated finish", readFirstParagraph(Path.of(job.getDocumentPath())));

        assertThrows(IllegalStateException.class, () -> service.save(
                "editor-test", user, new DocumentEditRequest(0, 0, 6, 14, "selected", "stale")));
    }

    @Test
    void returnsExactlyTwoAiAlternativesForValidSelection() throws Exception {
        when(llmWorkerPool.generateWithRetry(anyString(), any()))
                .thenReturn("{\"alternatives\":[\"first\",\"second\"]}");
        var alternatives = service.transform(
                "editor-test", user, new DocumentTransformRequest(0, 6, 14, "selected", "make clearer"));
        assertEquals(java.util.List.of("first", "second"), alternatives);

        when(llmWorkerPool.generateWithRetry(anyString(), any()))
                .thenReturn("{\"alternatives\":[\"first\",\"second\",\"third\"]}");
        assertThrows(IllegalStateException.class, () -> service.transform(
                "editor-test", user, new DocumentTransformRequest(0, 6, 14, "selected", "make clearer")));
    }

    private String readFirstParagraph(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path))) {
            return document.getParagraphs().get(0).getText();
        }
    }
}
