package book.example.dto;

public class DocumentFormatDefinition {
    private String pageSize = "A4";
    private String orientation = "PORTRAIT";
    private int marginTopTwips = 1440;
    private int marginBottomTwips = 1440;
    private int marginLeftTwips = 1440;
    private int marginRightTwips = 1440;
    private String defaultFont = "Times New Roman";
    private int defaultFontSize = 12;
    private double lineSpacing = 1.5;
    private int titleFontSize = 20;
    private int heading1FontSize = 16;
    private int heading2FontSize = 14;
    private int heading3FontSize = 12;
    private boolean pageNumbers = true;
    private boolean tableOfContents = true;
    private boolean figures = true;
    private boolean tables = true;

    public String getPageSize() { return pageSize; }
    public void setPageSize(String pageSize) { this.pageSize = pageSize; }
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    public int getMarginTopTwips() { return marginTopTwips; }
    public void setMarginTopTwips(int value) { this.marginTopTwips = value; }
    public int getMarginBottomTwips() { return marginBottomTwips; }
    public void setMarginBottomTwips(int value) { this.marginBottomTwips = value; }
    public int getMarginLeftTwips() { return marginLeftTwips; }
    public void setMarginLeftTwips(int value) { this.marginLeftTwips = value; }
    public int getMarginRightTwips() { return marginRightTwips; }
    public void setMarginRightTwips(int value) { this.marginRightTwips = value; }
    public String getDefaultFont() { return defaultFont; }
    public void setDefaultFont(String defaultFont) { this.defaultFont = defaultFont; }
    public int getDefaultFontSize() { return defaultFontSize; }
    public void setDefaultFontSize(int defaultFontSize) { this.defaultFontSize = defaultFontSize; }
    public double getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(double lineSpacing) { this.lineSpacing = lineSpacing; }
    public int getTitleFontSize() { return titleFontSize; }
    public void setTitleFontSize(int titleFontSize) { this.titleFontSize = titleFontSize; }
    public int getHeading1FontSize() { return heading1FontSize; }
    public void setHeading1FontSize(int value) { this.heading1FontSize = value; }
    public int getHeading2FontSize() { return heading2FontSize; }
    public void setHeading2FontSize(int value) { this.heading2FontSize = value; }
    public int getHeading3FontSize() { return heading3FontSize; }
    public void setHeading3FontSize(int value) { this.heading3FontSize = value; }
    public boolean isPageNumbers() { return pageNumbers; }
    public void setPageNumbers(boolean pageNumbers) { this.pageNumbers = pageNumbers; }
    public boolean isTableOfContents() { return tableOfContents; }
    public void setTableOfContents(boolean tableOfContents) { this.tableOfContents = tableOfContents; }
    public boolean isFigures() { return figures; }
    public void setFigures(boolean figures) { this.figures = figures; }
    public boolean isTables() { return tables; }
    public void setTables(boolean tables) { this.tables = tables; }
}
