package book.example.services;

import book.example.dto.RepositoryFile;
import book.example.dto.RepositorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

@Service
public class GitHubService {

        private final String githubToken;
        private final long cloneTimeoutSeconds;
        private final RepositoryCodeStructureAnalyzer codeStructureAnalyzer;

        public GitHubService(
                        @Value("${github.token:}") String githubToken,
                        @Value("${github.clone-timeout-seconds:300}") long cloneTimeoutSeconds,
                        RepositoryCodeStructureAnalyzer codeStructureAnalyzer) {
                this.githubToken = githubToken == null ? "" : githubToken.trim();
                this.cloneTimeoutSeconds = Math.max(30, cloneTimeoutSeconds);
                this.codeStructureAnalyzer = codeStructureAnalyzer;
        }

    /*
     * These limits protect the application from extremely large repositories.
     * They do NOT decide which files belong to the project.
     */
    private static final int MAX_FILES = 20000;

    private static final long MAX_TEXT_FILE_SIZE = 10_000_000;

    private static final long MAX_REPOSITORY_SCAN_SIZE = 1_000_000_000;

    private static final long MAX_ZIP_ENTRY_SIZE = 25_000_000;

    static Map<String, String> buildGitEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GCM_INTERACTIVE", "NEVER");
        return environment;
    }

    public RepositorySnapshot cloneRepository(String githubUrl) {

        validateGitHubUrl(githubUrl);

        Path tempDirectory = null;

        try {

            tempDirectory = Files.createTempDirectory(
                    "documentation-repo-" + UUID.randomUUID()
            );

            Path repositoryDirectory =
                    tempDirectory.resolve("repository");

            List<String> command = new ArrayList<>(List.of(
                    "git", "clone", "--depth", "1", githubUrl, repositoryDirectory.toString()));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(buildGitEnvironment());
            if (!githubToken.isBlank()) {
                String credentials = Base64.getEncoder().encodeToString(
                        ("x-access-token:" + githubToken).getBytes(StandardCharsets.UTF_8));
                // Keep the token out of the process command line.
                processBuilder.environment().put("GIT_CONFIG_COUNT", "1");
                processBuilder.environment().put("GIT_CONFIG_KEY_0", "http.extraHeader");
                processBuilder.environment().put("GIT_CONFIG_VALUE_0", "Authorization: Basic " + credentials);
            }
            processBuilder.redirectErrorStream(true);
            Path gitLog = tempDirectory.resolve("git-clone.log");
            processBuilder.redirectOutput(gitLog.toFile());

            Process process = processBuilder.start();

            boolean finished = process.waitFor(cloneTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "GitHub clone timed out after " + cloneTimeoutSeconds + " seconds.");
            }

            String output = Files.exists(gitLog)
                    ? Files.readString(gitLog, StandardCharsets.UTF_8)
                    : "";
            int exitCode = process.exitValue();

            if (exitCode != 0) {

                throw new IllegalStateException(
                        "Failed to clone GitHub repository. Git output: "
                                + output
                );
            }

            List<RepositoryFile> files =
                    scanRepository(repositoryDirectory);

            return new RepositorySnapshot(
                    githubUrl,
                    files
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Unable to access GitHub repository. "
                            + "Make sure Git is installed and the repository is accessible.",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "GitHub repository operation was interrupted.",
                    e
            );
        } finally {
            deleteTemporaryDirectory(tempDirectory);
        }
    }

    /**
     * Scan a ZIP upload using the same repository scanner as GitHub sources.
     * Entries are normalized and rejected when they escape the temporary root.
     */
    public RepositorySnapshot scanZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("A non-empty ZIP project is required.");
        }
        String filename = zipFile.getOriginalFilename() == null
                ? ""
                : zipFile.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP project uploads are supported.");
        }

        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("documentation-zip-" + UUID.randomUUID());
            Path repositoryDirectory = tempDirectory.resolve("repository").normalize();
            Files.createDirectories(repositoryDirectory);

            int entryCount = 0;
            long extractedBytes = 0;
            Set<String> extractedPaths = new HashSet<>();
            try (ZipInputStream input = new ZipInputStream(zipFile.getInputStream())) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (++entryCount > MAX_FILES * 2) {
                        throw new IllegalArgumentException("ZIP contains too many entries.");
                    }
                    Path target = repositoryDirectory.resolve(entry.getName()).normalize();
                    String normalizedEntry = repositoryDirectory.relativize(target).toString().replace("\\", "/");
                    if (!extractedPaths.add(normalizedEntry)) {
                        throw new IllegalArgumentException("ZIP contains duplicate entry: " + normalizedEntry);
                    }
                    if (!target.startsWith(repositoryDirectory)) {
                        throw new IllegalArgumentException("ZIP contains an unsafe path.");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    long entryBytes = 0;
                    byte[] buffer = new byte[8192];
                    try (var output = Files.newOutputStream(target)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            entryBytes += read;
                            extractedBytes += read;
                            if (entryBytes > MAX_ZIP_ENTRY_SIZE || extractedBytes > MAX_REPOSITORY_SCAN_SIZE) {
                                throw new IllegalArgumentException("ZIP project exceeds the safe extraction size limit.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
            }

            return new RepositorySnapshot("zip-upload://" + safeFilename(zipFile.getOriginalFilename()),
                    scanRepository(repositoryDirectory));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the ZIP project upload.", exception);
        } finally {
            deleteTemporaryDirectory(tempDirectory);
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null) return;
        try {
            if (!Files.exists(directory)) return;
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        } catch (IOException ignored) {
            // Cleanup must never turn a successful scan into a failed job.
        }
    }

    /**
     * Scans ALL relevant repository files.
     *
     * Text/source files:
     *     content is read.
     *
     * Binary files:
     *     only metadata is stored.
     */
    private List<RepositoryFile> scanRepository(Path repositoryDirectory) throws IOException {
        List<RepositoryFile> files = new ArrayList<>();
        long[] scannedSize = {0L};

        Files.walkFileTree(repositoryDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory,
                                                      java.nio.file.attribute.BasicFileAttributes attributes) {
                if (!directory.equals(repositoryDirectory) && shouldIgnore(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path,
                                             java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                // Git repositories can contain symlinks. Never follow one while
                // scanning evidence, otherwise a repository could make the scanner
                // read files outside the extracted/checked-out repository root.
                if (attributes.isSymbolicLink() || !Files.isRegularFile(path)) {
                    return FileVisitResult.CONTINUE;
                }
                if (isSensitivePath(repositoryDirectory.relativize(path))) {
                    return FileVisitResult.CONTINUE;
                }
                if (files.size() >= MAX_FILES) {
                    throw new IllegalStateException("Repository contains more than " + MAX_FILES
                            + " relevant files. Please submit a smaller repository or exclude generated/vendor files.");
                }

                long fileSize = Files.size(path);
                scannedSize[0] += fileSize;
                if (scannedSize[0] > MAX_REPOSITORY_SCAN_SIZE) {
                    throw new IllegalStateException("Repository scan exceeds the safe 1 GB limit before all files could be inspected.");
                }

                String relativePath = repositoryDirectory.relativize(path).toString().replace("\\", "/");
                String fileType = determineFileType(path);
                boolean binary = isBinaryFile(path);

                if (binary) {
                    files.add(new RepositoryFile(relativePath, null, fileType, fileSize, true));
                    return FileVisitResult.CONTINUE;
                }

                if (fileSize > MAX_TEXT_FILE_SIZE) {
                    throw new IllegalStateException("Repository contains a text/source file larger than "
                            + MAX_TEXT_FILE_SIZE + " bytes: " + relativePath
                            + ". Reduce generated/data files or exclude them before documentation generation.");
                }

                String content = readTextFile(path);
                content = redactSensitiveConfiguration(content, fileType);
                if (isGitLfsPointer(content)) {
                    throw new IllegalStateException("Repository contains a Git LFS pointer instead of the referenced file content: "
                            + relativePath + ". Install/configure Git LFS or provide a repository snapshot with the actual file content.");
                }
                files.add(new RepositoryFile(relativePath, content, fileType, fileSize, false));
                return FileVisitResult.CONTINUE;
            }
        });

        for (RepositoryFile file : files) {
            codeStructureAnalyzer.analyze(file);
        }
        return files;
    }


    private String redactSensitiveConfiguration(String content, String fileType) {
        if (content == null || content.isBlank()) return content;
        if (!("PROPERTIES".equals(fileType) || "YAML".equals(fileType) || "JSON".equals(fileType) || "XML".equals(fileType))) {
            return content;
        }
        String key = "(?i)(password|passwd|secret|api[_-]?key|token|access[_-]?token|client[_-]?secret|private[_-]?key|authorization)";
        String redacted = content.replaceAll("(?m)(\\b" + key + "\\b\\s*[=:]\\s*)([^\\r\\n,}]+)", "$1[REDACTED]");
        return redacted.replaceAll("(?i)(\"(?:password|passwd|secret|api[_-]?key|token|access[_-]?token|client[_-]?secret|private[_-]?key|authorization)\"\s*:\s*\")[^\"]*(\")", "$1[REDACTED]$2");
    }

    private boolean isSensitivePath(Path relativePath) {
        String normalized = relativePath.toString().replace("\\", "/").toLowerCase();
        String fileName = relativePath.getFileName() == null ? "" : relativePath.getFileName().toString().toLowerCase();
        if (normalized.startsWith("secrets/") || normalized.contains("/secrets/")) return true;
        if (normalized.startsWith("credentials/") || normalized.contains("/credentials/")) return true;
        if (fileName.equals(".env") || fileName.startsWith(".env.")) return true;
        if (fileName.equals(".npmrc") || fileName.equals(".pypirc")) return true;
        if (fileName.equals("credentials.json") || fileName.equals("secrets.json")) return true;
        return fileName.endsWith(".pem") || fileName.endsWith(".key") || fileName.endsWith(".p12") || fileName.endsWith(".pfx");
    }

    /**
     * Files/folders that should not be considered
     * part of the meaningful project source.
     */
    private boolean shouldIgnore(Path path) {

        String normalized =
                path.toString()
                        .replace("\\", "/")
                        .toLowerCase();

        return normalized.contains("/.git/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/target/")
                || normalized.contains("/build/")
                || normalized.contains("/dist/")
                || normalized.contains("/.venv/")
                || normalized.contains("/venv/")
                || normalized.contains("/__pycache__/")
                || normalized.contains("/.idea/")
                || normalized.contains("/.vscode/")
                || normalized.contains("/coverage/")
                || normalized.contains("/.next/")
                || normalized.contains("/bin/")
                || normalized.contains("/obj/");
    }

    /**
     * Determines the logical type of a file.
     */
    private String determineFileType(Path path) {

        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        if (fileName.endsWith(".java")) return "JAVA";
        if (fileName.endsWith(".py")) return "PYTHON";
        if (fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs")) return "JAVASCRIPT";
        if (fileName.endsWith(".jsx")) return "REACT";
        if (fileName.endsWith(".ts")) return "TYPESCRIPT";
        if (fileName.endsWith(".tsx")) return "REACT_TYPESCRIPT";
        if (fileName.endsWith(".vue")) return "VUE";
        if (fileName.endsWith(".svelte")) return "SVELTE";
        if (fileName.endsWith(".c")) return "C";
        if (fileName.endsWith(".h")) return "C_HEADER";
        if (fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx")) return "CPP";
        if (fileName.endsWith(".hpp")) return "CPP_HEADER";
        if (fileName.endsWith(".cs")) return "C_SHARP";
        if (fileName.endsWith(".go")) return "GO";
        if (fileName.endsWith(".rs")) return "RUST";
        if (fileName.endsWith(".kt") || fileName.endsWith(".kts")) return "KOTLIN";
        if (fileName.endsWith(".swift")) return "SWIFT";
        if (fileName.endsWith(".php")) return "PHP";
        if (fileName.endsWith(".rb")) return "RUBY";
        if (fileName.endsWith(".dart")) return "DART";
        if (fileName.endsWith(".sh")) return "SHELL";
        if (fileName.endsWith(".ps1")) return "POWERSHELL";
        if (fileName.endsWith(".bat") || fileName.endsWith(".cmd")) return "WINDOWS_SCRIPT";

        if (fileName.endsWith(".json")) return "JSON";
        if (fileName.endsWith(".xml")) return "XML";
        if (fileName.endsWith(".yml")) return "YAML";
        if (fileName.endsWith(".yaml")) return "YAML";
        if (fileName.endsWith(".properties")) return "PROPERTIES";

        if (fileName.endsWith(".md")) return "MARKDOWN";
        if (fileName.endsWith(".txt")) return "TEXT";

        if (fileName.endsWith(".sql")) return "SQL";

        if (fileName.endsWith(".html")) return "HTML";
        if (fileName.endsWith(".css")) return "CSS";
        if (fileName.endsWith(".scss")) return "SCSS";
        if (fileName.endsWith(".less")) return "LESS";

        if (fileName.endsWith(".csv")) return "CSV";

        if (fileName.endsWith(".pkl")) return "PYTHON_MODEL";
        if (fileName.endsWith(".pickle")) return "PYTHON_MODEL";

        if (fileName.endsWith(".keras")) return "KERAS_MODEL";
        if (fileName.endsWith(".h5")) return "HDF5_MODEL";
        if (fileName.endsWith(".pt")) return "PYTORCH_MODEL";
        if (fileName.endsWith(".pth")) return "PYTORCH_MODEL";

        if (fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".webp")) {

            return "IMAGE";
        }

        if (fileName.endsWith(".mp4")
                || fileName.endsWith(".avi")
                || fileName.endsWith(".mov")) {

            return "VIDEO";
        }

        if (fileName.equals("pom.xml")) return "MAVEN";
        if (fileName.equals("dockerfile")) return "DOCKERFILE";
        if (fileName.equals("requirements.txt")) return "PYTHON_DEPENDENCIES";
        if (fileName.equals("package.json")) return "NODE_DEPENDENCIES";

        return "OTHER";
    }

    /**
     * Detects files that should not have their raw bytes
     * passed into the LLM/RAG pipeline.
     */
    private boolean isBinaryFile(Path path) {

        String fileName =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        /*
         * Known binary/model/media formats.
         */
        if (fileName.endsWith(".pkl")
                || fileName.endsWith(".pickle")
                || fileName.endsWith(".keras")
                || fileName.endsWith(".h5")
                || fileName.endsWith(".pt")
                || fileName.endsWith(".pth")
                || fileName.endsWith(".bin")
                || fileName.endsWith(".onnx")
                || fileName.endsWith(".tflite")
                || fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".mp4")
                || fileName.endsWith(".avi")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".zip")
                || fileName.endsWith(".jar")
                || fileName.endsWith(".exe")
                || fileName.endsWith(".dll")) {

            return true;
        }

        /*
         * For unknown extensions, inspect the first part
         * of the file for binary/null bytes.
         */
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = new byte[4096];
            int read = input.read(bytes);
            if (read <= 0) return false;
            for (int i = 0; i < read; i++) {
                if (bytes[i] == 0) return true;
            }
            return false;
        } catch (IOException e) {

            /*
             * If we cannot safely inspect it,
             * treat it as binary.
             */
            return true;
        }
    }

    private void validateGitHubUrl(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) {
            throw new IllegalArgumentException("GitHub URL is required.");
        }
        if (githubUrl.length() > 2048) {
            throw new IllegalArgumentException("GitHub URL is too long.");
        }
        try {
            URI uri = new URI(githubUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("Only canonical GitHub HTTPS repository URLs are supported.");
            }
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            String[] parts = path.split("/");
            if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException("GitHub URL must point to a repository: https://github.com/<owner>/<repository>");
            }
            String repository = parts[2];
            if (repository.endsWith(".git")) repository = repository.substring(0, repository.length() - 4);
            if (repository.isBlank() || repository.contains("..")) {
                throw new IllegalArgumentException("Invalid GitHub repository path.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid GitHub URL.", exception);
        }
    }

    private String readTextFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("Text file is not valid UTF-8 and has no supported BOM: " + path, exception);
        }
    }

    private boolean isGitLfsPointer(String content) {
        if (content == null) return false;
        String normalized = content.replace("\r\n", "\n");
        return normalized.startsWith("version https://git-lfs.github.com/spec/v1\n")
                && normalized.contains("\noid sha256:")
                && normalized.contains("\nsize ");
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "project.zip";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}