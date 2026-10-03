package book.example.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketplaceFileServiceTest {
    @TempDir
    Path storage;

    @Test
    void storesValidProjectArchivePrivately() throws Exception {
        MarketplaceFileService service = new MarketplaceFileService(storage.toString());
        UUID listingId = UUID.randomUUID();
        MockMultipartFile project = new MockMultipartFile(
                "project", "project.zip", "application/zip", zip("README.md", "A project".getBytes()));

        Path saved = service.storeProject(listingId, project);

        assertThat(saved).exists().startsWith(storage.resolve("marketplace"));
        assertThat(saved.getFileName()).hasToString("project.zip");
    }

    @Test
    void rejectsCredentialFilesInProjectArchives() throws Exception {
        MarketplaceFileService service = new MarketplaceFileService(storage.toString());
        MockMultipartFile project = new MockMultipartFile(
                "project", "project.zip", "application/zip", zip(".env", "API_KEY=not-a-real-secret".getBytes()));

        assertThatThrownBy(() -> service.storeProject(UUID.randomUUID(), project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential");
    }

    @Test
    void rejectsTraversalAndNestedArchives() throws Exception {
        MarketplaceFileService service = new MarketplaceFileService(storage.toString());
        MockMultipartFile traversal = new MockMultipartFile(
                "project", "project.zip", "application/zip", zip("../outside.txt", "no".getBytes()));

        assertThatThrownBy(() -> service.storeProject(UUID.randomUUID(), traversal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe file path");

        MockMultipartFile nested = new MockMultipartFile(
                "project", "project.zip", "application/zip", zip("vendor.zip", new byte[]{1, 2, 3}));
        assertThatThrownBy(() -> service.storeProject(UUID.randomUUID(), nested))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested archives");
    }

    @Test
    void rejectsInvalidAndOversizedUploads() {
        MarketplaceFileService service = new MarketplaceFileService(storage.toString());
        MockMultipartFile invalid = new MockMultipartFile(
                "document", "notes.pdf", "application/pdf", "not a pdf".getBytes());
        assertThatThrownBy(() -> service.storeDocument(UUID.randomUUID(), invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");

        MockMultipartFile oversized = new MockMultipartFile(
                "document", "too-large.pdf", "application/pdf", new byte[(int) MarketplaceFileService.MAX_FILE_BYTES + 1]);
        assertThatThrownBy(() -> service.storeDocument(UUID.randomUUID(), oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20 MB");
    }

    private byte[] zip(String name, byte[] content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
