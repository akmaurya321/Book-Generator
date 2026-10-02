package book.example.services;

import book.example.dto.DiagramImage;
import book.example.dto.GeneratedDocumentation;
import book.example.dto.GeneratedSection;
import book.example.dto.GeneratedTable;
import book.example.dto.DocumentFormatDefinition;
import book.example.dto.CoverLayout;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class DocumentAssembler {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentAssembler.class);

    @Value("${app.storage.root:generated}")
    private String storageRoot = "generated";

    private final ThreadLocal<DocumentFormatDefinition> activeFormat =
            ThreadLocal.withInitial(DocumentFormatDefinition::new);

    // =========================================================
    // MAIN ASSEMBLY
    // =========================================================

    public Path assemble(
            GeneratedDocumentation documentation,
            String jobId) {

        if (documentation == null) {
            throw new IllegalArgumentException(
                    "Generated documentation is required"
            );
        }

        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(
                    "Job ID is required"
            );
        }

        String effectiveStorageRoot =
                storageRoot == null || storageRoot.isBlank()
                        ? "generated"
                        : storageRoot;

        Path outputDirectory =
                Path.of(
                        effectiveStorageRoot,
                        "jobs",
                        jobId
                );

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create output directory",
                    e
            );
        }

        Path outputFile =
                outputDirectory.resolve(
                        "documentation.docx"
                );

        activeFormat.set(
                documentation.getFormat() == null
                        ? new DocumentFormatDefinition()
                        : documentation.getFormat()
        );

        try (XWPFDocument document =
                     new XWPFDocument()) {

            configurePage(document);

            if (isEnabled(documentation, "cover_page")) {
                addTitlePage(
                        document,
                        jobId,
                        documentation
                );
            }

            if (isEnabled(documentation, "table_of_contents")) {
                addTableOfContents(document);
            }

            addDocumentIndexes(
                    document,
                    documentation
            );

            addSections(
                    document,
                    documentation.getSections()
            );

            addFooter(document);

            try (OutputStream outputStream =
                         Files.newOutputStream(outputFile)) {

                document.write(outputStream);
            }

            return outputFile;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to create DOCX document",
                    e
            );

        } finally {
            activeFormat.remove();
        }
    }

    // =========================================================
    // PAGE CONFIGURATION
    // =========================================================

    private void configurePage(
            XWPFDocument document) {

        CTSectPr section =
                document.getDocument()
                        .getBody()
                        .getSectPr();

        if (section == null) {

            section =
                    document.getDocument()
                            .getBody()
                            .addNewSectPr();
        }

        CTPageSz pageSize =
                section.isSetPgSz()
                        ? section.getPgSz()
                        : section.addNewPgSz();

        long[] page =
                pageDimensions(
                        format().getPageSize()
                );

        boolean landscape =
                "LANDSCAPE".equalsIgnoreCase(
                        format().getOrientation()
                );

        pageSize.setW(
                BigInteger.valueOf(
                        landscape ? page[1] : page[0]
                )
        );

        pageSize.setH(
                BigInteger.valueOf(
                        landscape ? page[0] : page[1]
                )
        );

        CTPageMar margins =
                section.isSetPgMar()
                        ? section.getPgMar()
                        : section.addNewPgMar();

        margins.setTop(
                BigInteger.valueOf(
                        format().getMarginTopTwips()
                )
        );

        margins.setBottom(
                BigInteger.valueOf(
                        format().getMarginBottomTwips()
                )
        );

        margins.setLeft(
                BigInteger.valueOf(
                        format().getMarginLeftTwips()
                )
        );

        margins.setRight(
                BigInteger.valueOf(
                        format().getMarginRightTwips()
                )
        );

        margins.setHeader(
                BigInteger.valueOf(720)
        );

        margins.setFooter(
                BigInteger.valueOf(720)
        );
    }

    // =========================================================
    // TITLE PAGE
    // =========================================================

    private void addTitlePage(
            XWPFDocument document,
            String jobId,
            GeneratedDocumentation documentation) {

        XWPFParagraph titleParagraph =
                document.createParagraph();

        titleParagraph.setStyle("Title");
        CoverLayout layout = documentation.getStudentDetails() == null
                ? new CoverLayout()
                : documentation.getStudentDetails().getCoverLayout();
        setCoverAlignment(titleParagraph, layout.getTitleX());
        titleParagraph.setSpacingBefore(coverSpacing(900, layout.getTitleY(), 15));

        XWPFRun titleRun =
                titleParagraph.createRun();

        titleRun.setText(
                safe(
                        documentation.getTitle(),
                        "Project Documentation"
                )
        );

        titleRun.setBold(true);
        titleRun.setFontFamily(
                format().getDefaultFont()
        );
        titleRun.setFontSize(scaledFontSize(format().getTitleFontSize(), layout.getTitleScale()));

        XWPFParagraph projectParagraph =
                document.createParagraph();

        setCoverAlignment(projectParagraph, layout.getTitleX());

        projectParagraph.setSpacingBefore(500);

        XWPFRun projectRun =
                projectParagraph.createRun();

        projectRun.setText(
                safe(
                        documentation.getProjectName(),
                        "Software Project"
                )
        );

        projectRun.setBold(true);
        projectRun.setFontFamily(
                format().getDefaultFont()
        );
        projectRun.setFontSize(scaledFontSize(15, layout.getTitleScale()));

        if (documentation.getStudentDetails() != null) {

            var details =
                    documentation.getStudentDetails();

            // -------------------------------------------------
            // COLLEGE LOGO
            // -------------------------------------------------

            boolean hasCollegeLogo = details.getCollegeLogoAssetId() != null
                    && !details.getCollegeLogoAssetId().isBlank();
            if (hasCollegeLogo) {

                addAssetImage(
                        document,
                        jobId,
                        details.getCollegeLogoAssetId(),
                            (int) Math.round(110 * layout.getLogoScale()),
                            (int) Math.round(110 * layout.getLogoScale()),
                            layout,
                            1100,
                            layout.getLogoX(),
                            layout.getLogoY(),
                            layout.getLogoZoom(),
                            layout.getLogoCropX(),
                            layout.getLogoCropY()
                );
            }

            // -------------------------------------------------
            // COLLEGE NAME
            // -------------------------------------------------

            XWPFParagraph institutionParagraph =
                    document.createParagraph();

            setCoverAlignment(institutionParagraph, layout.getBodyX());
            institutionParagraph.setSpacingBefore(coverSpacing(
                    hasCollegeLogo ? 2800 : 4800,
                    layout.getBodyY(),
                    55));

            XWPFRun institutionRun =
                    institutionParagraph.createRun();

            institutionRun.setText(
                    safe(
                            details.getCollegeName(),
                            "College / University"
                    )
            );

            institutionRun.setBold(true);
            institutionRun.setFontFamily(
                    format().getDefaultFont()
            );
            institutionRun.setFontSize(scaledFontSize(13, layout.getBodyScale()));

            // -------------------------------------------------
            // UNIVERSITY NAME
            // -------------------------------------------------

            if (details.getUniversityName() != null
                    && !details.getUniversityName().isBlank()) {

                XWPFParagraph universityParagraph =
                        document.createParagraph();

                setCoverAlignment(universityParagraph, layout.getBodyX());

                XWPFRun universityRun =
                        universityParagraph.createRun();

                universityRun.setText(
                        details.getUniversityName()
                );

                universityRun.setFontFamily(
                        format().getDefaultFont()
                );

                universityRun.setFontSize(scaledFontSize(11, layout.getBodyScale()));
            }

            // -------------------------------------------------
            // STUDENT DETAILS
            // -------------------------------------------------

            XWPFParagraph studentParagraph =
                    document.createParagraph();

            setCoverAlignment(studentParagraph, layout.getBodyX());

            studentParagraph.setSpacingBefore(500);

            XWPFRun studentRun =
                    studentParagraph.createRun();

            studentRun.setText(
                    "Submitted by\n"
                            + safe(details.getName(), "")
                            + (
                            details.getRollNumber() == null
                                    || details.getRollNumber().isBlank()
                                    ? ""
                                    : "\nRoll No: "
                                      + details.getRollNumber()
                    )
                            + (
                            details.getEnrollmentNumber() == null
                                    || details.getEnrollmentNumber().isBlank()
                                    ? ""
                                    : "\nEnrollment No: "
                                      + details.getEnrollmentNumber()
                    )
                            + (
                            details.getAcademicYear() == null
                                    || details.getAcademicYear().isBlank()
                                    ? ""
                                    : "\nAcademic Year: "
                                      + details.getAcademicYear()
                    )
                            + (
                            details.getCourse() == null
                                    || details.getCourse().isBlank()
                                    ? ""
                                    : "\n"
                                      + details.getCourse()
                    )
                            + (
                            details.getDepartment() == null
                                    || details.getDepartment().isBlank()
                                    ? ""
                                    : "\n"
                                      + details.getDepartment()
                    )
                            + (
                            details.getTeamMembers() == null
                                    || details.getTeamMembers().isEmpty()
                                    ? ""
                                    : "\nTeam Members: "
                                      + String.join(", ", details.getTeamMembers())
                    )
            );

            studentRun.setFontFamily(
                    format().getDefaultFont()
            );

            studentRun.setFontSize(scaledFontSize(12, layout.getBodyScale()));

            // -------------------------------------------------
            // GUIDE
            // -------------------------------------------------

            XWPFParagraph guideParagraph =
                    document.createParagraph();

            setCoverAlignment(guideParagraph, layout.getBodyX());

            guideParagraph.setSpacingBefore(500);

            XWPFRun guideRun =
                    guideParagraph.createRun();

            guideRun.setText(
                    "Guided by\n"
                            + safe(
                            details.getGuideName(),
                            ""
                    )
                            + (
                            details.getGuideDesignation() == null
                                    || details.getGuideDesignation().isBlank()
                                    ? ""
                                    : "\n"
                                      + details.getGuideDesignation()
                    )
            );

            guideRun.setFontFamily(
                    format().getDefaultFont()
            );

            guideRun.setFontSize(scaledFontSize(11, layout.getBodyScale()));

            // -------------------------------------------------
            // GUIDE SIGNATURE
            // -------------------------------------------------

            if (details.getGuideSignatureAssetId() != null
                    && !details.getGuideSignatureAssetId().isBlank()) {

                addAssetImage(
                        document,
                        jobId,
                        details.getGuideSignatureAssetId(),
                        95,
                        45
                );
            }

            // -------------------------------------------------
            // HOD
            // -------------------------------------------------

            if (details.getHodName() != null
                    && !details.getHodName().isBlank()) {

                XWPFParagraph hodParagraph =
                        document.createParagraph();

                setCoverAlignment(hodParagraph, layout.getBodyX());

                hodParagraph.setSpacingBefore(250);

                XWPFRun hodRun =
                        hodParagraph.createRun();

                hodRun.setText(
                        "HOD\n"
                                + details.getHodName()
                                + (
                                details.getHodDesignation() == null
                                        || details.getHodDesignation().isBlank()
                                        ? ""
                                        : "\n"
                                          + details.getHodDesignation()
                        )
                );

                hodRun.setFontFamily(
                        format().getDefaultFont()
                );

                hodRun.setFontSize(scaledFontSize(11, layout.getBodyScale()));
            }

            // -------------------------------------------------
            // HOD SIGNATURE
            // -------------------------------------------------

            if (details.getHodSignatureAssetId() != null
                    && !details.getHodSignatureAssetId().isBlank()) {

                addAssetImage(
                        document,
                        jobId,
                        details.getHodSignatureAssetId(),
                        95,
                        45
                );
            }

            // -------------------------------------------------
            // SUBMISSION DATE
            // -------------------------------------------------

            if (details.getSubmissionDate() != null
                    && !details.getSubmissionDate().isBlank()) {

                XWPFParagraph dateParagraph =
                        document.createParagraph();

                setCoverAlignment(dateParagraph, layout.getBodyX());

                dateParagraph.setSpacingBefore(350);

                XWPFRun dateRun =
                        dateParagraph.createRun();

                dateRun.setText(
                        details.getSubmissionDate()
                );

                dateRun.setFontFamily(
                        format().getDefaultFont()
                );

                dateRun.setFontSize(scaledFontSize(10, layout.getBodyScale()));
            }
        }

        // -----------------------------------------------------
        // GENERATED DOCUMENTATION TEXT
        // -----------------------------------------------------

        XWPFParagraph generatedParagraph =
                document.createParagraph();

        generatedParagraph.setAlignment(
                ParagraphAlignment.CENTER
        );

        generatedParagraph.setSpacingBefore(2000);

        XWPFRun generatedRun =
                generatedParagraph.createRun();

        generatedRun.setText(
                "Generated Project Documentation"
        );

        generatedRun.setItalic(true);

        generatedRun.setFontFamily(
                format().getDefaultFont()
        );

        generatedRun.setFontSize(11);

        addPageBreak(document);
    }

    // =========================================================
    // ASSET IMAGE
    // =========================================================

    private void addAssetImage(
            XWPFDocument document,
            String jobId,
            String assetId,
            int widthPx,
            int heightPx) {
        addAssetImage(document, jobId, assetId, widthPx, heightPx, null, 0, 50, 31, 1, 50, 50);
    }

    private void addAssetImage(
            XWPFDocument document,
            String jobId,
            String assetId,
            int widthPx,
            int heightPx,
            CoverLayout layout,
            int spacingBefore,
            double horizontalPosition,
            double verticalPosition,
            double zoom,
            double cropX,
            double cropY) {
        try {

            Path directory =
                    Path.of(
                            storageRoot == null
                                    || storageRoot.isBlank()
                                    ? "generated"
                                    : storageRoot,
                            "jobs",
                            jobId,
                            "assets"
                    );

            Path image;

            try (var stream =
                         Files.list(directory)) {

                image =
                        stream
                                .filter(Files::isRegularFile)
                                .filter(
                                        p -> p.getFileName()
                                                .toString()
                                                .startsWith(
                                                        assetId + "."
                                                )
                                )
                                .findFirst()
                                .orElse(null);
            }

            if (image == null) {
                return;
            }

            String lower =
                    image.getFileName()
                            .toString()
                            .toLowerCase(
                                    java.util.Locale.ROOT
                            );

            int type =
                    lower.endsWith(".png")
                            ? Document.PICTURE_TYPE_PNG
                            : Document.PICTURE_TYPE_JPEG;

            XWPFParagraph paragraph =
                    document.createParagraph();

            setCoverAlignment(paragraph, horizontalPosition);
            if (layout != null) {
                paragraph.setSpacingBefore(coverSpacing(spacingBefore, verticalPosition, 31));
            }

            try (InputStream input =
                         Files.newInputStream(image)) {

                var picture = paragraph.createRun()
                        .addPicture(input, type, image.getFileName().toString(),
                                Units.toEMU(widthPx), Units.toEMU(heightPx));
                var sourceImage = javax.imageio.ImageIO.read(image.toFile());
                double aspectRatio = sourceImage == null
                        ? 1
                        : (double) sourceImage.getWidth() / sourceImage.getHeight();
                double baseHorizontalCrop = Math.max(0, 1 - 1 / aspectRatio);
                double baseVerticalCrop = Math.max(0, 1 - aspectRatio);
                double horizontalCrop = 1 - (1 - baseHorizontalCrop) / zoom;
                double verticalCrop = 1 - (1 - baseVerticalCrop) / zoom;
                if (horizontalCrop > 0 || verticalCrop > 0) {
                    var crop = picture.getCTPicture().getBlipFill().addNewSrcRect();
                    crop.setL((int) Math.round(horizontalCrop * cropX * 1000));
                    crop.setR((int) Math.round(horizontalCrop * (100 - cropX) * 1000));
                    crop.setT((int) Math.round(verticalCrop * cropY * 1000));
                    crop.setB((int) Math.round(verticalCrop * (100 - cropY) * 1000));
                }
            }

        } catch (Exception ignored) {

            logger.debug(
                    "Optional front-matter asset could not be rendered: {}",
                    assetId
            );
        }
    }

    private void setCoverAlignment(XWPFParagraph paragraph, double horizontalPosition) {
        if (horizontalPosition < 35) {
            paragraph.setAlignment(ParagraphAlignment.LEFT);
        } else if (horizontalPosition > 65) {
            paragraph.setAlignment(ParagraphAlignment.RIGHT);
        } else {
            paragraph.setAlignment(ParagraphAlignment.CENTER);
        }
    }

    private int coverSpacing(int baseTwips, double position, double defaultPosition) {
        int adjustment = (int) Math.round((position - defaultPosition) * 80);
        return Math.max(0, baseTwips + adjustment);
    }

    private int scaledFontSize(int baseSize, double scale) {
        double safeScale = Double.isFinite(scale) ? Math.max(0.7, Math.min(1.5, scale)) : 1;
        return Math.max(8, Math.min(42, (int) Math.round(baseSize * safeScale)));
    }

    // =========================================================
    // TABLE OF CONTENTS
    // =========================================================

    private void addTableOfContents(
            XWPFDocument document) {

        XWPFParagraph heading =
                document.createParagraph();

        heading.setAlignment(
                ParagraphAlignment.CENTER
        );

        XWPFRun headingRun =
                heading.createRun();

        heading.setStyle("Title");

        headingRun.setText(
                "Table of Contents"
        );

        headingRun.setBold(true);

        headingRun.setFontFamily(
                format().getDefaultFont()
        );

        headingRun.setFontSize(16);

        XWPFParagraph tocParagraph =
                document.createParagraph();

        addTocField(tocParagraph);

        XWPFParagraph note =
                document.createParagraph();

        note.setAlignment(
                ParagraphAlignment.CENTER
        );

        XWPFRun noteRun =
                note.createRun();

        noteRun.setText(
                "Right-click the table and select "
                        + "\"Update Field\" in Microsoft Word."
        );

        noteRun.setItalic(true);

        noteRun.setFontFamily(
                format().getDefaultFont()
        );

        noteRun.setFontSize(9);

        addPageBreak(document);
    }

    private void addTocField(
            XWPFParagraph paragraph) {

        addSimpleField(
                paragraph,
                "TOC \\o \"1-3\" \\h \\z \\u"
        );
    }

    private void addSimpleField(
            XWPFParagraph paragraph,
            String instruction) {

        XWPFRun run =
                paragraph.createRun();

        XmlCursor cursor =
                run.getCTR().newCursor();

        cursor.toEndToken();

        cursor.beginElement(
                "fldSimple",
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        );

        cursor.insertAttributeWithValue(
                "instr",
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                instruction
        );

        cursor.toParent();

        cursor.dispose();
    }

    // =========================================================
    // DOCUMENT INDEXES
    // =========================================================

    private void addDocumentIndexes(
            XWPFDocument document,
            GeneratedDocumentation documentation) {

        List<GeneratedSection> sections =
                documentation.getSections();

        if (sections == null) {
            return;
        }

        boolean hasFigures =
                sections.stream()
                        .anyMatch(
                                section ->
                                        section != null
                                                && section.getDiagramImage() != null
                        );

        boolean hasTables =
                sections.stream()
                        .anyMatch(
                                section ->
                                        section != null
                                                && section.getTables() != null
                                                && !section.getTables().isEmpty()
                        );

        if (hasFigures
                && isEnabled(
                documentation,
                "list_of_figures"
        )) {

            addIndexPage(
                    document,
                    "List of Figures",
                    "TOC \\h \\z \\c \"Figure\""
            );
        }

        if (hasTables
                && isEnabled(
                documentation,
                "list_of_tables"
        )) {

            addIndexPage(
                    document,
                    "List of Tables",
                    "TOC \\h \\z \\c \"Table\""
            );
        }
    }

    private boolean isEnabled(
            GeneratedDocumentation documentation,
            String sectionId) {

        List<String> enabled =
                documentation.getEnabledSectionIds();

        return enabled == null
                || enabled.isEmpty()
                || enabled.contains(sectionId);
    }

    private void addIndexPage(
            XWPFDocument document,
            String title,
            String fieldInstruction) {

        XWPFParagraph heading =
                document.createParagraph();

        heading.setStyle("Heading1");

        heading.createRun()
                .setText(title);

        XWPFParagraph index =
                document.createParagraph();

        addSimpleField(
                index,
                fieldInstruction
        );

        addPageBreak(document);
    }

    // =========================================================
    // SECTIONS
    // =========================================================

    private void addSections(
            XWPFDocument document,
            List<GeneratedSection> sections) {

        if (sections == null
                || sections.isEmpty()) {

            return;
        }

        boolean hasAddedSection = false;

        int[] numbering =
                new int[]{0, 0, 0};

        for (GeneratedSection section :
                sections) {

            if (section == null) {
                continue;
            }

            if (hasAddedSection
                    && determineHeadingLevel(
                    section.getLevel()
            ) == 1) {

                addPageBreak(document);
            }

            addSection(
                    document,
                    section,
                    numbering
            );

            hasAddedSection = true;
        }
    }

    private void addSection(
            XWPFDocument document,
            GeneratedSection section,
            int[] numbering) {

        int level =
                determineHeadingLevel(
                        section.getLevel()
                );

        String headingText =
                formatHeading(
                        section,
                        numbering
                );

        XWPFParagraph heading =
                document.createParagraph();

        if (heading.getCTP().getPPr() == null) {
            heading.getCTP().addNewPPr();
        }

        heading.setKeepNext(true);

        heading.setStyle(
                "Heading" + level
        );

        heading.setSpacingBefore(
                level == 1 ? 250 : 160
        );

        heading.setSpacingAfter(100);

        XWPFRun headingRun =
                heading.createRun();

        headingRun.setText(
                headingText
        );

        headingRun.setBold(true);

        headingRun.setFontFamily(
                format().getDefaultFont()
        );

        headingRun.setFontSize(
                headingFontSize(level)
        );

        addContent(
                document,
                section.getContent()
        );

        addImages(
                document,
                section.getImagePaths()
        );

        addTables(
                document,
                section.getTables()
        );

        if (section.getDiagramImage() != null) {

            boolean inserted =
                    addDiagram(
                            document,
                            section.getDiagramImage()
                    );

            section.setDiagramStatus(
                    inserted
                            ? "INSERTED"
                            : "INSERTION_FAILED"
            );

            logger.info(
                    "Diagram DOCX insertion: title={}, inserted={}",
                    section.getTitle(),
                    inserted
            );

            if (!inserted) {

                throw new IllegalStateException(
                        "Required diagram could not be inserted into the DOCX: "
                                + section.getTitle()
                );
            }

        } else if (
                !"NOT_REQUESTED".equals(
                        section.getDiagramStatus()
                )
                        && !"NOT_REQUIRED".equals(
                        section.getDiagramStatus()
                )) {

            logger.warn(
                    "Diagram DOCX insertion skipped: title={}, status={}",
                    section.getTitle(),
                    section.getDiagramStatus()
            );
        }
    }

    // =========================================================
    // HEADING FORMATTING
    // =========================================================

    private String formatHeading(
            GeneratedSection section,
            int[] numbering) {

        String title =
                safe(
                        section.getTitle(),
                        "Untitled Section"
                );

        if (section.getLevel() == null
                || section.getLevel().startsWith("0")) {

            return title;
        }

        int level =
                Math.max(
                        1,
                        Math.min(
                                3,
                                determineHeadingLevel(
                                        section.getLevel()
                                )
                        )
                );

        for (int index = 0;
             index < level;
             index++) {

            if (numbering[index] == 0) {

                numbering[index] = 1;

            } else if (
                    index == level - 1
            ) {

                numbering[index]++;
            }
        }

        for (int index = level;
             index < numbering.length;
             index++) {

            numbering[index] = 0;
        }

        StringBuilder prefix =
                new StringBuilder();

        for (int index = 0;
             index < level;
             index++) {

            if (numbering[index] == 0) {
                continue;
            }

            if (prefix.length() > 0) {
                prefix.append('.');
            }

            prefix.append(
                    numbering[index]
            );
        }

        String normalizedTitle =
                title
                        .replaceFirst(
                                "(?i)^chapter\\s+\\d+\\s*[-:.)—–]?\\s*",
                                ""
                        )
                        .replaceFirst(
                                "^\\d+(?:\\.\\d+)*\\.?\\s+",
                                ""
                        );

        if (normalizedTitle.isBlank()) {
            normalizedTitle = title;
        }

        return prefix
                + " "
                + normalizedTitle;
    }

    // =========================================================
    // IMAGES
    // =========================================================

    private void addImages(
            XWPFDocument document,
            List<String> imagePaths) {

        if (imagePaths == null) {
            return;
        }

        for (String imagePath :
                imagePaths) {

            if (imagePath == null
                    || imagePath.isBlank()) {

                continue;
            }

            Path path =
                    Path.of(imagePath);

            if (!Files.isRegularFile(path)) {

                throw new IllegalStateException(
                        "Image file not found: "
                                + imagePath
                );
            }

            try (InputStream input =
                         Files.newInputStream(path)) {

                XWPFParagraph paragraph =
                        document.createParagraph();

                paragraph.setAlignment(
                        ParagraphAlignment.CENTER
                );

                XWPFRun run =
                        paragraph.createRun();

                int pictureType =
                        pictureType(path);

                run.addPicture(
                        input,
                        pictureType,
                        path.getFileName().toString(),
                        Units.toEMU(430),
                        Units.toEMU(260)
                );

            } catch (Exception e) {

                throw new IllegalStateException(
                        "Unable to insert image: "
                                + imagePath,
                        e
                );
            }
        }
    }

    private int pictureType(
            Path path) {

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        if (name.endsWith(".jpg")
                || name.endsWith(".jpeg")) {

            return Document.PICTURE_TYPE_JPEG;
        }

        if (name.endsWith(".gif")) {
            return Document.PICTURE_TYPE_GIF;
        }

        return Document.PICTURE_TYPE_PNG;
    }

    // =========================================================
    // TABLES
    // =========================================================

    private void addTables(
            XWPFDocument document,
            List<GeneratedTable> tables) {

        if (tables == null) {
            return;
        }

        for (GeneratedTable generatedTable :
                tables) {

            if (generatedTable == null
                    || generatedTable.getColumns() == null
                    || generatedTable.getColumns().isEmpty()
                    || generatedTable.getRows() == null
                    || generatedTable.getRows().isEmpty()) {

                continue;
            }

            int columnCount =
                    generatedTable
                            .getColumns()
                            .size();

            XWPFTable table =
                    document.createTable(
                            generatedTable
                                    .getRows()
                                    .size()
                                    + 1,
                            columnCount
                    );

            table.setStyleID(
                    "TableGrid"
            );

            fillTableRow(
                    table.getRow(0),
                    generatedTable.getColumns(),
                    true
            );

            for (int rowIndex = 0;
                 rowIndex < generatedTable
                         .getRows()
                         .size();
                 rowIndex++) {

                fillTableRow(
                        table.getRow(
                                rowIndex + 1
                        ),
                        generatedTable
                                .getRows()
                                .get(rowIndex),
                        false
                );
            }

            addCaption(
                    document,
                    "Table",
                    safe(
                            generatedTable.getTitle(),
                            "Evidence Table"
                    )
            );
        }
    }

    private void fillTableRow(
            XWPFTableRow row,
            List<String> values,
            boolean header) {

        for (int cellIndex = 0;
             cellIndex < row
                     .getTableCells()
                     .size();
             cellIndex++) {

            XWPFTableCell cell =
                    row.getCell(cellIndex);

            String value =
                    cellIndex < values.size()
                            ? values.get(cellIndex)
                            : "";

            cell.setText(
                    value == null
                            ? ""
                            : value
            );

            for (XWPFParagraph paragraph :
                    cell.getParagraphs()) {

                for (XWPFRun run :
                        paragraph.getRuns()) {

                    run.setFontFamily(
                            format().getDefaultFont()
                    );

                    run.setFontSize(10);

                    run.setBold(header);
                }
            }
        }
    }

    // =========================================================
    // CONTENT
    // =========================================================

    private void addContent(
            XWPFDocument document,
            String content) {

        if (content == null
                || content.isBlank()) {

            return;
        }

        String normalized =
                content
                        .replace("\r\n", "\n")
                        .replace("\r", "\n");

        String[] blocks =
                normalized.split(
                        "\\n\\s*\\n"
                );

        for (String block :
                blocks) {

            String text =
                    block.trim();

            if (text.isBlank()) {
                continue;
            }

            if (isBulletBlock(text)) {

                addBulletBlock(
                        document,
                        text
                );

            } else if (isNumberedBlock(text)) {

                addNumberedBlock(
                        document,
                        text
                );

            } else {

                addBodyParagraph(
                        document,
                        text
                );
            }
        }
    }

    private void addBodyParagraph(
            XWPFDocument document,
            String text) {

        XWPFParagraph paragraph =
                document.createParagraph();

        paragraph.setAlignment(
                ParagraphAlignment.BOTH
        );

        paragraph.setSpacingAfter(150);

        paragraph.setSpacingBetween(
                format().getLineSpacing()
        );

        XWPFRun run =
                paragraph.createRun();

        run.setText(
                cleanText(text)
        );

        run.setFontFamily(
                format().getDefaultFont()
        );

        run.setFontSize(
                format().getDefaultFontSize()
        );
    }

    // =========================================================
    // BULLETS
    // =========================================================

    private void addBulletBlock(
            XWPFDocument document,
            String text) {

        String[] lines =
                text.split("\\n");

        for (String line :
                lines) {

            String clean =
                    line.trim();

            if (clean.isBlank()) {
                continue;
            }

            clean =
                    clean.replaceFirst(
                            "^[\\-*•]\\s*",
                            ""
                    );

            XWPFParagraph paragraph =
                    document.createParagraph();

            paragraph.setIndentationLeft(720);

            paragraph.setFirstLineIndent(
                    -360
            );

            paragraph.setSpacingAfter(80);

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    "• "
                            + cleanText(clean)
            );

            run.setFontFamily(
                    format().getDefaultFont()
            );

            run.setFontSize(
                    format().getDefaultFontSize()
            );
        }
    }

    // =========================================================
    // NUMBERED LIST
    // =========================================================

    private void addNumberedBlock(
            XWPFDocument document,
            String text) {

        String[] lines =
                text.split("\\n");

        for (String line :
                lines) {

            String clean =
                    line.trim();

            if (clean.isBlank()) {
                continue;
            }

            XWPFParagraph paragraph =
                    document.createParagraph();

            paragraph.setIndentationLeft(720);

            paragraph.setFirstLineIndent(
                    -360
            );

            paragraph.setSpacingAfter(80);

            XWPFRun run =
                    paragraph.createRun();

            run.setText(
                    cleanText(clean)
            );

            run.setFontFamily(
                    format().getDefaultFont()
            );

            run.setFontSize(
                    format().getDefaultFontSize()
            );
        }
    }

    // =========================================================
    // DIAGRAM
    // =========================================================

    private boolean addDiagram(
            XWPFDocument document,
            DiagramImage diagram) {

        if (diagram.getData() == null
                || diagram.getData().length == 0) {

            logger.warn(
                    "Diagram DOCX insertion skipped: title={}, reason=empty image data",
                    diagram.getTitle()
            );

            return false;
        }

        XWPFParagraph imageParagraph =
                document.createParagraph();

        imageParagraph.setAlignment(
                ParagraphAlignment.CENTER
        );

        try {

            XWPFRun imageRun =
                    imageParagraph.createRun();

            imageRun.addPicture(
                    new ByteArrayInputStream(
                            diagram.getData()
                    ),
                    pictureType(
                            diagram.getFormat()
                    ),
                    safe(
                            diagram.getTitle(),
                            "Diagram"
                    ),
                    Units.toEMU(6.0),
                    Units.toEMU(3.8)
            );

            addCaption(
                    document,
                    "Figure",
                    safe(
                            diagram.getTitle(),
                            "System Diagram"
                    )
            );

            return true;

        } catch (Exception e) {

            logger.error(
                    "Diagram DOCX insertion failed: title={}",
                    diagram.getTitle(),
                    e
            );

            return false;
        }
    }

    private void addCaption(
            XWPFDocument document,
            String type,
            String title) {

        XWPFParagraph caption =
                document.createParagraph();

        caption.setAlignment(
                ParagraphAlignment.CENTER
        );

        caption.setSpacingAfter(180);

        caption.setStyle("Caption");

        XWPFRun captionRun =
                caption.createRun();

        captionRun.setText(
                type + " "
        );

        addSequenceField(
                captionRun,
                type
        );

        captionRun.setText(
                ": " + title
        );

        captionRun.setItalic(true);

        captionRun.setFontFamily(
                format().getDefaultFont()
        );

        captionRun.setFontSize(10);
    }

    private void addSequenceField(
            XWPFRun run,
            String type) {

        run.getCTR()
                .addNewFldChar()
                .setFldCharType(
                        STFldCharType.BEGIN
                );

        run.getCTR()
                .addNewInstrText()
                .setStringValue(
                        " SEQ " + type + " \\* ARABIC "
                );

        run.getCTR()
                .addNewFldChar()
                .setFldCharType(
                        STFldCharType.SEPARATE
                );

        run.setText("1");

        run.getCTR()
                .addNewFldChar()
                .setFldCharType(
                        STFldCharType.END
                );
    }

    // =========================================================
    // FOOTER
    // =========================================================

    private void addFooter(
            XWPFDocument document) {

        XWPFFooter footer =
                document.createFooter(
                        HeaderFooterType.DEFAULT
                );

        XWPFParagraph paragraph =
                footer.getParagraphArray(0);

        if (paragraph == null) {
            paragraph =
                    footer.createParagraph();
        }

        paragraph.setAlignment(
                ParagraphAlignment.CENTER
        );

        XWPFRun run =
                paragraph.createRun();

        run.setText(
                "Generated Project Documentation  |  Page "
        );

        run.setFontFamily(
                format().getDefaultFont()
        );

        run.setFontSize(9);

        if (format().isPageNumbers()) {
            addPageNumberField(run);
        }
    }

    // =========================================================
    // PAGE NUMBER FIELD
    // =========================================================

    private void addPageNumberField(
            XWPFRun run) {

        run.getCTR()
                .addNewFldChar()
                .setFldCharType(
                        STFldCharType.BEGIN
                );

        run.getCTR()
                .addNewInstrText()
                .setStringValue(
                        " PAGE "
                );

        run.getCTR()
                .addNewFldChar()
                .setFldCharType(
                        STFldCharType.END
                );
    }

    // =========================================================
    // PAGE DIMENSIONS
    // =========================================================

    private long[] pageDimensions(
            String pageSizeName) {

        return switch (
                pageSizeName == null
                        ? "A4"
                        : pageSizeName
                        .trim()
                        .toUpperCase(
                                java.util.Locale.ROOT
                        )
                ) {

            case "LETTER" ->
                    new long[]{
                            12240,
                            15840
                    };

            case "LEGAL" ->
                    new long[]{
                            12240,
                            20160
                    };

            case "A5" ->
                    new long[]{
                            8391,
                            11906
                    };

            default ->
                    new long[]{
                            11906,
                            16838
                    };
        };
    }

    private DocumentFormatDefinition format() {
        return activeFormat.get();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void addPageBreak(
            XWPFDocument document) {

        XWPFParagraph paragraph =
                document.createParagraph();

        paragraph.createRun()
                .addBreak(
                        BreakType.PAGE
                );
    }

    private boolean isBulletBlock(
            String text) {

        return text.lines()
                .anyMatch(
                        line ->
                                line.trim()
                                        .matches(
                                                "^[\\-*•]\\s+.*"
                                        )
                );
    }

    private boolean isNumberedBlock(
            String text) {

        return text.lines()
                .anyMatch(
                        line ->
                                line.trim()
                                        .matches(
                                                "^\\d+[.)]\\s+.*"
                                        )
                );
    }

    private String cleanText(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim();
    }

    private int determineHeadingLevel(
            String level) {

        if (level == null
                || level.isBlank()) {

            return 1;
        }

        try {

            String digits =
                    level.replaceAll(
                            "[^0-9]",
                            ""
                    );

            if (digits.isBlank()) {
                return 1;
            }

            int parsed =
                    Integer.parseInt(digits);

            if (parsed <= 1) {
                return 1;
            }

            if (parsed == 2) {
                return 2;
            }

            return 3;

        } catch (Exception ignored) {

            return 1;
        }
    }

    private int headingFontSize(
            int level) {

        return switch (level) {

            case 1 ->
                    format().getHeading1FontSize();

            case 2 ->
                    format().getHeading2FontSize();

            default ->
                    format().getHeading3FontSize();
        };
    }

    private int pictureType(
            String format) {

        if (format == null) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }

        return switch (
                format.toLowerCase()
                ) {

            case "png" ->
                    XWPFDocument.PICTURE_TYPE_PNG;

            case "jpg", "jpeg" ->
                    XWPFDocument.PICTURE_TYPE_JPEG;

            default ->
                    XWPFDocument.PICTURE_TYPE_PNG;
        };
    }

    private String safe(
            String value,
            String fallback) {

        if (value == null
                || value.isBlank()) {

            return fallback;
        }

        return value;
    }
}