package book.example.Analyzer;

import book.example.dto.ProjectFacts;
import book.example.dto.RepositoryFile;
import book.example.dto.RepositorySnapshot;
import book.example.services.SourceTextSanitizer;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ProjectAnalyzer {

    public ProjectFacts analyze(RepositorySnapshot snapshot) {

        ProjectFacts facts = new ProjectFacts();

        facts.setProjectName(
                extractProjectName(snapshot.getRepositoryUrl())
        );

        int textFiles = 0;
        int binaryFiles = 0;
        int structuralFiles = 0;

        for (RepositoryFile file : snapshot.getFiles()) {

            String path = file.getPath();

            String lowerPath =
                    path.toLowerCase(Locale.ROOT);

            facts.getAllFiles().add(path);
            if (file.isBinary()) binaryFiles++; else textFiles++;
            if (!file.isBinary() && file.getContent() != null && file.getSha256() != null) structuralFiles++;

            detectTechnology(file, facts);

            detectFramework(file, facts);

            detectDependencyFile(file, facts);

            detectApiFile(file, facts);

            detectDatabaseFile(file, facts);

            detectSecurityFile(file, facts);

            detectConfigurationFile(file, facts);

            detectTestFile(file, facts);

            detectModelFile(file, facts);

            detectDocumentationFile(file, facts);

            detectImageFile(file, facts);

            detectModule(path, facts);
        }

        facts.setAnalyzedFileCount(facts.getAllFiles().size());
        facts.setTextFileCount(textFiles);
        facts.setBinaryFileCount(binaryFiles);
        facts.setStructurallyAnalyzedFileCount(structuralFiles);

        facts.setProjectType(
                determineProjectType(facts)
        );

        facts.setDescription(
                buildInitialDescription(facts)
        );

        return facts;
    }

    private void detectTechnology(
            RepositoryFile file,
            ProjectFacts facts) {

        String type = file.getFileType();

        switch (type) {

            case "JAVA" ->
                    addIfMissing(facts.getTechnologies(), "Java");

            case "PYTHON" ->
                    addIfMissing(facts.getTechnologies(), "Python");

            case "JAVASCRIPT" ->
                    addIfMissing(
                            facts.getTechnologies(),
                            "JavaScript"
                    );

            case "TYPESCRIPT" ->
                    addIfMissing(
                            facts.getTechnologies(),
                            "TypeScript"
                    );

            case "REACT" ->
                    addIfMissing(
                            facts.getTechnologies(),
                            "JavaScript / React"
                    );

            case "REACT_TYPESCRIPT" -> addIfMissing(facts.getTechnologies(), "TypeScript / React");
            case "HTML" -> addIfMissing(facts.getTechnologies(), "HTML");
            case "CSS", "SCSS" -> addIfMissing(facts.getTechnologies(), "CSS");
            case "VUE" -> addIfMissing(facts.getTechnologies(), "Vue.js");
            case "SVELTE" -> addIfMissing(facts.getTechnologies(), "Svelte");
            case "C" -> addIfMissing(facts.getTechnologies(), "C");
            case "CPP", "CPP_HEADER" -> addIfMissing(facts.getTechnologies(), "C++");
            case "C_SHARP" -> addIfMissing(facts.getTechnologies(), "C#");
            case "GO" -> addIfMissing(facts.getTechnologies(), "Go");
            case "RUST" -> addIfMissing(facts.getTechnologies(), "Rust");
            case "KOTLIN" -> addIfMissing(facts.getTechnologies(), "Kotlin");
            case "SWIFT" -> addIfMissing(facts.getTechnologies(), "Swift");
            case "PHP" -> addIfMissing(facts.getTechnologies(), "PHP");
            case "RUBY" -> addIfMissing(facts.getTechnologies(), "Ruby");
            case "DART" -> addIfMissing(facts.getTechnologies(), "Dart");
            case "SHELL", "POWERSHELL", "WINDOWS_SCRIPT" -> addIfMissing(facts.getTechnologies(), "Shell scripting");

            case "SQL" ->
                    addIfMissing(
                            facts.getTechnologies(),
                            "SQL"
                    );

            default -> {
            }
        }
    }

    private void detectFramework(
            RepositoryFile file,
            ProjectFacts facts) {

        String path = file.getPath() == null ? "" : file.getPath().toLowerCase(Locale.ROOT);
        String content = file.getContent();
        if (content == null) return;
        content = SourceTextSanitizer.stripComments(content, file.getFileType());
        String imports = String.join("\n", file.getImports()).toLowerCase(Locale.ROOT);
        String type = file.getFileType() == null ? "" : file.getFileType().toUpperCase(Locale.ROOT);

        // Framework facts must come from an actual dependency/import or a framework-specific file type,
        // never from an arbitrary README/comment containing the framework name.
        if (path.endsWith("pom.xml") && content.matches("(?s).*<artifactId>spring-boot-(starter-|dependencies).*")) {
            addIfMissing(facts.getFrameworks(), "Spring Boot");
        }
        if (imports.contains("org.springframework.boot") || imports.contains("org.springframework.web.bind.annotation")) {
            addIfMissing(facts.getFrameworks(), "Spring Boot");
        }
        if (imports.contains("fastapi")) addIfMissing(facts.getFrameworks(), "FastAPI");
        if (imports.contains("flask")) addIfMissing(facts.getFrameworks(), "Flask");
        if (imports.contains("django")) addIfMissing(facts.getFrameworks(), "Django");
        if (imports.contains("tensorflow") || imports.contains("keras")) addIfMissing(facts.getFrameworks(), "TensorFlow / Keras");
        if (imports.contains("torch") || imports.contains("pytorch")) addIfMissing(facts.getFrameworks(), "PyTorch");
        if (imports.contains("express") || imports.contains("express.js")) addIfMissing(facts.getFrameworks(), "Express.js");
        if ((type.equals("REACT") || type.equals("REACT_TYPESCRIPT"))
                && (imports.contains("from react") || imports.contains("react") || looksLikeReactDependency(path, content))) {
            addIfMissing(facts.getFrameworks(), "React");
        }
    }

    private boolean looksLikeReactDependency(String path, String content) {
        if (path == null || content == null || !path.toLowerCase(Locale.ROOT).endsWith("package.json")) return false;
        return content.matches("(?s).*\"(?:dependencies|devDependencies|peerDependencies)\"\\s*:\\s*\\{[^}]*\"react\"\\s*:");
    }

    private void detectDependencyFile(
            RepositoryFile file,
            ProjectFacts facts) {

        String path =
                file.getPath().toLowerCase(Locale.ROOT);

        if (path.endsWith("pom.xml")
                || path.endsWith("package.json")
                || path.endsWith("requirements.txt")
                || path.endsWith("pyproject.toml")
                || path.endsWith("build.gradle")
                || path.endsWith("build.gradle.kts")) {

            addIfMissing(
                    facts.getDependencies(),
                    file.getPath()
            );
        }
    }

    private void detectApiFile(RepositoryFile file, ProjectFacts facts) {
        String content = file.getContent();
        if (content == null || content.isBlank()) return;
        content = SourceTextSanitizer.stripComments(content, file.getFileType());
        String lower = content.toLowerCase(Locale.ROOT);
        boolean apiEvidence = !file.getConstructs().isEmpty()
                && file.getConstructs().stream().anyMatch(v -> v.startsWith("/") || v.contains("mapping"));
        apiEvidence = apiEvidence
                || lower.contains("@restcontroller")
                || lower.contains("@requestmapping")
                || lower.contains("@getmapping")
                || lower.contains("@postmapping")
                || lower.contains("fastapi()")
                || lower.contains("app.route")
                || lower.contains("router.get")
                || lower.contains("router.post")
                || lower.contains("express()");
        if (apiEvidence) addIfMissing(facts.getApiFiles(), file.getPath());
    }

    private void detectDatabaseFile(RepositoryFile file, ProjectFacts facts) {
        String content = file.getContent();
        if (content == null || content.isBlank()) return;
        content = SourceTextSanitizer.stripComments(content, file.getFileType());
        String lower = content.toLowerCase(Locale.ROOT);
        boolean sql = "SQL".equalsIgnoreCase(file.getFileType());
        boolean dbEvidence = sql
                || lower.contains("jdbc:")
                || lower.contains("spring.datasource")
                || lower.contains("datasource")
                || lower.contains("sqlalchemy")
                || lower.contains("mongoose")
                || lower.contains("mongodb")
                || lower.contains("postgresql")
                || lower.contains("mysql")
                || lower.contains("create table")
                || lower.contains("@entity")
                || lower.contains("@repository")
                || lower.contains("jparepository");
        if (dbEvidence) addIfMissing(facts.getDatabaseFiles(), file.getPath());
    }

    private void detectSecurityFile(RepositoryFile file, ProjectFacts facts) {
        String content = file.getContent();
        if (content == null || content.isBlank()) return;
        content = SourceTextSanitizer.stripComments(content, file.getFileType());
        String lower = content.toLowerCase(Locale.ROOT);
        String imports = String.join(" ", file.getImports()).toLowerCase(Locale.ROOT);
        boolean evidence = lower.contains("@enablewebsecurity")
                || lower.contains("securityfilterchain")
                || lower.contains("securitymiddleware")
                || imports.contains("org.springframework.security")
                || imports.contains("io.jsonwebtoken")
                || imports.contains("jsonwebtoken")
                || lower.contains("jwt.verify(")
                || lower.contains("jwt.sign(")
                || imports.contains("oauth2")
                || imports.contains("passport")
                || imports.contains("bcrypt")
                || imports.contains("argon2");
        if (evidence) addIfMissing(facts.getSecurityFiles(), file.getPath());
    }

    private void detectConfigurationFile(
            RepositoryFile file,
            ProjectFacts facts) {

        String path =
                file.getPath().toLowerCase(Locale.ROOT);

        if (path.contains("application.properties")
                || path.contains("application.yml")
                || path.contains("application.yaml")
                || path.endsWith("config.json")
                || path.endsWith(".env")
                || path.endsWith("dockerfile")
                || path.endsWith("docker-compose.yml")
                || path.endsWith("docker-compose.yaml")) {

            addIfMissing(
                    facts.getConfigurationFiles(),
                    file.getPath()
            );
        }
    }

    private void detectTestFile(
            RepositoryFile file,
            ProjectFacts facts) {

        String path =
                file.getPath().toLowerCase(Locale.ROOT);

        if (path.contains("/test/")
                || path.contains("\\test\\")
                || path.contains("test_")
                || path.contains("_test.")
                || path.contains("tests/")) {

            addIfMissing(
                    facts.getTestFiles(),
                    file.getPath()
            );
        }
    }

    private void detectModelFile(
            RepositoryFile file,
            ProjectFacts facts) {

        if (file.isBinary()) {

            String type =
                    file.getFileType();

            if (type.contains("MODEL")) {

                addIfMissing(
                        facts.getModelFiles(),
                        file.getPath()
                );
            }
        }
    }

    private void detectDocumentationFile(
            RepositoryFile file,
            ProjectFacts facts) {

        String path =
                file.getPath().toLowerCase(Locale.ROOT);

        if (path.endsWith("readme.md")
                || path.contains("/docs/")
                || path.contains("documentation")) {

            addIfMissing(
                    facts.getDocumentationFiles(),
                    file.getPath()
            );
        }
    }

    private void detectImageFile(
            RepositoryFile file,
            ProjectFacts facts) {

        if ("IMAGE".equals(file.getFileType())) {

            addIfMissing(
                    facts.getImageFiles(),
                    file.getPath()
            );
        }
    }

    private void detectModule(
            String path,
            ProjectFacts facts) {

        String[] parts =
                path.split("/");

        if (parts.length >= 2) {

            String firstFolder = parts[0];

            if (!firstFolder.equals(".git")) {

                addIfMissing(
                        facts.getModules(),
                        firstFolder
                );
            }
        }
    }

    private String determineProjectType(
            ProjectFacts facts) {

        if (facts.getFrameworks().contains("Spring Boot")) {
            return "JAVA_BACKEND";
        }

        if (facts.getFrameworks().contains("FastAPI")
                || facts.getFrameworks().contains("Flask")
                || facts.getFrameworks().contains("Django")) {

            return "PYTHON_BACKEND";
        }

        if (facts.getFrameworks().contains("React")) {
            return "WEB_APPLICATION";
        }

        if (facts.getFrameworks().contains("TensorFlow / Keras")
                || facts.getFrameworks().contains("PyTorch")) {

            return "AI_ML";
        }

        if (facts.getTechnologies().contains("Java")) {
            return "JAVA_PROJECT";
        }

        if (facts.getTechnologies().contains("Python")) {
            return "PYTHON_PROJECT";
        }

        if (facts.getTechnologies().contains("JavaScript")
                || facts.getTechnologies().contains("TypeScript")) {

            return "JAVASCRIPT_TYPESCRIPT_PROJECT";
        }

        return "GENERAL_SOFTWARE_PROJECT";
    }

    private String buildInitialDescription(
            ProjectFacts facts) {

        return "Detected project type: "
                + facts.getProjectType()
                + ". Technologies: "
                + String.join(
                ", ",
                facts.getTechnologies()
        )
                + ". Frameworks: "
                + String.join(
                ", ",
                facts.getFrameworks()
        )
                + ".";
    }

    private String extractProjectName(
            String repositoryUrl) {

        if (repositoryUrl == null) {
            return "Unknown Project";
        }

        String cleanUrl =
                repositoryUrl.endsWith("/")
                        ? repositoryUrl.substring(
                        0,
                        repositoryUrl.length() - 1
                )
                        : repositoryUrl;

        int lastSlash =
                cleanUrl.lastIndexOf("/");

        if (lastSlash >= 0
                && lastSlash < cleanUrl.length() - 1) {

            return cleanUrl.substring(
                    lastSlash + 1
            );
        }

        return "Unknown Project";
    }

    private void addIfMissing(
            java.util.List<String> list,
            String value) {

        if (!list.contains(value)) {
            list.add(value);
        }
    }
}
