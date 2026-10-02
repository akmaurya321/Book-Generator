package book.example.services;

public class LlmProviderException extends IllegalStateException {
    private final Integer statusCode;
    private final boolean transientFailure;

    public LlmProviderException(String message, Integer statusCode, boolean transientFailure, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.transientFailure = transientFailure;
    }

    public Integer getStatusCode() { return statusCode; }
    public boolean isTransientFailure() { return transientFailure; }
}
