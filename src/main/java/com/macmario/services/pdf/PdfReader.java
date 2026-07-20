package com.macmario.services.pdf;

import com.macmario.general.Version;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

class PdfReader extends Version {

    private final File inFile;
    private final Path outPath;

    PdfReader(String in, String out) {
        inFile  = new File(in);
        outPath = Path.of(out);
    }

    boolean extract() {
        if (!inFile.isFile()) {
            log(1, "extract: input not found — " + inFile);
            return false;
        }
        log(1, "extract: reading " + inFile);
        try (PDDocument doc = Loader.loadPDF(inFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            // Ensure parent directories exist
            Path parent = outPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(outPath, text, StandardCharsets.UTF_8);
            log(1, "extract: written " + outPath + " (" + text.length() + " chars)");
            return true;
        } catch (IOException e) {
            log(1, "extract: FAILED — " + e.getMessage());
            return false;
        }
    }
}
