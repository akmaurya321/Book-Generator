package book.example.services;

import java.util.List;

public interface EmbeddingProvider {

    List<Float> embed(String text);
}