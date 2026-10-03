package book.example.dto;

public record DocumentEditRequest(
        long version,
        int paragraphIndex,
        int startOffset,
        int endOffset,
        String selectedText,
        String replacement) {
}
