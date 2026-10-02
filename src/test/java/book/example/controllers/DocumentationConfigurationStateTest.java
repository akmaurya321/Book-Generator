package book.example.controllers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationConfigurationStateTest {
    @Test
    void configurationAssetsRemainEditableDuringBackgroundIndexing() {
        assertTrue(DocumentationFileController.isConfigurationEditable("INDEXING_PROJECT"));
        assertTrue(DocumentationFileController.isConfigurationEditable("WAITING_FOR_INDEXING"));
        assertTrue(DocumentationFileController.isConfigurationEditable("WAITING_FOR_USER_CONFIGURATION"));
    }

    @Test
    void configurationAssetsAreLockedOnceGenerationStartsOrTerminates() {
        for (String status : new String[]{
                "QUEUED_FOR_GENERATION", "GENERATING_DOCUMENTATION", "ASSEMBLING_DOCUMENT",
                "VALIDATING_DOCUMENT", "PREPARING_PDF", "COMPLETED", "FAILED", "CANCELLED"}) {
            assertFalse(DocumentationFileController.isConfigurationEditable(status), status);
        }
    }
}
