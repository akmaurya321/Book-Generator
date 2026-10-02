package book.example.services;

import book.example.dto.RepositoryFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolyglotRepositoryCodeStructureAnalyzerTest {
    private final RepositoryCodeStructureAnalyzer analyzer = new RepositoryCodeStructureAnalyzer();

    @Test
    void analyzesJavaPythonJavaScriptHtmlCssAndSqlFromContent() {
        assertHas("JAVA", "import java.util.List;\npublic class UserService { public User findUser() { return null; } }", "UserService", "java.util.List");
        assertHas("PYTHON", "from fastapi import FastAPI\nclass UserService:\n    def find_user(self): pass", "UserService", "fastapi");
        assertHas("JAVASCRIPT", "import x from 'x';\nclass App {}\nconst load = () => {};", "App", "x");
        assertHas("HTML", "<html><body><a href='/users'>Users</a></body></html>", "html", "/users");
        assertHas("CSS", ".login-form { display: flex; }", ".login-form");
        assertHas("SQL", "CREATE TABLE users(id INT); SELECT * FROM users;", "CREATE TABLE", "SELECT");
    }

    @Test
    void fingerprintsSourceAndDoesNotAnalyzeBinary() {
        RepositoryFile source = new RepositoryFile("User.java", "class User {}", "JAVA", 13, false);
        analyzer.analyze(source);
        assertNotNull(source.getSha256());
        assertFalse(source.getSymbols().isEmpty());

        RepositoryFile binary = new RepositoryFile("model.pt", null, "PYTORCH_MODEL", 100, true);
        analyzer.analyze(binary);
        assertTrue(binary.getSymbols().isEmpty());
        assertNull(binary.getSha256());
    }

    private void assertHas(String type, String content, String... expected) {
        RepositoryFile file = new RepositoryFile("source", content, type, content.length(), false);
        analyzer.analyze(file);
        String all = (file.getSymbols() + " " + file.getImports() + " " + file.getConstructs()).toLowerCase();
        for (String value : expected) assertTrue(all.contains(value.toLowerCase()), type + " missing evidence: " + value);
    }
}
