package book.example.services;

import book.example.dto.RepositoryChunk;
import book.example.dto.RepositoryFile;
import book.example.dto.RepositorySnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Creates provenance-preserving chunks. Raw source lines remain the evidence of record. */
@Service
public class RepositoryChunker {
    private static final int CHUNK_SIZE = 160;
    private static final int CHUNK_OVERLAP = 40;

    public List<RepositoryChunk> chunk(RepositorySnapshot snapshot) {
        if (snapshot == null || snapshot.getFiles() == null) throw new IllegalArgumentException("Repository snapshot is required");
        List<RepositoryChunk> chunks = new ArrayList<>();
        for (RepositoryFile file : snapshot.getFiles()) {
            if (file == null || file.isBinary() || file.getContent() == null || file.getContent().isBlank()) continue;
            chunks.addAll(chunkFile(file));
        }
        return chunks;
    }

    private List<RepositoryChunk> chunkFile(RepositoryFile file) {
        List<RepositoryChunk> chunks = new ArrayList<>();
        String[] lines = file.getContent().split("\\R", -1);
        int start = 0;
        while (start < lines.length) {
            int end = Math.min(start + CHUNK_SIZE, lines.length);
            StringBuilder raw = new StringBuilder();
            for (int i = start; i < end; i++) { raw.append(lines[i]); if (i < end - 1) raw.append('\n'); }
            if (!raw.toString().isBlank()) {
                RepositoryChunk chunk = new RepositoryChunk();
                chunk.setChunkId(stableChunkId(file.getPath(), start + 1, end, raw.toString()));
                chunk.setFilePath(file.getPath());
                chunk.setFileType(file.getFileType());
                chunk.setStartLine(start + 1); chunk.setEndLine(end);
                String context = "SOURCE_FILE: " + file.getPath()
                        + "\nFILE_TYPE: " + file.getFileType()
                        + "\nFILE_SHA256: " + safe(file.getSha256())
                        + "\nSYMBOLS: " + String.join(", ", file.getSymbols())
                        + "\nIMPORTS: " + String.join(", ", file.getImports())
                        + "\nANNOTATIONS: " + String.join(", ", file.getAnnotations())
                        + "\nCONSTRUCTS: " + String.join(", ", file.getConstructs())
                        + "\nSOURCE_LINES " + (start + 1) + "-" + end + ":\n";
                chunk.setContent(context + raw);
                Map<String,String> m = new HashMap<>();
                m.put("repository", "source"); m.put("filePath", file.getPath()); m.put("fileType", safe(file.getFileType()));
                m.put("startLine", String.valueOf(start + 1)); m.put("endLine", String.valueOf(end)); m.put("sha256", safe(file.getSha256()));
                m.put("symbols", String.join(", ", file.getSymbols())); m.put("imports", String.join(", ", file.getImports()));
                m.put("annotations", String.join(", ", file.getAnnotations())); m.put("constructs", String.join(", ", file.getConstructs()));
                chunk.setMetadata(m); chunks.add(chunk);
            }
            if (end >= lines.length) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }
    private String stableChunkId(String filePath, int startLine, int endLine, String content) {
        try {
            String raw = safe(filePath) + "\n" + startLine + "-" + endLine + "\n" + safe(content);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("chunk-");
            for (int i = 0; i < 16; i++) out.append(String.format("%02x", digest[i]));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create deterministic repository chunk ID.", e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
