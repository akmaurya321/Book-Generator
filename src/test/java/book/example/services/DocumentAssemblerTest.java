package book.example.services;

import book.example.dto.GeneratedDocumentation;
import book.example.dto.GeneratedSection;
import book.example.dto.GeneratedTable;
import book.example.dto.CoverLayout;
import book.example.dto.StudentProjectDetails;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentAssemblerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void assemblesRealWordTableAndReferenceHeadingStyles() throws Exception {
        DocumentAssembler assembler = new DocumentAssembler();
        ReflectionTestUtils.setField(assembler, "storageRoot", temporaryDirectory.toString());

        GeneratedTable table = new GeneratedTable(
                "Detected Technologies",
                List.of("Technology", "Evidence"),
                List.of(List.of("Java", "Detected from scanned repository files"))
        );
        GeneratedSection chapter = new GeneratedSection(1, "Chapter 1 — Introduction", "H1", "Verified chapter content.");
        chapter.setTables(List.of(table));
        GeneratedSection subsection = new GeneratedSection(2, "1.1 Scope", "H2", "Verified subsection content.");

        GeneratedDocumentation documentation = new GeneratedDocumentation();
        documentation.setTitle("Demo Project");
        documentation.setProjectName("Demo Project");
        documentation.setSections(List.of(chapter, subsection));

        Path output = assembler.assemble(documentation, "table-assembly-test");

        try (InputStream input = Files.newInputStream(output);
             XWPFDocument docx = new XWPFDocument(input)) {
            assertEquals(1, docx.getTables().size());
            assertEquals("Technology", docx.getTables().getFirst().getRow(0).getCell(0).getText());
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().equals("1 Introduction") && "Heading1".equals(paragraph.getStyle())));
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().equals("1.1 Scope") && "Heading2".equals(paragraph.getStyle())));
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().contains("Detected Technologies")));
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().equals("List of Tables")));
        }
    }

    @Test
    void appliesCoverLayoutAndCropsTheCollegeLogoInTheExportedDocument() throws Exception {
        DocumentAssembler assembler = new DocumentAssembler();
        ReflectionTestUtils.setField(assembler, "storageRoot", temporaryDirectory.toString());

        Path assetDirectory = temporaryDirectory.resolve("jobs/cover-layout-test/assets");
        Files.createDirectories(assetDirectory);
        Path logo = assetDirectory.resolve("IMG-001.png");
        ImageIO.write(new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB), "png", logo.toFile());

        CoverLayout layout = new CoverLayout();
        layout.setTitleX(20);
        layout.setTitleY(20);
        layout.setTitleScale(1.25);
        layout.setLogoScale(1.2);
        layout.setLogoZoom(1.5);
        layout.setLogoCropX(25);
        layout.setLogoCropY(75);
        layout.setBodyX(80);
        layout.setBodyScale(1.1);
        StudentProjectDetails details = new StudentProjectDetails();
        details.setName("Student Example");
        details.setEnrollmentNumber("ENR-42");
        details.setCollegeName("Example College");
        details.setCollegeLogoAssetId("IMG-001");
        details.setTeamMembers(List.of("Teammate One"));
        details.setCoverLayout(layout);

        GeneratedDocumentation documentation = new GeneratedDocumentation();
        documentation.setTitle("Cover layout report");
        documentation.setProjectName("Example Project");
        documentation.setStudentDetails(details);

        Path output = assembler.assemble(documentation, "cover-layout-test");

        try (InputStream input = Files.newInputStream(output);
             XWPFDocument docx = new XWPFDocument(input)) {
            assertEquals(ParagraphAlignment.LEFT, docx.getParagraphs().getFirst().getAlignment());
            assertEquals(1, docx.getAllPictures().size());
            var picture = docx.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .flatMap(run -> run.getEmbeddedPictures().stream())
                    .findFirst()
                    .orElseThrow();
            var crop = picture.getCTPicture().getBlipFill().getSrcRect();
            assertNotNull(crop);
            assertEquals(16667, crop.getL());
            assertEquals(25000, crop.getT());
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().contains("Enrollment No: ENR-42")
                            && paragraph.getText().contains("Teammate One")));
            assertTrue(docx.getParagraphs().stream().anyMatch(paragraph ->
                    paragraph.getText().equals("Example College")
                            && paragraph.getAlignment() == ParagraphAlignment.RIGHT));
        }
    }
}
