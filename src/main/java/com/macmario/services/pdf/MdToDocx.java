package com.macmario.services.pdf;

import com.macmario.general.Version;
import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class MdToDocx extends Version {

    private record RunStyle(boolean bold, boolean italic, boolean strike) {
        static final RunStyle NORMAL = new RunStyle(false, false, false);
        RunStyle asBold()   { return new RunStyle(true, italic, strike); }
        RunStyle asItalic() { return new RunStyle(bold, true, strike); }
        RunStyle asStrike() { return new RunStyle(bold, italic, true); }
    }

    private final File inFile;
    private final File outFile;

    MdToDocx(String in, String out) {
        inFile  = new File(in);
        outFile = new File(out);
    }

    boolean convert() {
        if (!inFile.isFile()) { log(1, "convert: not found — " + inFile); return false; }
        log(1, "convert: reading " + inFile);
        try {
            String raw   = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
            String md    = stripFrontMatter(raw)[0];
            MutableDataSet opts = new MutableDataSet();
            opts.set(Parser.EXTENSIONS, Arrays.asList(
                    TablesExtension.create(), StrikethroughExtension.create(), TaskListExtension.create()));
            Node docNode = Parser.builder(opts).build().parse(md);
            try (XWPFDocument xdoc = new XWPFDocument()) {
                setPageMargins(xdoc);
                renderChildren(xdoc, docNode, 0);
                File parent = outFile.getAbsoluteFile().getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) { xdoc.write(fos); }
            }
            log(1, "convert: written " + outFile);
            return true;
        } catch (Exception e) {
            log(1, "convert: FAILED — " + e.getMessage());
            return false;
        }
    }

    private static void setPageMargins(XWPFDocument doc) {
        CTBody body   = doc.getDocument().getBody();
        CTSectPr sect = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar mar = sect.isSetPgMar()  ? sect.getPgMar()  : sect.addNewPgMar();
        BigInteger m  = BigInteger.valueOf(1134); // 2 cm in twips
        mar.setTop(m); mar.setBottom(m); mar.setLeft(m); mar.setRight(m);
    }

    private static void renderChildren(XWPFDocument doc, Node parent, int indent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) renderBlock(doc, n, indent);
    }

    private static void renderBlock(XWPFDocument doc, Node node, int indent) {
        if      (node instanceof Heading h)          renderHeading(doc, h);
        else if (node instanceof Paragraph p)        renderPara(doc, p, indent, RunStyle.NORMAL, null);
        else if (node instanceof FencedCodeBlock b)  renderCodeLines(doc, b.getContentChars().toString());
        else if (node instanceof IndentedCodeBlock b)renderCodeLines(doc, b.getContentChars().toString());
        else if (node instanceof BulletList bl)      renderList(doc, bl, indent, false);
        else if (node instanceof OrderedList ol)     renderList(doc, ol, indent, true);
        else if (node instanceof BlockQuote bq)      renderBlockQuote(doc, bq);
        else if (node instanceof ThematicBreak)      renderHR(doc);
        else if (node instanceof TableBlock tb)      renderTable(doc, tb);
        else                                         renderChildren(doc, node, indent);
    }

    private static void renderHeading(XWPFDocument doc, Heading h) {
        int[] ptSizes = {22, 18, 15, 13, 12, 11};
        int size = ptSizes[Math.min(h.getLevel() - 1, 5)];
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingBefore(h.getLevel() <= 2 ? 240 : 160);
        para.setSpacingAfter(80);
        if (h.getLevel() <= 2) addParaBottomBorder(para, "DDDDDD");
        for (Node c = h.getFirstChild(); c != null; c = c.getNext())
            renderInlineNode(para, c, RunStyle.NORMAL.asBold(), size);
    }

    private static void renderPara(XWPFDocument doc, Node inlineParent, int indent,
                                   RunStyle style, String prefix) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingAfter(80);
        if (indent > 0) para.setIndentationLeft(indent * 360);
        if (prefix != null) {
            XWPFRun pre = para.createRun();
            applyRun(pre, style, 11);
            pre.setText(prefix);
        }
        for (Node c = inlineParent.getFirstChild(); c != null; c = c.getNext())
            renderInlineNode(para, c, style, 11);
    }

    private static void renderCodeLines(XWPFDocument doc, String content) {
        for (String line : content.split("\n", -1)) {
            XWPFParagraph para = doc.createParagraph();
            para.setSpacingAfter(0);
            setParaShading(para, "F5F5F5");
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(9);
            run.setText(line);
        }
    }

    private static void renderList(XWPFDocument doc, Node listNode, int depth, boolean ordered) {
        int idx = 1;
        for (Node item = listNode.getFirstChild(); item != null; item = item.getNext()) {
            String mark;
            if (item instanceof TaskListItem tli) mark = tli.isItemDoneMarker() ? "☑ " : "☐ ";
            else mark = ordered ? (idx++) + ". " : "• ";
            Node para = item.getFirstChild();
            while (para != null && !(para instanceof Paragraph)) para = para.getNext();
            renderPara(doc, para != null ? para : item, depth + 1, RunStyle.NORMAL, mark);
            for (Node ic = item.getFirstChild(); ic != null; ic = ic.getNext()) {
                if      (ic instanceof BulletList nl) renderList(doc, nl, depth + 1, false);
                else if (ic instanceof OrderedList nl) renderList(doc, nl, depth + 1, true);
            }
        }
    }

    private static void renderBlockQuote(XWPFDocument doc, BlockQuote bq) {
        for (Node c = bq.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Paragraph p) renderPara(doc, p, 1, RunStyle.NORMAL.asItalic(), null);
            else renderBlock(doc, c, 1);
        }
    }

    private static void renderHR(XWPFDocument doc) {
        addParaBottomBorder(doc.createParagraph(), "CCCCCC");
    }

    private static void renderTable(XWPFDocument doc, TableBlock tableBlock) {
        List<List<Node>> rows    = new ArrayList<>();
        List<Boolean>    headers = new ArrayList<>();
        for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
            boolean head = section instanceof TableHead;
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) continue;
                List<Node> cells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext())
                    if (cell instanceof TableCell) cells.add(cell);
                rows.add(cells);
                headers.add(head);
            }
        }
        if (rows.isEmpty()) return;
        int numCols = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = doc.createTable(rows.size(), numCols);
        addTableBorders(table);
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow xRow  = table.getRow(r);
            RunStyle rowStyle  = Boolean.TRUE.equals(headers.get(r)) ? RunStyle.NORMAL.asBold() : RunStyle.NORMAL;
            List<Node> cells   = rows.get(r);
            for (int c = 0; c < cells.size() && c < numCols; c++) {
                XWPFTableCell xCell = xRow.getCell(c);
                XWPFParagraph cellP = xCell.getParagraphs().isEmpty()
                        ? xCell.addParagraph() : xCell.getParagraphs().get(0);
                TableCell md = (TableCell) cells.get(c);
                for (Node ch = md.getFirstChild(); ch != null; ch = ch.getNext())
                    if (ch instanceof Paragraph)
                        for (Node in = ch.getFirstChild(); in != null; in = in.getNext())
                            renderInlineNode(cellP, in, rowStyle, 11);
            }
        }
    }

    private static void renderInlineNode(XWPFParagraph para, Node node, RunStyle style, int fontSize) {
        if (node instanceof Text t) {
            XWPFRun run = para.createRun();
            applyRun(run, style, fontSize);
            run.setText(t.getChars().toString());
        } else if (node instanceof StrongEmphasis) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNext())
                renderInlineNode(para, c, style.asBold(), fontSize);
        } else if (node instanceof Emphasis) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNext())
                renderInlineNode(para, c, style.asItalic(), fontSize);
        } else if (node instanceof Strikethrough) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNext())
                renderInlineNode(para, c, style.asStrike(), fontSize);
        } else if (node instanceof Code code) {
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(9);
            run.setBold(style.bold());
            run.setStrikeThrough(style.strike());
            run.setText(code.getText().toString());
        } else if (node instanceof HardLineBreak || node instanceof SoftLineBreak) {
            para.createRun().addBreak();
        } else {
            for (Node c = node.getFirstChild(); c != null; c = c.getNext())
                renderInlineNode(para, c, style, fontSize);
        }
    }

    private static void applyRun(XWPFRun run, RunStyle style, int fontSize) {
        run.setBold(style.bold());
        run.setItalic(style.italic());
        run.setStrikeThrough(style.strike());
        run.setFontSize(fontSize);
    }

    private static void addParaBottomBorder(XWPFParagraph para, String color) {
        CTPPr ppr   = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTPBdr pBdr = ppr.isSetPBdr() ? ppr.getPBdr() : ppr.addNewPBdr();
        CTBorder b  = pBdr.addNewBottom();
        b.setVal(STBorder.SINGLE);
        b.setColor(color);
        b.setSz(BigInteger.valueOf(4));
    }

    private static void setParaShading(XWPFParagraph para, String fill) {
        CTPPr ppr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTShd shd = ppr.isSetShd() ? ppr.getShd() : ppr.addNewShd();
        shd.setFill(fill);
        shd.setVal(STShd.CLEAR);
    }

    private static void addTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        if (tblPr.getTblBorders() != null) return;
        CTTblBorders b = tblPr.addNewTblBorders();
        for (CTBorder border : new CTBorder[]{b.addNewTop(), b.addNewBottom(), b.addNewLeft(),
                b.addNewRight(), b.addNewInsideH(), b.addNewInsideV()}) {
            border.setVal(STBorder.SINGLE);
            border.setColor("CCCCCC");
            border.setSz(BigInteger.valueOf(4));
        }
    }

    static String[] stripFrontMatter(String content) {
        String title = null;
        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            int end = content.indexOf("\n---", 3);
            if (end > 0) {
                for (String line : content.substring(4, end).split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("title:")) title = t.substring(6).trim().replaceAll("^\"|\"$", "");
                }
                content = content.substring(end + 4).stripLeading();
            }
        }
        return new String[]{content, title};
    }
}
