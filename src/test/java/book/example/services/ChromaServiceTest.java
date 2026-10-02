package book.example.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ChromaServiceTest {

    @Test
    void deleteJobDataTreatsAlreadyDeletedCollectionAsSuccessfulCleanup() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/collections", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] body = "{\"id\":\"collection-id\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
                return;
            }

            byte[] body = "{\"error\":\"InvalidArgumentError\",\"message\":\"Collection collection-id does not exist.\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            ChromaService service = new ChromaService(
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertDoesNotThrow(() -> service.deleteJobData("test-job"));
        } finally {
            server.stop(0);
        }
    }
}
