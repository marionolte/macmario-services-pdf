package com.macmario.services.pdf;

import com.macmario.general.Version;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts a PDF to DOCX, preserving table layout detected via text-position analysis.
 *
 * Algorithm:
 *  1. Collect all text segments with (x, y, width) from each page.
 *  2. Group segments into lines (same Y ± LINE_SNAP pt).
 *  3. Group consecutive lines into blocks (Y gap < BLOCK_GAP pt).
 *  4. Within each block, split each line into columns at X gaps > COL_GAP pt.
 *  5. If ≥ 2 lines in the block have ≥ 2 columns → emit XWPFTable; else emit paragraph.
 */
class PdfToDocx extends Version {

    // Tuning constants (all in PDF user-space points, ~1/72 inch)
    private static final float LINE_SNAP  = 4f;   // chars within this Y-distance are on the same line
    private static final float BLOCK_GAP  = 18f;  // Y-gap that starts a new block (paragraph / table)
    private static final float COL_GAP    = 18f;  // X-gap that separates adjacent columns

    // ---- positioned text segment ------------------------------------------------

    record Seg(float x, float y, float w, String text) {}

    // ---- custom stripper that collects segments ---------------------------------

    static class Collector extends PDFTextStripper {
        final List<Seg> segs = new ArrayList<>();

        Collector() throws IOException { super(); }

        @Override
        protected void writeString(String text, List<TextPosition> tp) throws IOException {
            if (tp.isEmpty() || text.isBlank()) return;
            TextPosition f = tp.get(0);
            TextPosition l = tp.get(tp.size() - 1);
            float w = l.getX() + l.getWidth() - f.getX();
            segs.add(new Seg(f.getX(), f.getY(), Math.max(w, 1f), text));
        }
    }

    // ---- main entry point -------------------------------------------------------

    private final File inFile;
    private final File outFile;

    PdfToDocx(String in, String out) {
        inFile  = new File(in);
        outFile = new File(out);
    }

