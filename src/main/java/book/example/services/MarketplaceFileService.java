package book.example.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MarketplaceFileService {
    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ARCHIVE_EXPANDED_BYTES = 150L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 5000;
    private static final int MAX_SCANNED_TEXT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".json", ".xml", ".html", ".htm", ".js", ".jsx", ".ts", ".tsx",
            ".java", ".py", ".rb", ".go", ".rs", ".php", ".cs", ".cpp", ".h", ".yml", ".yaml",
            ".properties", ".sql", ".sh", ".bat", ".ps1", ".env", ".toml", ".ini", ".gradle",
            ".kt", ".swift", ".vue", ".css", ".scss", ".config");
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".msi", ".com", ".scr",
            ".jar", ".class", ".apk", ".app", ".dmg", ".iso");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|"
                    + "AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|"
                    + "(?:api[_-]?key|client[_-]?secret|access[_-]?token|password)\\s*[:=]\\s*[\"']?[^\\s\"']{12,})");
    private static final Pattern SUSPICIOUS_SCRIPT_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:curl|wget)\\b[^\\r\\n|]{0,300}\\|\\s*(?:sh|bash)\\b|"
                    + "\\bpowershell(?:\\.exe)?\\b[^\\r\\n]{0,200}(?:-enc(?:odedcommand)?\\s|frombase64string)|"
                    + "\\b(?:nc|ncat|netcat)\\b[^\\r\\n]{0,200}\\s-e\\s|"
                    + "\\b(?:chmod\\s+\\+x|base64\\s+-d)\\b[^\\r\\n]{0,200}(?:/dev/tcp|curl|wget))");

    private final Path storageRoot;
    private final Path generatedJobsRoot;

    public MarketplaceFileService(@Value("${app.storage.root:generated}") String storageRoot) {
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        this.storageRoot = root.resolve("marketplace").normalize();
        this.generatedJobsRoot = root.resolve("jobs").normalize();
    }

    public Path storeProject(UUID listingId, MultipartFile upload) {
        byte[] bytes = readUpload(upload, "ZIP");
        inspectProjectArchive(bytes);
        return persist(listingId, "project.zip", bytes);
    }

    public Path storeDocument(UUID listingId, MultipartFile upload) {
        byte[] bytes = readUpload(upload, "PDF or DOCX");
        String extension = inspectDocument(bytes, safeFilename(upload.getOriginalFilename()));
        return persist(listingId, "documentation" + extension, bytes);
    }

    public String validateDocumentUpload(MultipartFile upload) {
        byte[] bytes = readUpload(upload, "PDF or DOCX");
        return inspectDocument(bytes, safeFilename(upload.getOriginalFilename()));
    }

    private String inspectDocument(byte[] bytes, String filename) {
        String extension = extension(filename);
        if (extension.equals(".pdf")) {
            if (!startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("The uploaded PDF does not have a valid PDF signature.");
            }
            inspectPdf(bytes, filename);
        } else if (extension.equals(".docx")) {
            inspectDocx(bytes);
        } else {
            throw new IllegalArgumentException("Documentation must be a PDF or DOCX file.");
        }
        return extension;
    }

    public String safeDocumentExtension(String originalFilename) {
        String extension = extension(safeFilename(originalFilename));
        if (!Set.of(".pdf", ".docx").contains(extension)) {
            throw new IllegalArgumentException("Template documents must be PDF or DOCX.");
        }
        return extension;
    }

    private void inspectPdf(byte[] bytes, String filename) {
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000) {
                throw new IllegalArgumentException("The PDF must contain between 1 and 1,000 pages.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            BoundedTextWriter extractedText = new BoundedTextWriter(MAX_SCANNED_TEXT_BYTES);
            stripper.writeText(pdf, extractedText);
            String text = extractedText.toString();
            scanText(text, filename);
            if (SUSPICIOUS_SCRIPT_PATTERN.matcher(text).find()) {
                throw new IllegalArgumentException("Potentially dangerous script content was found in " + filename + ".");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded PDF is invalid or could not be inspected.", exception);
        }
    }

    public String createDocumentPreview(Path document) {
        String name = document.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            String text;
            if (name.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(document.toFile())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(Math.min(pdf.getNumberOfPages(), 3));
                    text = stripper.getText(pdf);
                }
            } else if (name.endsWith(".docx")) {
                try (XWPFDocument docx = new XWPFDocument(Files.newInputStream(document))) {
                    StringBuilder content = new StringBuilder();
                    for (XWPFParagraph paragraph : docx.getParagraphs()) {
                        appendBounded(content, paragraph.getText());
                    }
                    for (XWPFTable table : docx.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            for (var cell : row.getTableCells()) appendBounded(content, cell.getText());
                        }
                    }
                    text = content.toString();
                }
            } else {
                return "";
            }
            String normalized = text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                    .replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
            return normalized.substring(0, Math.min(1800, normalized.length()));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Unable to generate a safe document preview.", exception);
        }
    }

    public String createProjectPreview(Path archivePath) {
        List<String> filenames = new ArrayList<>();
        try (ZipInputStream archive = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null && filenames.size() < 25) {
                if (!entry.isDirectory()) {
                    String name = entry.getName().replace('\\', '/');
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!lower.endsWith(".env") && !lower.contains("/.env.")
                            && !lower.endsWith(".pem") && !lower.endsWith(".p12")) {
                        filenames.add(name.length() > 140 ? name.substring(0, 140) : name);
                    }
                }
            }
            return filenames.isEmpty() ? "Project archive" : "Project files:\n" + String.join("\n", filenames);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to generate a safe project preview.", exception);
        }
    }

    public Path resolveStoredFile(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("This listing does not include that file.");
        }
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        boolean marketplaceFile = resolved.startsWith(storageRoot);
        boolean generatedDocument = resolved.startsWith(generatedJobsRoot)
                && Set.of("documentation.docx", "documentation.pdf")
                .contains(resolved.getFileName().toString());
        if ((!marketplaceFile && !generatedDocument) || !Files.isRegularFile(resolved)) {
            throw new IllegalStateException("Marketplace file is unavailable.");
        }
        return resolved;
    }

    public void removeListingFiles(UUID listingId) {
        Path directory = storageRoot.resolve(listingId.toString()).normalize();
        if (!directory.startsWith(storageRoot) || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean failed Marketplace uploads.", exception);
        }
    }

    private byte[] readUpload(MultipartFile upload, String description) {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Upload a " + description + " file.");
        }
        if (upload.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Marketplace files must be 20 MB or smaller.");
        }
        try {
            return upload.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded file.", exception);
        }
    }

    private void inspectProjectArchive(byte[] bytes) {
        if (!startsWith(bytes, new byte[]{'P', 'K', 3, 4})
                && !startsWith(bytes, new byte[]{'P', 'K', 5, 6})) {
            throw new IllegalArgumentException("The uploaded project is not a valid ZIP archive.");
        }
        long expandedBytes = 0;
        int entryCount = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = archive.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("The project archive contains too many files.");
                }
                String name = entry.getName().replace('\\', '/');
                Path normalized = Path.of(name).normalize();
                if (name.startsWith("/") || normalized.isAbsolute() || normalized.startsWith("..")
                        || name.indexOf('\0') >= 0) {
                    throw new IllegalArgumentException("The project archive contains an unsafe file path.");
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                String basename = lowerName.substring(lowerName.lastIndexOf('/') + 1);
                String extension = extension(basename);
                if (basename.equals(".env") || basename.startsWith(".env.")
                        || basename.equals("id_rsa") || basename.equals("id_ed25519")
                        || basename.endsWith(".pem") || basename.endsWith(".p12")
                        || basename.endsWith(".pfx")) {
                    throw new IllegalArgumentException("The project archive contains a private credential or key file.");
                }
                if (BLOCKED_EXTENSIONS.contains(extension)) {
                    throw new IllegalArgumentException("The project archive contains an executable file.");
                }
                if (!entry.isDirectory() && (extension.equals(".zip") || extension.equals(".7z")
                        || extension.equals(".rar") || extension.equals(".tar") || extension.equals(".gz"))) {
                    throw new IllegalArgumentException("Nested archives are not accepted.");
                }

                int read;
                int scanned = 0;
                StringBuilder text = new StringBuilder();
                while ((read = archive.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_ARCHIVE_EXPANDED_BYTES) {
                        throw new IllegalArgumentException("The project archive expands beyond the safe size limit.");
                    }
                    if (TEXT_EXTENSIONS.contains(extension) && scanned < MAX_SCANNED_TEXT_BYTES) {
                        int count = Math.min(read, MAX_SCANNED_TEXT_BYTES - scanned);
                        text.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                        scanned += count;
                    }
                }
                if (!text.isEmpty()) {
                    scanText(text.toString(), name);
                }
                archive.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The project archive is invalid or could not be inspected.", exception);
        }
    }

    private void inspectDocx(byte[] bytes) {
        if (!startsWith(bytes, new byte[]{'P', 'K', 3, 4})) {
            throw new IllegalArgumentException("The uploaded DOCX does not have a valid Office document signature.");
        }
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            long expanded = 0;
            boolean documentFound = false;
            int entryCount = 0;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("The DOCX contains too many package files.");
                }
                String name = entry.getName().replace('\\', '/');
                Path normalized = Path.of(name).normalize();
                if (name.startsWith("/") || normalized.isAbsolute() || normalized.startsWith("..")) {
                    throw new IllegalArgumentException("The DOCX contains an unsafe package path.");
                }
                String extension = extension(name.toLowerCase(Locale.ROOT));
                if (BLOCKED_EXTENSIONS.contains(extension) || extension.equals(".zip")
                        || name.startsWith("word/embeddings/") || name.startsWith("word/activeX/")) {
                    throw new IllegalArgumentException("The DOCX contains an embedded executable or archive.");
                }
                boolean documentPart = "word/document.xml".equals(name);
                if (documentPart) documentFound = true;
                StringBuilder xml = extension.equals(".xml") || extension.equals(".rels")
                        ? new StringBuilder() : null;
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_ARCHIVE_EXPANDED_BYTES) {
                        throw new IllegalArgumentException("The DOCX expands beyond the safe size limit.");
                    }
                    if (xml != null && xml.length() + read <= MAX_SCANNED_TEXT_BYTES) {
                        xml.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
                if (xml != null) scanText(xml.toString(), "DOCX document");
                archive.closeEntry();
            }
            if (!documentFound) {
                throw new IllegalArgumentException("The uploaded file is not a readable DOCX document.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The uploaded DOCX is invalid or could not be inspected.", exception);
        }
    }

    private void scanText(String content, String filename) {
        if (SECRET_PATTERN.matcher(content).find()) {
            throw new IllegalArgumentException("Potential credentials were found in " + filename + ".");
        }
        if (SUSPICIOUS_SCRIPT_PATTERN.matcher(content).find()) {
            throw new IllegalArgumentException("Potentially dangerous script content was found in " + filename + ".");
        }
    }

    private Path persist(UUID listingId, String filename, byte[] bytes) {
        Path directory = storageRoot.resolve(listingId.toString()).normalize();
        Path target = directory.resolve(filename).normalize();
        if (!directory.startsWith(storageRoot) || !target.startsWith(directory)) {
            throw new IllegalStateException("Invalid Marketplace storage path.");
        }
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the Marketplace upload.", exception);
        }
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank() || original.length() > 255) {
            throw new IllegalArgumentException("A valid filename is required.");
        }
        String normalized = original.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private void appendBounded(StringBuilder target, String value) {
        if (value == null || value.isBlank() || target.length() >= 12_000) return;
        int remaining = 12_000 - target.length();
        target.append(value, 0, Math.min(remaining, value.length())).append('\n');
    }

    private static final class BoundedTextWriter extends Writer {
        private final int limit;
        private final StringBuilder value = new StringBuilder();

        private BoundedTextWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            int remaining = limit - value.length();
            if (remaining > 0) value.append(chars, offset, Math.min(remaining, length));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
