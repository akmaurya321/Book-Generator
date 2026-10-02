package book.example.services;

import book.example.dto.StudentContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentContextTest {
    @Test
    void emptyContextIsSafeAndUserEditable() {
        StudentContext context = new StudentContext();
        assertTrue(context.getMotivation().isBlank());
        context.setMotivation("Manual documentation was time-consuming.");
        context.setObjectives(java.util.List.of("Analyze project", "Generate report"));
        assertEquals(2, context.getObjectives().size());
        assertEquals("Manual documentation was time-consuming.", context.getMotivation());
    }
}