    boolean convert() {
        if (!inFile.isFile()) { log(1, "convert: not found — " + inFile); return false; }
        log(1, "convert: reading " + inFile);
        try (PDDocument pdf = Loader.loadPDF(inFile)) {
            Collector col = new Collector();
            col.setSortByPosition(true);
            try (XWPFDocument xdoc = new XWPFDocument()) {
                setPageMargins(xdoc);
                int pages = pdf.getNumberOfPages();
                for (int p = 1; p <= pages; p++) {
                    col.segs.clear();
                    col.setStartPage(p);
                    col.setEndPage(p);
                    col.getText(pdf);
                    List<Object> blocks = detectBlocks(groupIntoLines(col.segs));
                    for (Object block : blocks) {
                        if (block instanceof List<?> rows) emitTable(xdoc, cast(rows));
                        else emitParagraph(xdoc, (String) block);
                    }
                }
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

    // ---- step 1: group segments into lines by Y coordinate ----------------------

    private static List<List<Seg>> groupIntoLines(List<Seg> segs) {
        if (segs.isEmpty()) return Collections.emptyList();
        List<Seg> sorted = segs.stream()
                .sorted(Comparator.comparingDouble(Seg::y).thenComparingDouble(Seg::x))
                .toList();
        List<List<Seg>> result = new ArrayList<>();
        List<Seg> cur = new ArrayList<>();
        float refY = sorted.get(0).y();
        for (Seg s : sorted) {
            if (Math.abs(s.y() - refY) <= LINE_SNAP) {
                cur.add(s);
            } else {
                result.add(cur);
                cur = new ArrayList<>();
                cur.add(s);
                refY = s.y();
            }
        }
        if (!cur.isEmpty()) result.add(cur);
        return result;
    }

    // ---- step 2: detect table vs paragraph blocks -------------------------------

    /**
     * Returns a list of blocks where each block is either:
     *   - a {@code String}            → paragraph text
     *   - a {@code List<List<String>>} → table (rows × cells)
     */
    private static List<Object> detectBlocks(List<List<Seg>> lines) {
        List<Object> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            // Extend block as long as consecutive lines are Y-close
            int j = i + 1;
            while (j < lines.size()
                    && lines.get(j).get(0).y() - lines.get(j - 1).get(0).y() <= BLOCK_GAP) {
                j++;
            }
            List<List<Seg>> blockLines = lines.subList(i, j);

            // Split every line into columns
            List<List<String>> rows = blockLines.stream()
                    .map(PdfToDocx::splitCols)
                    .collect(Collectors.toList());

            // A block is a table when at least 2 rows each have ≥ 2 columns
            // and multiColRows make up the majority of the block
            long multiColCount = rows.stream().filter(r -> r.size() >= 2).count();
            boolean isTable = multiColCount >= 2 && multiColCount * 2 >= rows.size();

            if (isTable) {
                int maxCols = rows.stream().mapToInt(List::size).max().orElse(1);
                List<List<String>> tableRows = rows.stream().map(r -> {
                    List<String> norm = new ArrayList<>(r);
                    while (norm.size() < maxCols) norm.add("");
                    return Collections.unmodifiableList(norm);
                }).collect(Collectors.toList());
                blocks.add(tableRows);
            } else {
                String text = rows.stream()
                        .map(r -> String.join(" ", r))
                        .collect(Collectors.joining(" "))
                        .replaceAll("\\s{2,}", " ")
                        .trim();
                if (!text.isEmpty()) blocks.add(text);
            }
            i = j;
        }
        return blocks;
    }

    // ---- step 3: split one line's segments into columns by X gap ----------------

    private static List<String> splitCols(List<Seg> line) {
        List<Seg> sorted = line.stream()
                .sorted(Comparator.comparingDouble(Seg::x))
                .toList();
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        float lastEnd = sorted.get(0).x();
        for (Seg s : sorted) {
            if (s.x() - lastEnd > COL_GAP && !cur.isEmpty()) {
                cols.add(cur.toString().trim());
                cur = new StringBuilder();
            }
            cur.append(s.text());
            lastEnd = s.x() + s.w();
        }
        if (!cur.isEmpty()) cols.add(cur.toString().trim());
        return cols;
    }

    // ---- DOCX emitters ----------------------------------------------------------

    private static void emitParagraph(XWPFDocument xdoc, String text) {
        XWPFParagraph p = xdoc.createParagraph();
        p.setSpacingAfter(80);
        p.createRun().setText(text);
    }

    private static void emitTable(XWPFDocument xdoc, List<List<String>> rows) {
        int numCols = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = xdoc.createTable(rows.size(), numCols);
        addTableBorders(table);
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow xRow = table.getRow(r);
            List<String> cells = rows.get(r);
            for (int c = 0; c < cells.size() && c < numCols; c++) {
                XWPFTableCell xCell = xRow.getCell(c);
                XWPFParagraph cp = xCell.getParagraphs().isEmpty()
                        ? xCell.addParagraph() : xCell.getParagraphs().get(0);
                cp.createRun().setText(cells.get(c));
            }
        }
    }

    private static void addTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        if (tblPr.getTblBorders() != null) return;
        CTTblBorders b = tblPr.addNewTblBorders();
        for (CTBorder border : new CTBorder[]{
                b.addNewTop(), b.addNewBottom(), b.addNewLeft(),
                b.addNewRight(), b.addNewInsideH(), b.addNewInsideV()}) {
            border.setVal(STBorder.SINGLE);
            border.setColor("CCCCCC");
            border.setSz(BigInteger.valueOf(4));
        }
    }

    private static void setPageMargins(XWPFDocument doc) {
        CTBody   body = doc.getDocument().getBody();
        CTSectPr sect = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar mar = sect.isSetPgMar()  ? sect.getPgMar()  : sect.addNewPgMar();
        BigInteger m  = BigInteger.valueOf(1134); // 2 cm in twips
        mar.setTop(m); mar.setBottom(m); mar.setLeft(m); mar.setRight(m);
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> cast(List<?> list) {
        return (List<List<String>>) list;
    }
}
