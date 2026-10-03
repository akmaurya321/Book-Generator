package book.example.dto;

public record DocumentTransformRequest(
        int paragraphIndex,
        int startOffset,
        int endOffset,
        String selectedText,
        String instruction) {
}
