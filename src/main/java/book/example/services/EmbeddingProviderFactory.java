package book.example.services;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderFactory {

    private final AiProperties aiProperties;
    private final OllamaEmbeddingProvider ollamaEmbeddingProvider;
    private final HuggingFaceEmbeddingProvider huggingFaceEmbeddingProvider;
    private final OpenAiEmbeddingProvider openAiEmbeddingProvider;

    public EmbeddingProviderFactory(
            AiProperties aiProperties,
            OllamaEmbeddingProvider ollamaEmbeddingProvider,
            HuggingFaceEmbeddingProvider huggingFaceEmbeddingProvider,
            OpenAiEmbeddingProvider openAiEmbeddingProvider) {

        this.aiProperties = aiProperties;
        this.ollamaEmbeddingProvider =
                ollamaEmbeddingProvider;
        this.huggingFaceEmbeddingProvider =
                huggingFaceEmbeddingProvider;
        this.openAiEmbeddingProvider =
                openAiEmbeddingProvider;
    }

    public EmbeddingProvider getProvider() {

        String provider = aiProperties.getProvider();

        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException(
                    "ai.provider is not configured"
            );
        }

        return switch (provider.toLowerCase()) {

            case "ollama" ->
                    ollamaEmbeddingProvider;

            case "huggingface", "hf" ->
                    huggingFaceEmbeddingProvider;

            case "openai" ->
                    openAiEmbeddingProvider;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported AI provider: "
                                    + provider
                    );
        };
    }
}