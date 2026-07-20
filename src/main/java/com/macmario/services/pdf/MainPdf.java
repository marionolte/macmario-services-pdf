package com.macmario.services.pdf;

import com.macmario.general.Version;

public class MainPdf extends Version {

    public static void main(String[] args) {
        int use = 0;
        String in = null, out = null;

        if (args.length == 0) {
            use++;
        } else {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-d")) { debug++; }
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-d")) {
                    // handled above
                } else if (args[i].equals("-in") && args.length > (i + 1)) {
                    in = args[++i];
                } else if (args[i].equals("-out") && args.length > (i + 1)) {
                    out = args[++i];
                } else if (!args[i].startsWith("-")) {
                    // positional value consumed by a previous flag
                } else {
                    use++;
                }
            }
        }

        if (use > 0 || in == null || out == null) {
            usage();
            System.exit(-1);
        }

        boolean ok;
        String inLower  = in.toLowerCase();
        String outLower = out.toLowerCase();
        if (inLower.endsWith(".pdf") && outLower.endsWith(".docx")) {
            ok = new PdfToDocx(in, out).convert();
        } else if (inLower.endsWith(".pdf")) {
            ok = new PdfReader(in, out).extract();
        } else if (inLower.endsWith(".md") && outLower.endsWith(".docx")) {
            ok = new MdToDocx(in, out).convert();
        } else if (inLower.endsWith(".md")) {
            ok = new MdToPdf(in, out).convert();
        } else {
            usage();
            System.exit(-1);
            return;
        }
        System.out.println(ok ? "Successfull" : "Failed");
        System.exit(0);
    }

    private static void usage() {
        System.out.println("java -jar " + jarfile + " -in <file> -out <file>");
        System.out.println("  -in  <pdf-file>  -out <txt>   PDF → text  : extract text from PDF");
        System.out.println("  -in  <pdf-file>  -out <docx>  PDF → DOCX : convert PDF to Word document");
        System.out.println("  -in  <md-file>   -out <pdf>   MD  → PDF  : convert Markdown to PDF");
        System.out.println("  -in  <md-file>   -out <docx>  MD  → DOCX : convert Markdown to Word document");
        System.out.println("  -out <out-file>   output path");
        System.out.println("  -d                enable debug output");
        System.out.println("Mode is detected from -in extension; output format from -out extension.");
    }
}
