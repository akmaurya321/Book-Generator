package book.example.services;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfConversionServiceTest {

    private static final Path WINDOWS_LIBREOFFICE = Path.of(
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe"
    );

    @TempDir
    Path tempDirectory;

    @Test
    void convertsDocxToNonEmptyPdfWhenLibreOfficeIsInstalled() throws Exception {
        String configuredPath = System.getenv("LIBREOFFICE_PATH");
        boolean installed = configuredPath != null && !configuredPath.isBlank()
                ? Files.exists(Path.of(configuredPath))
                : Files.exists(WINDOWS_LIBREOFFICE) || commandExists("soffice");
        assumeTrue(installed, "LibreOffice is not installed; skipping PDF conversion integration test");

        Path docxPath = tempDirectory.resolve("conversion-check.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(docxPath)) {
            document.createParagraph().createRun().setText("PDF conversion smoke test");
            document.write(output);
        }

        Path pdfPath = new PdfConversionService().convertToPdf(docxPath);

        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);
    }

    private boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("where.exe", command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
