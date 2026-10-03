package book.example.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class TemplateFileStorageService {
    private final Path root;
    private final MarketplaceFileService securityScanner;

    public TemplateFileStorageService(
            @Value("${app.storage.root:generated}") String storageRoot,
            MarketplaceFileService securityScanner) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize().resolve("template-library").normalize();
        this.securityScanner = securityScanner;
    }

    public String storeTemplate(UUID catalogId, int version, MultipartFile upload) {
        String extension = securityScanner.validateDocumentUpload(upload);
        return store(upload, catalogId, version, "template" + extension, extension);
    }

    public FormatAnalyzer.Analysis analyzePrivate(MultipartFile upload, FormatAnalyzer analyzer) {
        String extension = securityScanner.validateDocumentUpload(upload);
        Path temporaryFile = null;
        try {
            Files.createDirectories(root);
            temporaryFile = Files.createTempFile(root, "private-format-", extension);
            upload.transferTo(temporaryFile);
            return analyzer.analyze(temporaryFile);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the private template format.", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to remove the temporary private template file.", exception);
                }
            }
        }
    }

    public String storePreview(UUID catalogId, int version, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) return null;
        if (upload.getSize() > 8L * 1024 * 1024) {
            throw new IllegalArgumentException("Template preview must be 8 MB or smaller.");
        }
        String name = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension;
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the template preview.", exception);
        }
        if (name.endsWith(".png") && signature(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            extension = ".png";
        } else if ((name.endsWith(".jpg") || name.endsWith(".jpeg"))
                && bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
            extension = ".jpg";
        } else if (name.endsWith(".pdf") && signature(bytes, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            extension = ".pdf";
        } else {
            throw new IllegalArgumentException("Preview must be a valid PNG, JPEG, or PDF file.");
        }
        return persist(catalogId, version, "preview" + extension, bytes);
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Template asset is unavailable.");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Template asset is unavailable.");
        }
        return path;
    }

    public Path resolveKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("Template asset key is required.");
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid template asset key.");
        return path;
    }

    public void removeVersion(UUID catalogId, int version) {
        Path directory = root.resolve(catalogId.toString()).resolve("v" + version).normalize();
        if (!directory.startsWith(root) || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to remove failed template files.", exception);
        }
    }

    private String store(MultipartFile upload, UUID catalogId, int version, String filename, String extension) {
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded template.", exception);
        }
        String key = catalogId + "/v" + version + "/" + filename;
        persist(catalogId, version, filename, bytes);
        return key;
    }

    private String persist(UUID catalogId, int version, String filename, byte[] bytes) {
        Path destination = root.resolve(catalogId.toString()).resolve("v" + version).resolve(filename).normalize();
        if (!destination.startsWith(root)) throw new IllegalArgumentException("Invalid template asset path.");
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "template-", ".upload");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return root.relativize(destination).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the template asset.", exception);
        }
    }

    private boolean signature(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }
}
