package book.example.services;

/**
 * Removes comments while preserving strings and line breaks. This is deliberately
 * conservative: the raw repository content remains the authoritative evidence;
 * the sanitized view is used only for structural detection so comments cannot
 * masquerade as executable/configuration constructs.
 */
public final class SourceTextSanitizer {
    private SourceTextSanitizer() {}

    public static String stripComments(String source, String fileType) {
        if (source == null || source.isEmpty()) return source;
        String type = fileType == null ? "" : fileType.toUpperCase(java.util.Locale.ROOT);
        boolean html = type.contains("HTML") || type.contains("XML") || type.contains("VUE") || type.contains("SVELTE");
        boolean hashComments = type.contains("PYTHON") || type.contains("RUBY") || type.contains("SHELL") || type.contains("POWERSHELL") || type.contains("WINDOWS_SCRIPT") || type.contains("YAML") || type.contains("YML");

        StringBuilder out = new StringBuilder(source.length());
        boolean line = false, block = false, htmlComment = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char n = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (htmlComment) {
                if (c == '-' && n == '-' && i + 2 < source.length() && source.charAt(i + 2) == '>') {
                    out.append("   "); i += 2; htmlComment = false;
                } else if (c == '\n' || c == '\r') out.append(c);
                else out.append(' ');
                continue;
            }
            if (line) {
                if (c == '\n' || c == '\r') { line = false; out.append(c); }
                else out.append(' ');
                continue;
            }
            if (block) {
                if (c == '*' && n == '/') { out.append("  "); i++; block = false; }
                else if (c == '\n' || c == '\r') out.append(c);
                else out.append(' ');
                continue;
            }
            if (quote != 0) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (html && c == '<' && n == '!' && i + 3 < source.length() && source.startsWith("<!--", i)) {
                out.append("    "); i += 3; htmlComment = true; continue;
            }
            if (c == '/' && n == '/') { out.append("  "); i++; line = true; continue; }
            if (type.contains("SQL") && c == '-' && n == '-') { out.append("  "); i++; line = true; continue; }
            if (c == '/' && n == '*') { out.append("  "); i++; block = true; continue; }
            if (hashComments && c == '#' && (i == 0 || Character.isWhitespace(source.charAt(i - 1)))) { out.append(' '); line = true; continue; }
            if (c == '"' || c == '\'' || (c == '`' && !type.contains("SQL"))) quote = c;
            out.append(c);
        }
        return out.toString();
    }
}
