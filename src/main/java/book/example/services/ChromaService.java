package book.example.services;

import book.example.dto.RepositoryChunk;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChromaService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ChromaService(
            ObjectMapper objectMapper,
            @Value("${chroma.base-url:http://localhost:8000}") String chromaBaseUrl) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        this.restClient =
                RestClient.builder()
                        .requestFactory(requestFactory)
                        .baseUrl(chromaBaseUrl)
                        .build();

        this.objectMapper = objectMapper;
    }

    // =========================================================
    // CREATE / GET JOB COLLECTION
    // =========================================================

    public String getOrCreateCollection(
            String jobId) {

        validateJobId(jobId);

        String collectionName =
                collectionName(jobId);

        try {
            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "name",
                    collectionName
            );

            request.put(
                    "get_or_create",
                    true
            );

            Map<String, String> metadata =
                    new LinkedHashMap<>();

            metadata.put(
                    "jobId",
                    jobId
            );

            metadata.put(
                    "temporary",
                    "true"
            );

            request.put(
                    "metadata",
                    metadata
            );

            String response =
                    restClient.post()
                            .uri("/api/v1/collections")
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

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode idNode =
                    root.get("id");

            if (idNode == null ||
                    idNode.asText().isBlank()) {

                throw new IllegalStateException(
                        "Chroma collection ID was not returned"
                );
            }

            return idNode.asText();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to create/get Chroma collection "
                            + collectionName,
                    e
            );
        }
    }

    // =========================================================
    // ADD CHUNK
    // =========================================================

    public void addChunkToCollection(
            String jobId,
            String collectionId,
            RepositoryChunk chunk,
            List<Float> embedding) {

        validateJobId(jobId);
        if (collectionId == null || collectionId.isBlank()) {
            throw new IllegalArgumentException("Chroma collection ID is required");
        }

        if (chunk == null) {
            throw new IllegalArgumentException(
                    "Chunk is required"
            );
        }

        if (embedding == null ||
                embedding.isEmpty()) {

            throw new IllegalArgumentException(
                    "Embedding is required"
            );
        }

        try {

            Map<String, Object> request =
                    new LinkedHashMap<>();

            request.put(
                    "ids",
                    List.of(chunk.getChunkId())
            );

            request.put(
                    "embeddings",
                    List.of(embedding)
            );

            request.put(
                    "documents",
                    List.of(
                            chunk.getContent()
                    )
            );

            Map<String, Object> metadata =
                    new LinkedHashMap<>();

            metadata.put(
                    "jobId",
                    jobId
            );

            metadata.put(
                    "filePath",
                    chunk.getFilePath()
            );

            metadata.put(
                    "fileType",
                    chunk.getFileType()
            );

            metadata.put(
                    "startLine",
                    chunk.getStartLine()
            );

            metadata.put(
                    "endLine",
                    chunk.getEndLine()
            );

            request.put(
                    "metadatas",
                    List.of(metadata)
            );

            restClient.post()
                    .uri(
                            "/api/v1/collections/"
                                    + collectionId
                                    + "/upsert"
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
                    .toBodilessEntity();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to add chunk to Chroma "
                            + "for job "
                            + jobId,
                    e
            );
        }
    }

    // =========================================================
    // DELETE ENTIRE JOB COLLECTION
    // =========================================================

    public void deleteJobData(
            String jobId) {

        validateJobId(jobId);

        String collectionName =
                collectionName(jobId);

        try {

            /*
             * We first find the collection.
             *
             * If it does not exist, cleanup is simply skipped.
             */

            String collectionId =
                    findCollectionId(
                            collectionName
                    );

            if (collectionId == null) {
                return;
            }

            restClient.delete()
                    .uri(
                            "/api/v1/collections/"
                                    + collectionId
                    )
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException e) {

            if (e.getStatusCode().value() == 400 &&
                    e.getResponseBodyAsString().toLowerCase().contains("collection") &&
                    e.getResponseBodyAsString().toLowerCase().contains("does not exist")) {
                return;
            }

            throw new IllegalStateException(
                    "Failed to delete Chroma collection "
                            + collectionName,
                    e
            );

        } catch (Exception e) {

            /*
             * Cleanup should not hide the original
             * documentation-generation failure.
             */

            throw new IllegalStateException(
                    "Failed to delete Chroma collection "
                            + collectionName,
                    e
            );
        }
    }

    // =========================================================
    // FIND COLLECTION ID
    // =========================================================

    private String findCollectionId(
            String collectionName) {

        try {

            String response =
                    restClient.get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(
                                                            "/api/v1/collections/{name}"
                                                    )
                                                    .build(
                                                            collectionName
                                                    )
                            )
                            .retrieve()
                            .body(String.class);

            if (response == null ||
                    response.isBlank()) {

                return null;
            }

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode idNode =
                    root.get("id");

            if (idNode == null) {
                return null;
            }

            return idNode.asText();

        } catch (Exception e) {

            /*
             * Chroma returns an error when collection
             * does not exist. Cleanup treats that as
             * "nothing to delete".
             */

            return null;
        }
    }

    // =========================================================
    // COLLECTION NAME
    // =========================================================

    private String collectionName(
            String jobId) {

        return "job_" +
                jobId.replace(
                        "-",
                        ""
                );
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateJobId(
            String jobId) {

        if (jobId == null ||
                jobId.isBlank()) {

            throw new IllegalArgumentException(
                    "Job ID is required"
            );
        }
    }
}