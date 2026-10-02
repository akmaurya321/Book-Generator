package book.example.dto;

import java.util.ArrayList;
import java.util.List;

/** Result of best-effort repository indexing. Successful chunks remain usable even when a small subset fails. */
public class RepositoryIndexResult {
    private int successfulChunks;
    private int totalChunks;
    private List<String> failedChunkIds = new ArrayList<>();
    private List<String> failedFilePaths = new ArrayList<>();

    public int getSuccessfulChunks() { return successfulChunks; }
    public void setSuccessfulChunks(int successfulChunks) { this.successfulChunks = successfulChunks; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public List<String> getFailedChunkIds() { return failedChunkIds; }
    public void setFailedChunkIds(List<String> failedChunkIds) { this.failedChunkIds = failedChunkIds == null ? new ArrayList<>() : failedChunkIds; }
    public List<String> getFailedFilePaths() { return failedFilePaths; }
    public void setFailedFilePaths(List<String> failedFilePaths) { this.failedFilePaths = failedFilePaths == null ? new ArrayList<>() : failedFilePaths; }

    public boolean isPartial() { return !failedChunkIds.isEmpty() && successfulChunks > 0; }
    public boolean isComplete() { return failedChunkIds.isEmpty() && successfulChunks == totalChunks; }
}
