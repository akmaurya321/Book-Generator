package book.example.services;

import book.example.dto.RepositoryFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryCodeStructureAnalyzerTest {

    @Test
    void extractsFactualJavaStructureFromContent() {
        RepositoryFile file = new RepositoryFile(
                "src/main/java/com/example/UserService.java",
                "package com.example;\n" +
                "import java.util.List;\n" +
                "@Service\n" +
                "public class UserService {\n" +
                "  public List<String> findUsers() { return List.of(); }\n" +
                "}",
                "JAVA", 160, false);

        new RepositoryCodeStructureAnalyzer().analyze(file);

        assertNotNull(file.getSha256());
        assertEquals(6, file.getLineCount());
        assertTrue(file.getImports().contains("java.util.List"));
        assertTrue(file.getAnnotations().contains("Service"));
        assertTrue(file.getSymbols().contains("UserService"));
        assertTrue(file.getSymbols().contains("findUsers"));
    }

    @Test
    void doesNotAnalyzeBinaryContent() {
        RepositoryFile file = new RepositoryFile("image.png", null, "IMAGE", 10, true);
        new RepositoryCodeStructureAnalyzer().analyze(file);
        assertNull(file.getSha256());
        assertTrue(file.getSymbols().isEmpty());
    }
}
