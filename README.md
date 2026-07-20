# MHService PDF — PDF Utilities CLI

A standalone CLI tool that handles two PDF-related operations in one JAR:

- **PDF → text** — extract all text from a PDF file
- **Markdown → PDF** — convert a Markdown file to a styled PDF (Obsidian-compatible)

Mode is auto-detected from the `-in` file extension: `.pdf` extracts text, `.md` produces a PDF.

---

## Build

Requires Java 17 and Maven.

```shell
cd tools/pdf
mvn package -DskipTests
# produces: target/MHService-pdf-1.0.jar
```

---

## CLI usage

```
java -jar MHService-pdf-1.0.jar -in <file> -out <file> [-d]
```

| Flag | Description |
|------|-------------|
| `-in <file>` | Input file. Extension determines the mode (`.pdf` or `.md`) |
| `-out <file>` | Output file path (created including any missing parent directories) |
| `-d` | Enable debug output |

---

## PDF → text extraction

Reads a PDF and writes its full text content to a plain-text file.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  /path/to/document.pdf \
    -out /path/to/output.txt
```

Backed by **Apache PDFBox 3.x** (`Loader.loadPDF` + `PDFTextStripper`).

---

## Markdown → PDF conversion

Converts a Markdown file to a PDF with professional typography.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  /path/to/note.md \
    -out /path/to/output.pdf
```

### Supported Markdown features

| Feature | Syntax |
|---------|--------|
| Headings | `# H1` … `###### H6` |
| Bold / italic | `**bold**`, `*italic*` |
| Strikethrough | `~~text~~` |
| Code inline | `` `code` `` |
| Code block | ` ``` … ``` ` |
| Tables | GFM pipe tables |
| Task lists | `- [x]` / `- [ ]` |
| Blockquotes | `> …` |
| Links | `[text](url)` |
| Horizontal rule | `---` |

### YAML front matter

Obsidian-style front matter is stripped before conversion.
The `title:` field, if present, becomes the PDF document title.

```yaml
---
title: My Note
tags: [foo, bar]
---

# Actual content starts here
```

### PDF styling

- **Page size**: A4 with 2 cm margins on all sides
- **Body font**: Helvetica / Arial, 11 pt, line-height 1.5
- **Headings**: scaled 22 pt → 12 pt with `h1`/`h2` underlines
- **Code blocks**: monospace (Courier), light grey background, left accent border
- **Tables**: full border, header row shaded
- **Links**: blue (`#5B6EC1`)
- **Strikethrough**: line-through, muted grey

Backed by **flexmark-java 0.64.8** (Markdown → XHTML) and
**Flying Saucer 9.1.22 + OpenPDF** (XHTML → PDF).

---

## Examples

```shell
JAR=target/MHService-pdf-1.0.jar

# Extract text from a PDF
java -jar $JAR -in report.pdf -out report.txt

# Convert an Obsidian note to PDF
java -jar $JAR -in notes/meeting.md -out export/meeting.pdf

# Convert with debug output
java -jar $JAR -d -in notes/meeting.md -out export/meeting.pdf
```

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Operation succeeded (prints `Successfull`) |
| `-1` | Missing or invalid arguments (prints usage) |
| `0` | Operation failed — input not found or processing error (prints `Failed`) |
