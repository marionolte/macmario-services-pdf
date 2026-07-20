package com.macmario.services.pdf;

import com.macmario.general.Version;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Pattern;

class MdToPdf extends Version {

    private final File inFile;
    private final File outFile;

    MdToPdf(String in, String out) {
        inFile  = new File(in);
        outFile = new File(out);
    }

    boolean convert() {
        if (!inFile.isFile()) {
            log(1, "convert: input not found — " + inFile);
            return false;
        }
        log(1, "convert: reading " + inFile);
        try {
            String raw      = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
            String[] parts  = stripFrontMatter(raw);
            String markdown = parts[0];
            String title    = parts[1] != null ? parts[1] : inFile.getName();
            String xhtml    = markdownToXhtml(markdown, title);
            renderToPdf(xhtml);
            log(1, "convert: written " + outFile);
            return true;
        } catch (Exception e) {
            log(1, "convert: FAILED — " + e.getMessage());
            return false;
        }
    }

    private static String[] stripFrontMatter(String content) {
        String title = null;
        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            int end = content.indexOf("\n---", 3);
            if (end > 0) {
                String fm = content.substring(4, end);
                for (String line : fm.split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("title:")) {
                        title = t.substring(6).trim().replaceAll("^\"|\"$", "");
                    }
                }
                content = content.substring(end + 4).stripLeading();
            }
        }
        return new String[]{content, title};
    }

    private static String markdownToXhtml(String markdown, String title) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()
        ));
        Parser       parser   = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node         document = parser.parse(markdown);
        String       body     = closeVoidElements(renderer.render(document));

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                  <title>%s</title>
                  <style type="text/css">
                    @page { size: A4; margin: 2cm; }
                    body  { font-family: Helvetica, Arial, sans-serif; font-size: 11pt; line-height: 1.5; color: #1e1e1e; margin: 0; padding: 0; }
                    h1 { font-size: 22pt; color: #1a1a1a; border-bottom: 1.5pt solid #ddd; padding-bottom: 4pt; margin-top: 0; }
                    h2 { font-size: 17pt; color: #2a2a2a; border-bottom: 0.5pt solid #eee; padding-bottom: 2pt; }
                    h3 { font-size: 14pt; color: #333; }
                    h4,h5,h6 { font-size: 12pt; color: #444; }
                    p  { margin-top: 6pt; margin-bottom: 6pt; }
                    a  { color: #5B6EC1; }
                    code { font-family: Courier, "Courier New", monospace; font-size: 9pt; background-color: #f3f3f3; border: 0.5pt solid #e0e0e0; padding: 0pt 2pt; }
                    pre  { font-family: Courier, "Courier New", monospace; font-size: 9pt; background-color: #f5f5f5; border-left: 3pt solid #bbb; padding: 6pt 8pt; white-space: pre; }
                    pre code { background: none; border: none; padding: 0; }
                    blockquote { border-left: 3pt solid #ccc; margin-left: 0; margin-right: 0; padding-left: 10pt; color: #555; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 6pt; margin-bottom: 6pt; }
                    th { background-color: #f0f0f0; font-weight: bold; }
                    th,td { border: 0.5pt solid #ccc; padding: 4pt 8pt; text-align: left; }
                    ul,ol { padding-left: 18pt; }
                    li { margin-bottom: 2pt; }
                    hr { border: none; border-top: 0.5pt solid #ccc; margin: 12pt 0; }
                    del { text-decoration: line-through; color: #888; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, body);
    }

    private static final Pattern VOID_ELEMENT = Pattern.compile(
            "(?i)<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)(\\s[^>]*?)?(?<!/)>");

    private static String closeVoidElements(String html) {
        return VOID_ELEMENT.matcher(html).replaceAll(m -> {
            String attrs = m.group(2) != null ? m.group(2) : "";
            return "<" + m.group(1).toLowerCase() + attrs + "/>";
        });
    }

    private void renderToPdf(String xhtml) throws Exception {
        File parent = outFile.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        String baseUrl = inFile.getAbsoluteFile().getParentFile().toURI().toString();
        try (OutputStream os = new FileOutputStream(outFile)) {
            ITextRenderer r = new ITextRenderer();
            r.setDocumentFromString(xhtml, baseUrl);
            r.layout();
            r.createPDF(os);
        }
    }
}
