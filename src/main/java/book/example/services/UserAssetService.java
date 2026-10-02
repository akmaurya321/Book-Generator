package book.example.services;

import book.example.dto.UploadedAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UserAssetService {
    private final String storageRoot;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public UserAssetService(@Value("${app.storage.root:generated}") String storageRoot) { this.storageRoot = storageRoot; }

    public UploadedAsset storeImage(String jobId, String sectionId, MultipartFile file, String caption) {
        if (jobId == null || jobId.isBlank() || sectionId == null || sectionId.isBlank()) throw new IllegalArgumentException("Job and section are required");
        if (sectionId.length() > 128) throw new IllegalArgumentException("Section identifier is too long.");
        if (caption != null && caption.length() > 500) throw new IllegalArgumentException("Image caption is too long.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Image is required");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Image must be 10 MB or smaller.");
        try {
            if (ImageIO.read(new ByteArrayInputStream(file.getBytes())) == null) {
                throw new IllegalArgumentException("The uploaded file is not a valid image.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to validate the uploaded image.", e);
        }
        String ext = extension(file.getOriginalFilename());
        Path dir = Path.of(storageRoot, "jobs", jobId, "assets");
        try {
            Files.createDirectories(dir);
            AtomicInteger counter = counters.computeIfAbsent(jobId, k -> new AtomicInteger(discoverNextAssetNumber(dir)));
            int number;
            String id;
            Path path;
            do {
                number = counter.incrementAndGet();
                id = "IMG-%03d".formatted(number);
                path = dir.resolve(id + ext);
            } while (Files.exists(path));
            Files.write(path, file.getBytes());
            UploadedAsset asset = new UploadedAsset(id, jobId, sectionId, file.getOriginalFilename(), file.getContentType(), path.toString(), caption);
            return asset;
        } catch (IOException e) { throw new IllegalStateException("Unable to store uploaded image", e); }
    }

    public Path resolve(String jobId, String assetId) {
        if (jobId == null || jobId.isBlank() || assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Job and asset are required");
        }
        Path directory = Path.of(storageRoot, "jobs", jobId, "assets");
        if (Files.isDirectory(directory)) {
            try (var stream = Files.list(directory)) {
                var path = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith(assetId + "."))
                        .findFirst().orElse(null);
                if (path != null) return path;
            } catch (IOException ignored) {}
        }
        throw new IllegalArgumentException("Unknown or unavailable asset: " + assetId);
    }
    private int discoverNextAssetNumber(Path directory) {
        int max = 0;
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("IMG-")) continue;
                int dot = name.indexOf('.');
                String number = dot > 4 ? name.substring(4, dot) : name.substring(4);
                try { max = Math.max(max, Integer.parseInt(number)); } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
        return max;
    }

    private String extension(String filename) {
        if (filename == null || filename.lastIndexOf('.') < 0) return ".bin";
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg)") ? ext : ".bin";
    }
}
