package book.example.services;

import book.example.dto.RepositoryFile;
import book.example.dto.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryChunkerDeterminismTest {

    @Test
    void chunkIdsRemainStableAcrossRechunkingForFailedOnlyRetries() {
        RepositoryFile file = new RepositoryFile(
                "src/main/java/example/Demo.java",
                "class Demo {\n" + "  void run() {}\n" + "}\n",
                "JAVA", 45, false);
        RepositorySnapshot snapshot = new RepositorySnapshot("", List.of(file));
        RepositoryChunker chunker = new RepositoryChunker();

        String first = chunker.chunk(snapshot).get(0).getChunkId();
        String second = chunker.chunk(snapshot).get(0).getChunkId();

        assertEquals(first, second);
        assertTrue(first.startsWith("chunk-"));
    }
}
