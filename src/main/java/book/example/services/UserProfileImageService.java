package book.example.services;

import book.example.Entity.AppUser;
import book.example.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserProfileImageService {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final String AVATAR_URL = "/api/auth/avatar";

    private final UserRepository userRepository;
    private final Path storageRoot;

    public UserProfileImageService(
            UserRepository userRepository,
            @Value("${app.storage.root:generated}") String storageRoot) {
        this.userRepository = userRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public String updateAvatar(AppUser authenticatedUser, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Choose a profile image to upload.");
        }
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Profile images must be 5 MB or smaller.");
        }

        BufferedImage image;
        String format;
        try (ImageInputStream input = ImageIO.createImageInputStream(upload.getInputStream())) {
            if (input == null) {
                throw new IllegalArgumentException("The uploaded file is not a supported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Choose a PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw new IllegalArgumentException("Choose a PNG or JPEG image.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("The image dimensions are too large.");
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded profile image.", exception);
        }
        if (image == null) {
            throw new IllegalArgumentException("The uploaded file is not a valid image.");
        }

        String extension = format.equals("png") ? ".png" : ".jpg";
        Path directory = storageRoot.resolve("users").resolve(authenticatedUser.getId().toString()).normalize();
        Path target = directory.resolve("avatar" + extension).normalize();
        if (!target.startsWith(directory)) {
            throw new IllegalStateException("Invalid profile image storage path.");
        }

        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "avatar-", extension);
            try {
                if (!ImageIO.write(image, format.equals("png") ? "png" : "jpeg", temporary.toFile())) {
                    throw new IllegalStateException("The profile image format could not be saved.");
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            Files.deleteIfExists(directory.resolve("avatar" + (extension.equals(".png") ? ".jpg" : ".png")));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save the profile image.", exception);
        }

        AppUser user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalStateException("The account no longer exists."));
        user.setAvatarUrl(AVATAR_URL);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return AVATAR_URL + "?v=" + user.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC);
    }

    public Path resolveAvatar(UUID userId) {
        Path directory = storageRoot.resolve("users").resolve(userId.toString()).normalize();
        for (String extension : new String[]{".png", ".jpg"}) {
            Path image = directory.resolve("avatar" + extension).normalize();
            if (!image.startsWith(directory)) {
                throw new IllegalStateException("Invalid profile image storage path.");
            }
            if (Files.isRegularFile(image)) {
                return image;
            }
        }
        throw new IllegalArgumentException("No profile image has been uploaded.");
    }
}
