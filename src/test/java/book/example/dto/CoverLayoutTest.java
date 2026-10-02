package book.example.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverLayoutTest {

    @Test
    void clampsCoverPositionsAndScalesToSafePageBounds() {
        CoverLayout layout = new CoverLayout();
        layout.setTitleY(90);
        layout.setLogoY(5);
        layout.setBodyY(-10);
        layout.setLogoZoom(9);
        layout.setBodyScale(Double.NaN);

        assertEquals(22, layout.getTitleY());
        assertEquals(24, layout.getLogoY());
        assertEquals(44, layout.getBodyY());
        assertEquals(2.5, layout.getLogoZoom());
        assertEquals(1, layout.getBodyScale());
    }
}
