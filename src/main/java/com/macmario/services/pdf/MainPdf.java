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
        if (in.toLowerCase().endsWith(".md")) {
            MdToPdf converter = new MdToPdf(in, out);
            ok = converter.convert();
        } else {
            PdfReader reader = new PdfReader(in, out);
            ok = reader.extract();
        }
        System.out.println(ok ? "Successfull" : "Failed");
        System.exit(0);
    }

    private static void usage() {
        System.out.println("java -jar " + jarfile + " -in <file> -out <file>");
        System.out.println("  -in  <pdf-file>   PDF → text: extract text from PDF");
        System.out.println("  -in  <md-file>    MD  → PDF : convert Markdown to PDF");
        System.out.println("  -out <out-file>   output path");
        System.out.println("  -d                enable debug output");
        System.out.println("Mode is detected from the -in file extension (.pdf → text, .md → pdf).");
    }
}
