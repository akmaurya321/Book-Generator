package book.example.controllers;

import book.example.Entity.AppUser;
import book.example.dto.DocumentEditRequest;
import book.example.dto.DocumentTransformRequest;
import book.example.services.DocumentEditorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documentation/{jobId}/editor")
public class DocumentEditorController {
    private final DocumentEditorService documentEditorService;

    public DocumentEditorController(DocumentEditorService documentEditorService) {
        this.documentEditorService = documentEditorService;
    }

    @GetMapping
    public ResponseEntity<?> load(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String jobId) {
        try {
            return ResponseEntity.ok(documentEditorService.load(jobId, user));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/alternatives")
    public ResponseEntity<?> transform(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String jobId,
            @RequestBody DocumentTransformRequest request) {
        try {
            List<String> alternatives = documentEditorService.transform(jobId, user, request);
            return ResponseEntity.ok(Map.of("alternatives", alternatives));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && (exception.getMessage().startsWith("The selected text is stale")
                    || exception.getMessage().startsWith("Only completed documents"))) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String jobId,
            @RequestBody DocumentEditRequest request) {
        try {
            return ResponseEntity.ok(documentEditorService.save(jobId, user, request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/undo")
    public ResponseEntity<?> undo(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String jobId,
            @RequestBody Map<String, Long> request) {
        return moveHistory(jobId, user, request, false);
    }

    @PostMapping("/redo")
    public ResponseEntity<?> redo(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String jobId,
            @RequestBody Map<String, Long> request) {
        return moveHistory(jobId, user, request, true);
    }

    private ResponseEntity<?> moveHistory(
            String jobId,
            AppUser user,
            Map<String, Long> request,
            boolean redo) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Document version is required."));
            }
            Long version = request.get("version");
            if (version == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Document version is required."));
            }
            return ResponseEntity.ok(documentEditorService.moveHistory(jobId, user, version, redo));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }
}
