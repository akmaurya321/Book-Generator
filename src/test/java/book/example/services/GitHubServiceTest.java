package book.example.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GitHubServiceTest {

    @Test
    void buildGitEnvironment_disablesInteractivePrompts() {
        Map<String, String> environment = GitHubService.buildGitEnvironment();

        assertNotNull(environment);
        assertEquals("0", environment.get("GIT_TERMINAL_PROMPT"));
        assertEquals("NEVER", environment.get("GCM_INTERACTIVE"));
    }
}
