# MHService PDF — PDF & Document Utilities CLI

A standalone CLI tool that handles four document operations in one JAR:

- **PDF → text** — extract all text from a PDF file
- **PDF → DOCX** — convert a PDF to an editable Word document
- **Markdown → PDF** — convert a Markdown file to a styled PDF (Obsidian-compatible)
- **Markdown → DOCX** — convert a Markdown file to a Word document

Mode is auto-detected from the `-in` and `-out` file extensions.

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
| `-in <file>` | Input file. Extension determines the source (`.pdf` or `.md`) |
| `-out <file>` | Output file. Extension determines the target format for `.md` input |
| `-d` | Enable debug output |

### Mode detection

| `-in` extension | `-out` extension | Operation |
|-----------------|------------------|-----------|
| `.pdf` | `.docx` | PDF → Word document |
| `.pdf` | anything else | PDF → text extraction |
| `.md` | `.docx` | Markdown → Word document |
| `.md` | anything else | Markdown → PDF |

---

## PDF → text extraction

Reads a PDF and writes its full text content to a plain-text file.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  report.pdf \
    -out report.txt
```

Backed by **Apache PDFBox 3.x** (`Loader.loadPDF` + `PDFTextStripper`).

---

## PDF → DOCX

Converts a PDF to an editable Word document. Text is extracted page by page, reflowed into
paragraphs (blank-line boundaries), and written to a `.docx` file.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  report.pdf \
    -out report.docx
```

Backed by **Apache PDFBox 3.x** (position-aware text extraction) and
**Apache POI 5.2.5 XWPF** (Word document writer).

### Table detection

The converter analyses the X/Y coordinates of every text segment on each page and
reconstructs tabular data as native Word tables:

- Lines that share the same Y position are grouped into a single row.
- Within each row, gaps wider than ~18 pt between adjacent text segments are treated as
  column separators.
- A block of consecutive lines is emitted as an `XWPFTable` when at least two of its lines
  each contain two or more columns; otherwise the lines are joined into a paragraph.

> **Limitations**: The algorithm works on visual text layout — fonts, colors, and cell
> shading are not transferred. Merged cells and rotated text are not supported. Headers and
> footers appear as regular paragraphs.

---

## Markdown → PDF

Converts a Markdown file to a PDF with professional typography.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  notes/meeting.md \
    -out export/meeting.pdf
```

Backed by **flexmark-java 0.64.8** (Markdown → XHTML) and
**Flying Saucer 9.1.22 + OpenPDF** (XHTML → PDF).

### PDF styling

- **Page size**: A4 with 2 cm margins on all sides
- **Body font**: Helvetica / Arial, 11 pt, line-height 1.5
- **Headings**: scaled 22 pt → 11 pt with `h1`/`h2` underlines
- **Code blocks**: monospace (Courier), light grey background, left accent border
- **Tables**: full border, header row in bold
- **Strikethrough**: line-through, muted grey

---

## Markdown → DOCX

Converts a Markdown file to a Word (`.docx`) document.

```shell
java -jar MHService-pdf-1.0.jar \
    -in  notes/meeting.md \
    -out export/meeting.docx
```

Backed by **flexmark-java 0.64.8** (Markdown parser) and
**Apache POI 5.2.5 XWPF** (Word document writer).

### DOCX styling

- **Page margins**: 2 cm on all sides
- **Body font**: 11 pt; headings scale from 22 pt (H1) to 11 pt (H6), all bold
- **H1 / H2**: bottom border rule for visual separation
- **Code blocks**: Courier New 9 pt, light grey paragraph shading
- **Tables**: visible borders on all cells; header row is bold
- **Task lists**: ☑ / ☐ prefix characters
- **Blockquotes**: indented, italic

---

## Supported Markdown features

| Feature | Syntax |
|---------|--------|
| Headings | `# H1` … `###### H6` |
| Bold / italic | `**bold**`, `*italic*` |
| Strikethrough | `~~text~~` |
| Inline code | `` `code` `` |
| Fenced code block | ` ``` … ``` ` |
| Indented code block | 4-space indent |
| GFM tables | pipe `\|` syntax |
| Task lists | `- [x]` / `- [ ]` |
| Blockquotes | `> …` |
| Nested lists | indented `- ` or `1. ` |
| Links | `[text](url)` |
| Horizontal rule | `---` |

---

## YAML front matter

Obsidian-style front matter is stripped before conversion.
The `title:` field, if present, is used as the PDF document title.

```yaml
---
title: My Note
tags: [foo, bar]
---

# Actual content starts here
```

---

## Examples

```shell
JAR=target/MHService-pdf-1.0.jar

# Extract text from a PDF
java -jar $JAR -in report.pdf -out report.txt

# Convert a PDF to an editable Word document
java -jar $JAR -in report.pdf -out report.docx

# Convert a Markdown note to PDF
java -jar $JAR -in notes/meeting.md -out export/meeting.pdf

# Convert a Markdown note to Word
java -jar $JAR -in notes/meeting.md -out export/meeting.docx

# Any mode with debug output
java -jar $JAR -d -in report.pdf -out report.docx
```

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Operation succeeded — prints `Successfull` |
| `-1` | Missing or invalid arguments — prints usage |
| `0` | Operation failed — input not found or processing error — prints `Failed` |
