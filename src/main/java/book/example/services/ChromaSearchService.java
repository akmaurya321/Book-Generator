package book.example.services;

import book.example.dto.RagSearchResult;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChromaSearchService {

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final EmbeddingService embeddingService;

    private final ChromaService chromaService;

    public ChromaSearchService(
            EmbeddingService embeddingService,
            ChromaService chromaService,
            ObjectMapper objectMapper,
            @Value("${chroma.base-url:http://localhost:8000}") String chromaBaseUrl) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        this.restClient =
                RestClient.builder()
                        .requestFactory(requestFactory)
                        .baseUrl(chromaBaseUrl)
                        .build();

        this.embeddingService =
                embeddingService;

        this.chromaService =
                chromaService;

        this.objectMapper =
                objectMapper;
    }

    public static List<RagSearchResult> rankForSection(
            List<RagSearchResult> results,
            String query) {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeText(query);
        List<String> queryTokens = List.of(normalizedQuery.split("\\s+"));

        return results.stream()
                .filter(result -> score(result, queryTokens) > 0.0)
                .sorted(Comparator
                        .comparingDouble((RagSearchResult result) -> score(result, queryTokens))
                        .reversed())
                .toList();
    }

    private static double score(
            RagSearchResult result,
            List<String> queryTokens) {

        if (result == null) return 0.0;
        String content = normalizeText(result.getContent());
        String symbols = normalizeText(String.join(" ", result.getSymbols()));
        String imports = normalizeText(String.join(" ", result.getImports()));
        String constructs = normalizeText(String.join(" ", result.getConstructs()));
        double score = result.getDistance() <= 0.0 ? 1.0 : Math.max(0.0, 2.0 - (result.getDistance() * 2.0));
        int exact = 0;
        for (String token : queryTokens) {
            if (token.isBlank()) continue;
            if (symbols.contains(token)) { score += 4.0; exact++; }
            else if (constructs.contains(token)) { score += 3.0; exact++; }
            else if (imports.contains(token)) { score += 2.5; exact++; }
            else if (content.contains(token)) score += 1.0;
        }
        if (result.getDistance() > 0.0) score -= Math.min(result.getDistance() * 0.5, 1.0);
        if (result.getFileType() != null && !result.getFileType().isBlank()) score += 0.15;
        return Math.max(score, 0.0);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // =========================================================
    // SEARCH
    // =========================================================

    public List<RagSearchResult> search(
            String jobId,
            String query,
            int topK) {

        if (jobId == null ||
                jobId.isBlank()) {

            throw new IllegalArgumentException(
                    "Job ID is required"
            );
        }

        if (query == null ||
                query.isBlank()) {

            throw new IllegalArgumentException(
                    "Search query is required"
            );
        }

        if (topK <= 0) {
            topK = 5;
        }

        /*
         * Make sure the job collection exists.
         *
         * Normally it already exists because
         * RepositoryIngestionService created it.
         */
        String collectionId =
                chromaService.getOrCreateCollection(
                        jobId
                );

        List<Float> queryEmbedding =
                embeddingService.embed(
                        query
                );

        try {

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "query_embeddings",
                    List.of(queryEmbedding)
            );

            int retrievalK = Math.min(Math.max(topK * 3, topK), 50);
            request.put(
                    "n_results",
                    retrievalK
            );

            request.put(
                    "include",
                    List.of(
                            "documents",
                            "metadatas",
                            "distances"
                    )
            );

            String response =
                    restClient.post()
                            .uri(
                                    "/api/v1/collections/"
                                            + collectionId
                                            + "/query"
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    objectMapper
                                            .writeValueAsString(
                                                    request
                                            )
                            )
                            .retrieve()
                            .body(String.class);

            List<RagSearchResult> parsed = parseResults(response);
            List<RagSearchResult> ranked = rankForSection(parsed, query);
            if (ranked.size() > topK) return ranked.subList(0, topK);
            return ranked;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to search Chroma for job "
                            + jobId,
                    e
            );
        }
    }

    // =========================================================
    // PARSE CHROMA RESPONSE
    // =========================================================

    private List<RagSearchResult> parseResults(
            String response) {

        List<RagSearchResult> results =
                new ArrayList<>();

        if (response == null ||
                response.isBlank()) {

            return results;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            JsonNode documents =
                    root.get("documents");

            JsonNode metadatas =
                    root.get("metadatas");

            JsonNode distances =
                    root.get("distances");

            if (documents == null ||
                    !documents.isArray() ||
                    documents.isEmpty()) {

                return results;
            }

            JsonNode documentList =
                    documents.get(0);

            JsonNode metadataList =
                    metadatas != null &&
                            metadatas.isArray() &&
                            !metadatas.isEmpty()
                            ? metadatas.get(0)
                            : null;

            JsonNode distanceList =
                    distances != null &&
                            distances.isArray() &&
                            !distances.isEmpty()
                            ? distances.get(0)
                            : null;

            for (int i = 0;
                 i < documentList.size();
                 i++) {

                String content =
                        documentList
                                .get(i)
                                .asText();

                String filePath = "";

                String fileType = "";

                int startLine = 0;

                int endLine = 0;

                double distance = 0.0;
                RagSearchResult result = new RagSearchResult();

                if (metadataList != null &&
                        i < metadataList.size()) {

                    JsonNode metadata =
                            metadataList.get(i);

                    filePath =
                            text(
                                    metadata,
                                    "filePath"
                            );

                    fileType =
                            text(
                                    metadata,
                                    "fileType"
                            );

                    startLine =
                            integer(
                                    metadata,
                                    "startLine"
                            );

                    endLine =
                            integer(
                                    metadata,
                                    "endLine"
                            );
                    result.setSymbols(stringList(metadata, "symbols"));
                    result.setImports(stringList(metadata, "imports"));
                    result.setAnnotations(stringList(metadata, "annotations"));
                    result.setConstructs(stringList(metadata, "constructs"));
                }

                if (distanceList != null &&
                        i < distanceList.size()) {

                    distance =
                            distanceList
                                    .get(i)
                                    .asDouble();
                }

                result.setContent(
                        content
                );

                result.setFilePath(
                        filePath
                );

                result.setFileType(
                        fileType
                );

                result.setStartLine(
                        startLine
                );

                result.setEndLine(
                        endLine
                );

                result.setDistance(
                        distance
                );

                results.add(
                        result
                );
            }

            return results;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to parse Chroma search response",
                    e
            );
        }
    }

    // =========================================================
    // JSON HELPERS
    // =========================================================

    private List<String> stringList(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("\\s*,\\s*"))
                .map(String::trim).filter(v -> !v.isBlank()).limit(2000).toList();
    }

    private String text(
            JsonNode node,
            String field) {

        if (node == null ||
                node.get(field) == null) {

            return "";
        }

        return node.get(field)
                .asText("");
    }

    private int integer(
            JsonNode node,
            String field) {

        if (node == null ||
                node.get(field) == null) {

            return 0;
        }

        return node.get(field)
                .asInt(0);
    }
}