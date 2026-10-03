package book.example.services;

import book.example.dto.DocumentFormatDefinition;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FormatAnalyzer {
    public record Analysis(
            DocumentFormatDefinition format,
            Map<String, Object> properties,
            String previewText) {}

    public Analysis analyze(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("A validated template file is required for format analysis.");
        }
        String filename = file.getFileName().toString().toLowerCase();
        try {
            if (filename.endsWith(".docx")) return analyzeDocx(file);
            if (filename.endsWith(".pdf")) return analyzePdf(file);
            throw new IllegalArgumentException("Format analysis supports DOCX and PDF templates.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("The template could not be analyzed.", exception);
        }
    }

    private Analysis analyzeDocx(Path file) throws IOException {
        DocumentFormatDefinition format = new DocumentFormatDefinition();
        Map<String, Object> properties = new LinkedHashMap<>();
        StringBuilder preview = new StringBuilder();
        Map<String, Integer> fonts = new HashMap<>();
        Map<Integer, Integer> fontSizes = new HashMap<>();
        int images = 0;
        int tables = 0;
        int headings = 0;
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
            var bodySection = document.getDocument().getBody().getSectPr();
            if (bodySection != null) {
                if (bodySection.isSetPgSz()) {
                    var size = bodySection.getPgSz();
                    int width = size.getW() == null ? 0 : Integer.parseInt(size.getW().toString());
                    int height = size.getH() == null ? 0 : Integer.parseInt(size.getH().toString());
                    format.setOrientation(width > height ? "LANDSCAPE" : "PORTRAIT");
                    format.setPageSize(pageSize(width, height));
                    properties.put("pageSizeConfidence", "HIGH");
                }
                if (bodySection.isSetPgMar()) {
                    var margins = bodySection.getPgMar();
                    if (margins.getTop() != null) format.setMarginTopTwips(Integer.parseInt(margins.getTop().toString()));
                    if (margins.getBottom() != null) format.setMarginBottomTwips(Integer.parseInt(margins.getBottom().toString()));
                    if (margins.getLeft() != null) format.setMarginLeftTwips(Integer.parseInt(margins.getLeft().toString()));
                    if (margins.getRight() != null) format.setMarginRightTwips(Integer.parseInt(margins.getRight().toString()));
                    properties.put("marginConfidence", "HIGH");
                }
            }
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                appendPreview(preview, text);
                if (paragraph.getStyle() != null && paragraph.getStyle().matches("(?i)heading[1-3]")) {
                    headings++;
                }
                for (XWPFRun run : paragraph.getRuns()) {
                    String font = run.getFontFamily();
                    if (font != null && !font.isBlank()) fonts.merge(font, Math.max(1, run.text().length()), Integer::sum);
                    if (run.getFontSize() > 0) fontSizes.merge(run.getFontSize(), Math.max(1, run.text().length()), Integer::sum);
                    images += run.getEmbeddedPictures().size();
                }
            }
            tables = document.getTables().size();
            if (!fonts.isEmpty()) {
                format.setDefaultFont(fonts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey());
            }
            if (!fontSizes.isEmpty()) {
                format.setDefaultFontSize(fontSizes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey());
            }
            properties.put("source", "DOCX_DOCUMENT_PROPERTIES");
            properties.put("fontConfidence", fonts.isEmpty() ? "LOW" : "MEDIUM");
            properties.put("headingCount", headings);
            properties.put("headingConfidence", "HIGH");
            properties.put("tableCount", tables);
            properties.put("imageCount", images);
            properties.put("pageNumbering", inspectPageNumbering(document));
            properties.put("pageNumberingConfidence", "MEDIUM");
            properties.put("pageCount", null);
        }
        return new Analysis(format, properties, preview.toString());
    }

    private Analysis analyzePdf(Path file) throws IOException {
        DocumentFormatDefinition format = new DocumentFormatDefinition();
        Map<String, Object> properties = new LinkedHashMap<>();
        StringBuilder preview = new StringBuilder();
        List<Float> fontSizes = new ArrayList<>();
        Map<String, Integer> fontNames = new HashMap<>();
        List<Float> leftEdges = new ArrayList<>();
        List<Float> rightEdges = new ArrayList<>();
        List<Float> topEdges = new ArrayList<>();
        List<Float> bottomEdges = new ArrayList<>();
        int[] inferredHeadings = {0};
        try (var pdf = Loader.loadPDF(file.toFile())) {
            if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000) {
                throw new IllegalArgumentException("Templates must contain between 1 and 1,000 pages.");
            }
            var first = pdf.getPage(0).getCropBox();
            float width = first.getWidth();
            float height = first.getHeight();
            format.setOrientation(width > height ? "LANDSCAPE" : "PORTRAIT");
            format.setPageSize(pageSize(Math.round(width * 20), Math.round(height * 20)));
            properties.put("pageSizeConfidence", "MEDIUM");
            properties.put("pageCount", pdf.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) throws IOException {
                    appendPreview(preview, text);
                    for (TextPosition position : positions) {
                        float size = position.getFontSizeInPt();
                        if (size > 0) fontSizes.add(size);
                        if (position.getFont() != null) fontNames.merge(position.getFont().getName(), 1, Integer::sum);
                        if (position.getPageWidth() > 0 && position.getPageHeight() > 0) {
                            leftEdges.add(position.getXDirAdj());
                            rightEdges.add(position.getPageWidth() - position.getXDirAdj() - position.getWidthDirAdj());
                            topEdges.add(position.getYDirAdj() - position.getHeightDir());
                            bottomEdges.add(position.getPageHeight() - position.getYDirAdj());
                        }
                    }
                    if (!text.isBlank() && !positions.isEmpty()) {
                        float size = positions.stream().map(TextPosition::getFontSizeInPt).max(Float::compare).orElse(0f);
                        if (size >= 15) inferredHeadings[0]++;
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(pdf.getNumberOfPages());
            stripper.getText(pdf);

            if (!fontNames.isEmpty()) {
                String font = fontNames.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                format.setDefaultFont(normalizePdfFont(font));
                properties.put("fontFamilyConfidence", "LOW");
            } else {
                properties.put("fontFamilyConfidence", "LOW");
            }
            if (!fontSizes.isEmpty()) {
                format.setDefaultFontSize(Math.round(median(fontSizes)));
                properties.put("fontSizeConfidence", "MEDIUM");
            } else {
                properties.put("fontSizeConfidence", "LOW");
            }
            if (!leftEdges.isEmpty()) {
                float twipsPerPoint = 20f;
                format.setMarginLeftTwips(Math.max(0, Math.round(median(leftEdges) * twipsPerPoint)));
                format.setMarginRightTwips(Math.max(0, Math.round(median(rightEdges) * twipsPerPoint)));
                format.setMarginTopTwips(Math.max(0, Math.round(median(topEdges) * twipsPerPoint)));
                format.setMarginBottomTwips(Math.max(0, Math.round(median(bottomEdges) * twipsPerPoint)));
                properties.put("marginConfidence", "LOW");
            } else {
                properties.put("marginConfidence", "LOW");
            }
            properties.put("source", "PDF_TEXT_AND_PAGE_GEOMETRY_INFERENCE");
            properties.put("inferredHeadingCount", inferredHeadings[0]);
            properties.put("headingConfidence", "LOW");
            properties.put("tableCount", null);
            properties.put("tableConfidence", "LOW");
            properties.put("imageCount", countImages(pdf));
            properties.put("imageConfidence", "MEDIUM");
            properties.put("pageNumbering", preview.toString().matches("(?s).*\\b(?:Page\\s+)?\\d+\\s*(?:of\\s+\\d+)?\\b.*"));
            properties.put("pageNumberingConfidence", "LOW");
            properties.put("fontSizes", fontSizes.stream().distinct().sorted(Comparator.naturalOrder()).limit(12).toList());
            properties.put("warning", "PDF styles and layout are inferred from rendered pages; original Word styles cannot be recovered with certainty.");
        }
        return new Analysis(format, properties, preview.toString());
    }

    private String pageSize(int widthTwips, int heightTwips) {
        int width = Math.min(widthTwips, heightTwips);
        int height = Math.max(widthTwips, heightTwips);
        if (Math.abs(width - 11906) < 700 && Math.abs(height - 16838) < 900) return "A4";
        if (Math.abs(width - 12240) < 700 && Math.abs(height - 15840) < 900) return "LETTER";
        return "A4";
    }

    private boolean inspectPageNumbering(XWPFDocument document) {
        return document.getFooterList().stream()
                .flatMap(footer -> footer.getParagraphs().stream())
                .anyMatch(paragraph -> paragraph.getRuns().stream()
                        .anyMatch(run -> run.getCTR().getFldCharList().size() > 0
                                || run.text().matches("(?i).*\\b(page|pagina)\\b.*")));
    }

    private int countImages(org.apache.pdfbox.pdmodel.PDDocument pdf) throws IOException {
        int images = 0;
        for (var page : pdf.getPages()) {
            if (page.getResources() == null) continue;
            for (var name : page.getResources().getXObjectNames()) {
                if (page.getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                    images++;
                }
            }
        }
        return images;
    }

    private float median(List<Float> values) {
        List<Float> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2f : sorted.get(middle);
    }

    private String normalizePdfFont(String font) {
        String normalized = font.replaceAll("^[A-Z]{6}\\+", "").replaceAll("[-,].*$", "").trim();
        if (normalized.isBlank() || normalized.equalsIgnoreCase("unknown")) return "Times New Roman";
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private void appendPreview(StringBuilder preview, String text) {
        if (text == null || text.isBlank() || preview.length() >= 6000) return;
        if (!preview.isEmpty()) preview.append('\n');
        preview.append(text, 0, Math.min(text.length(), 6000 - preview.length()));
    }
}
