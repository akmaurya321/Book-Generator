package book.example.dto;

public class CoverLayout {

    private double titleX = 50;
    private double titleY = 15;
    private double titleScale = 1;
    private double logoX = 50;
    private double logoY = 31;
    private double logoScale = 1;
    private double logoZoom = 1;
    private double logoCropX = 50;
    private double logoCropY = 50;
    private double bodyX = 50;
    private double bodyY = 55;
    private double bodyScale = 1;

    public double getTitleX() { return titleX; }
    public void setTitleX(double titleX) { this.titleX = bounded(titleX, 50, 10, 90); }
    public double getTitleY() { return titleY; }
    public void setTitleY(double titleY) { this.titleY = bounded(titleY, 15, 6, 22); }
    public double getTitleScale() { return titleScale; }
    public void setTitleScale(double titleScale) { this.titleScale = bounded(titleScale, 1, 0.7, 1.5); }
    public double getLogoX() { return logoX; }
    public void setLogoX(double logoX) { this.logoX = bounded(logoX, 50, 10, 90); }
    public double getLogoY() { return logoY; }
    public void setLogoY(double logoY) { this.logoY = bounded(logoY, 31, 24, 43); }
    public double getLogoScale() { return logoScale; }
    public void setLogoScale(double logoScale) { this.logoScale = bounded(logoScale, 1, 0.5, 1.8); }
    public double getLogoZoom() { return logoZoom; }
    public void setLogoZoom(double logoZoom) { this.logoZoom = bounded(logoZoom, 1, 1, 2.5); }
    public double getLogoCropX() { return logoCropX; }
    public void setLogoCropX(double logoCropX) { this.logoCropX = bounded(logoCropX, 50, 0, 100); }
    public double getLogoCropY() { return logoCropY; }
    public void setLogoCropY(double logoCropY) { this.logoCropY = bounded(logoCropY, 50, 0, 100); }
    public double getBodyX() { return bodyX; }
    public void setBodyX(double bodyX) { this.bodyX = bounded(bodyX, 50, 10, 90); }
    public double getBodyY() { return bodyY; }
    public void setBodyY(double bodyY) { this.bodyY = bounded(bodyY, 55, 44, 68); }
    public double getBodyScale() { return bodyScale; }
    public void setBodyScale(double bodyScale) { this.bodyScale = bounded(bodyScale, 1, 0.7, 1.5); }

    private static double bounded(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
