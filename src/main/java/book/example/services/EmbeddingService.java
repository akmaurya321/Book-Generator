package book.example.services;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingProviderFactory providerFactory;

    public EmbeddingService(
            EmbeddingProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public List<Float> embed(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text cannot be empty"
            );
        }

        EmbeddingProvider provider =
                providerFactory.getProvider();

        return provider.embed(text);
    }
}