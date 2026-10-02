package book.example.services;

import book.example.dto.RepositoryFile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, content-first polyglot source parser.
 * It never calls an LLM and never treats a filename as proof of a technology.
 * The parser intentionally records only constructs that are actually present
 * in file content. It is language-aware lexical/structural analysis rather
 * than a compiler; the raw source remains the authoritative evidence.
 */
@Service
public class RepositoryCodeStructureAnalyzer {
    private static final Pattern JAVA_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:\\.\\*)?)\\s*;?");
    private static final Pattern JAVA_TYPE = Pattern.compile("\\b(?:public|protected|private|abstract|final|static|sealed|non-sealed|record|class|interface|enum)*\\s*(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JAVA_METHOD = Pattern.compile("(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp|\\s)*\\s*(?:<[^>]+>\\s+)?(?:[A-Za-z_$][\\w$<>?,.\\[\\]]*|void)\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;{}\\n]*\\)\\s*(?:throws[^\\{]+)?\\{");
    private static final Pattern PY_IMPORT = Pattern.compile("(?m)^\\s*(?:from\\s+([\\w.]+)\\s+import|import\\s+([\\w., *]+))");
    private static final Pattern PY_DEF = Pattern.compile("(?m)^\\s*(?:async\\s+)?def\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern PY_CLASS = Pattern.compile("(?m)^\\s*class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JS_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:.+?\\s+from\\s+)?['\"]([^'\"]+)['\"]|^\\s*const\\s+.+?\\s*=\\s*require\\(['\"]([^'\"]+)['\"]\\)");
    private static final Pattern JS_FUNCTION = Pattern.compile("(?m)\\bfunction\\s+([A-Za-z_$][\\w$]*)\\s*\\(|\\b(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>");
    private static final Pattern JS_CLASS = Pattern.compile("(?m)\\bclass\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern ANNOTATION = Pattern.compile("(?m)^\\s*@([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*([a-z][a-z0-9:-]*)\\b");
    private static final Pattern CSS_SELECTOR = Pattern.compile("(?m)([^{}\\n]+)\\{");
    private static final Pattern GO_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:\\((.*?)\\)|\"([^\"]+)\")", Pattern.DOTALL);
    private static final Pattern GO_DECL = Pattern.compile("(?m)^\\s*(?:type|func)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern RUST_USE = Pattern.compile("(?m)^\\s*use\\s+([^;]+);");
    private static final Pattern RUST_DECL = Pattern.compile("(?m)^\\s*(?:pub\\s+)?(?:fn|struct|enum|trait|mod)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern C_DECL = Pattern.compile("(?m)^\\s*(?:class|struct|interface|enum|namespace)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern PHP_DECL = Pattern.compile("(?m)\\b(?:class|interface|trait|function)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SWIFT_DECL = Pattern.compile("(?m)^\\s*(?:class|struct|enum|protocol|func)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern KOTLIN_DECL = Pattern.compile("(?m)^\\s*(?:class|object|interface|fun)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ANCHOR = Pattern.compile("(?i)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)");
    private static final Pattern ROUTE = Pattern.compile("(?i)(?:@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(\\s*[\"']([^\"']+)|(?:app|router)\\.(?:get|post|put|patch|delete|use)\\s*\\(\\s*[\"']([^\"']+)|(?:@app\\.(?:get|post|put|patch|delete))\\s*\\(\\s*[\"']([^\"']+))");

    public void analyze(RepositoryFile file) {
        if (file == null) return;
        if (file.isBinary() || file.getContent() == null) return;
        String rawContent = file.getContent();
        String content = SourceTextSanitizer.stripComments(rawContent, file.getFileType());
        file.setSha256(sha256(rawContent));
        file.setLineCount(rawContent.isEmpty() ? 0 : rawContent.split("\\R", -1).length);
        file.setImports(parseImports(file.getFileType(), content));
        file.setAnnotations(unique(ANNOTATION, content, 1));
        file.setSymbols(parseSymbols(file.getFileType(), content));
        file.setConstructs(parseConstructs(file.getFileType(), content));
    }

    private List<String> parseImports(String type, String content) {
        Set<String> out = new LinkedHashSet<>();
        addMatches(out, JAVA_IMPORT, content, 1);
        addMatches(out, PY_IMPORT, content, 1);
        addMatches(out, PY_IMPORT, content, 2);
        addMatches(out, JS_IMPORT, content, 1);
        addMatches(out, JS_IMPORT, content, 2);
        addMatches(out, RUST_USE, content, 1);
        Matcher go = GO_IMPORT.matcher(content);
        while (go.find()) {
            if (go.group(1) != null) for (String s : go.group(1).split("\\R")) addClean(out, s);
            addClean(out, go.group(2));
        }
        return limit(out, 1000);
    }

    private List<String> parseSymbols(String type, String content) {
        Set<String> out = new LinkedHashSet<>();
        addMatches(out, JAVA_TYPE, content, 1); addMatches(out, JAVA_METHOD, content, 1);
        addMatches(out, PY_CLASS, content, 1); addMatches(out, PY_DEF, content, 1);
        addMatches(out, JS_FUNCTION, content, 1); addMatches(out, JS_FUNCTION, content, 2); addMatches(out, JS_CLASS, content, 1);
        addMatches(out, GO_DECL, content, 1); addMatches(out, RUST_DECL, content, 1);
        addMatches(out, C_DECL, content, 1); addMatches(out, PHP_DECL, content, 1);
        addMatches(out, SWIFT_DECL, content, 1); addMatches(out, KOTLIN_DECL, content, 1);
        addMatches(out, HTML_TAG, content, 1); addMatches(out, ANCHOR, content, 1);
        return limit(out, 2000);
    }

    private List<String> parseConstructs(String type, String content) {
        Set<String> out = new LinkedHashSet<>();
        String t = type == null ? "" : type.toUpperCase();
        if (t.contains("HTML") || t.contains("REACT")) {
            addMatches(out, HTML_TAG, content, 1);
            addMatches(out, ROUTE, content, 1); addMatches(out, ROUTE, content, 2); addMatches(out, ROUTE, content, 3);
        }
        if (t.contains("CSS") || t.contains("SCSS") || t.contains("LESS")) addMatches(out, CSS_SELECTOR, content, 1);
        if (t.contains("JAVA") || t.contains("KOTLIN") || t.contains("C#") || t.contains("PYTHON") || t.contains("JAVASCRIPT") || t.contains("TYPESCRIPT")) {
            addMatches(out, ROUTE, content, 1); addMatches(out, ROUTE, content, 2); addMatches(out, ROUTE, content, 3);
        }
        if (t.contains("SQL")) {
            for (String keyword : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE TABLE", "ALTER TABLE", "CREATE VIEW", "CREATE INDEX", "JOIN", "TRANSACTION")) {
                if (Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b").matcher(content).find()) out.add(keyword);
            }
            Matcher tables = Pattern.compile("(?is)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][\\w$]*)").matcher(content);
            while (tables.find()) out.add("TABLE:" + tables.group(1));
            Matcher views = Pattern.compile("(?is)\\bCREATE\\s+VIEW\\s+([A-Za-z_][\\w$]*)").matcher(content);
            while (views.find()) out.add("VIEW:" + views.group(1));
        }
        if (t.contains("YAML") || t.contains("JSON") || t.contains("PROPERTIES") || t.contains("XML")) {
            for (String keyword : List.of("server", "spring", "datasource", "database", "security", "oauth", "jwt", "docker", "kubernetes", "redis", "mongo", "postgres", "mysql")) {
                if (Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b").matcher(content).find()) out.add(keyword);
            }
        }
        return limit(out, 2000);
    }

    private void addMatches(Set<String> out, Pattern pattern, String content, int group) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) addClean(out, group <= matcher.groupCount() ? matcher.group(group) : null);
    }

    private void addClean(Set<String> out, String value) {
        if (value == null) return;
        String v = value.trim();
        if (!v.isBlank()) out.add(v);
    }

    private List<String> unique(Pattern pattern, String content, int group) { Set<String> out = new LinkedHashSet<>(); addMatches(out, pattern, content, group); return limit(out, 1000); }
    private List<String> limit(Set<String> values, int max) { return values.stream().filter(v -> v != null && !v.isBlank()).limit(max).toList(); }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Unable to fingerprint repository file.", e); }
    }
}
