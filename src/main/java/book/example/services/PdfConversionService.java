package book.example.services;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PdfConversionService {

        private static final Logger logger = LoggerFactory.getLogger(PdfConversionService.class);
    private static final long TIMEOUT_SECONDS = 120;
    private static final String DEFAULT_LIBREOFFICE_WINDOWS_PATH =
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe";

    static String resolveLibreOfficeExecutable(
            String osName,
            Map<String, String> env
    ) {
        if (env != null) {
            String configuredPath = env.get("LIBREOFFICE_PATH");
            if (configuredPath != null && !configuredPath.isBlank()) {
                return configuredPath;
            }
        }

        if (osName != null && osName.toLowerCase().contains("win")) {
            if (Files.exists(Path.of(DEFAULT_LIBREOFFICE_WINDOWS_PATH))) {
                return DEFAULT_LIBREOFFICE_WINDOWS_PATH;
            }
        }

        return "soffice";
    }

    public Path convertToPdf(Path docxPath) {
        if (docxPath == null || !Files.exists(docxPath)) {
            throw new IllegalArgumentException("DOCX file does not exist: " + docxPath);
        }

        if (!docxPath.toString().toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Only DOCX files are supported");
        }

        Path outputDirectory = docxPath.getParent();
        if (outputDirectory == null) {
            throw new IllegalStateException("DOCX output directory could not be determined");
        }

        Path outputLog = null;
        Path profileDirectory = null;
        try {
            outputLog = Files.createTempFile(outputDirectory, "libreoffice-conversion-", ".log");
            profileDirectory = Files.createTempDirectory(outputDirectory, "libreoffice-profile-");
            String isolatedProfile = "-env:UserInstallation=" + profileDirectory.toUri();
            String libreOfficeExecutable = resolveLibreOfficeExecutable(
                    System.getProperty("os.name"),
                    System.getenv()
            );

            ProcessBuilder processBuilder = new ProcessBuilder(
                    libreOfficeExecutable,
                    "--headless",
                    isolatedProfile,
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    outputDirectory.toAbsolutePath().toString(),
                    docxPath.toAbsolutePath().toString()
            );
            processBuilder.redirectErrorStream(true)
                    .redirectOutput(outputLog.toFile());

            Process process = processBuilder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                throw new IllegalStateException(
                        "PDF conversion timed out after " + TIMEOUT_SECONDS
                                + " seconds. LibreOffice output: " + Files.readString(outputLog)
                );
            }

            String consoleOutput = Files.readString(outputLog);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "PDF conversion failed. LibreOffice output: " + consoleOutput
                );
            }

            String docxFileName = docxPath.getFileName().toString();
            String pdfFileName = docxFileName.substring(0, docxFileName.lastIndexOf('.')) + ".pdf";
            Path pdfPath = outputDirectory.resolve(pdfFileName);

            if (!Files.exists(pdfPath)) {
                throw new IllegalStateException(
                        "PDF conversion completed but PDF file was not created: " + pdfPath
                );
            }
            if (Files.size(pdfPath) == 0) {
                throw new IllegalStateException("Generated PDF is empty: " + pdfPath);
            }

            return pdfPath;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to start LibreOffice for PDF conversion. Check the LibreOffice installation "
                            + "or set LIBREOFFICE_PATH.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PDF conversion was interrupted", e);
        } finally {
            if (outputLog != null) {
                try {
                    Files.deleteIfExists(outputLog);
                } catch (IOException cleanupException) {
                    logger.warn("Unable to remove LibreOffice conversion log {}", outputLog, cleanupException);
                }
            }
            if (profileDirectory != null) {
                try (var paths = Files.walk(profileDirectory)) {
                    paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
                } catch (IOException cleanupException) {
                    logger.warn("Unable to remove LibreOffice profile {}", profileDirectory, cleanupException);
                }
            }
        }
    }
}