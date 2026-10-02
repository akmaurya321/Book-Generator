package book.example.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceTextSanitizerTest {
    @Test
    void removesCommentsButPreservesExecutableStringsAndLines() {
        String source = "// @GetMapping(\"/fake\")\n" +
                "@GetMapping(\"/real\")\n" +
                "String url = \"http://example.com//keep\";\n" +
                "/* @Repository */\n" +
                "class UserService {}\n";
        String clean = SourceTextSanitizer.stripComments(source, "JAVA");
        assertFalse(clean.contains("@Repository"));
        assertTrue(clean.contains("@GetMapping(\"/real\")"));
        assertTrue(clean.contains("http://example.com//keep"));
        assertTrue(clean.split("\\R", -1).length >= 5);
    }

    @Test
    void removesPythonCommentsWithoutDamagingStrings() {
        String source = "# import fake\n" +
                "from fastapi import FastAPI\n" +
                "message = '# not a comment'\n";
        String clean = SourceTextSanitizer.stripComments(source, "PYTHON");
        assertFalse(clean.contains("import fake"));
        assertTrue(clean.contains("from fastapi import FastAPI"));
        assertTrue(clean.contains("# not a comment"));
    }
}
