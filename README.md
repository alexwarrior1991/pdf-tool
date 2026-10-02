# PDF Tool

A PDF utility built on Apache PDFBox, with a **graphical interface (JavaFX)** and a **command line (CLI)**.
It can merge, split, extract or delete pages, rotate, compress images, add a watermark, number pages, convert
images to PDF and PDF to images, extract text, view and edit metadata, and add or remove password protection.


## Requirements
- Java 21 or later
- Maven 3.8+ (to build)


## Building the JAR
From the project root:

```bash
mvn clean package
```

The JAR ends up in `target/pdf-tool-1.0-SNAPSHOT.jar` (plus `target/original-pdf-tool-1.0-SNAPSHOT.jar`, without
dependencies). The JAR bundles JavaFX for the operating system it is built on: a JAR built on Windows opens the
interface on Windows. The command line works on any system. To build the JAR for another system add
`-Djavafx.platform=win`, `mac`, `mac-aarch64` or `linux`.


## Graphical interface
To open it, use any of these:

- Double-click `pdf-tool-1.0-SNAPSHOT.jar` (if `.jar` files are associated with Java 21).
- `java -jar target/pdf-tool-1.0-SNAPSHOT.jar` (no arguments) or `java -jar target/pdf-tool-1.0-SNAPSHOT.jar gui`.
- During development, without packaging: `mvn javafx:run`.

The window has a sidebar with every tool, grouped into Organize, Convert, Edit, Security and Document, and a home
screen with one card per tool:

| Screen | What it does |
|---|---|
| Merge PDFs | Sortable list of PDFs (or whole folders) combined into one. |
| Split PDF | By ranges (`1-3, 4-10, 11-*`) or into parts of N pages. |
| Extract or delete pages | Click the thumbnails or type ranges; to extract in another order type `5, 1-3`. |
| Rotate pages | 90° right, 180° or 90° left, on all pages or some. The thumbnails preview the rotation. |
| Images to PDF | JPG, PNG, GIF, BMP or TIFF → one PDF (A4, Letter or image size). |
| PDF to images | Pages → PNG or JPG at the chosen resolution. |
| Extract text | Shows the text, copies it to the clipboard or saves it as `.txt`. |
| Compress | Presets (High quality, Balanced, Smallest size) or custom quality and resolution. |
| Watermark | Diagonal text with opacity and color, with a preview. |
| Page numbers | "Page 1 of 10" or another format, in 6 positions; can skip the cover page. |
| Protect with password | AES-256 with an optional open password, an owner password and permissions. |
| Remove password | Unprotected copy (needs the owner password). |
| Info & metadata | Pages, size, version, permissions, dates… and editing of title, author, subject and keywords. |

Common to every screen:

- Files can be **dragged and dropped** onto each field or list, or chosen with "Browse…". The last folder used is
  remembered.
- When you pick a PDF its page count and size are shown and, on the screens that need them, its **thumbnails**.
- The output is suggested automatically next to the original (`report_rotated.pdf`, `report_compressed.pdf`…)
  without overwriting existing files. If you type a path that already exists, you are asked before it is replaced.
- Operations run in the background with a progress bar. When they finish, a banner offers buttons to **open the
  resulting file or folder**; if something fails, a clear message and a "Details" link with the technical
  information.
- The original PDF is never modified unless you choose to overwrite it. Outputs are written to a temporary file
  first, so an error never leaves a half-written PDF.

### Editing the screens with Scene Builder
The screens are FXML files in `src/main/resources/com/alejandro/pdftool/gui/`:

- `main.fxml`: main window (sidebar and home screen). Each sidebar button stores in `userData` the name of its
  screen's FXML (`merge` → `merge.fxml`).
- One screen per tool: `merge.fxml`, `split.fxml`, `pages.fxml`, `rotate.fxml`, `compress.fxml`,
  `watermark.fxml`, `page-numbers.fxml`, `images-to-pdf.fxml`, `pdf-to-images.fxml`, `text.fxml`, `info.fxml`,
  `encrypt.fxml` and `decrypt.fxml`.
- Reusable components that the screens include with `fx:include`: `file-field.fxml` (file field),
  `file-list.fxml` (file list), `run-bar.fxml` (main button, progress and banners) and `thumbnails.fxml`
  (thumbnails).
- `app.css`: styles. Every FXML references it so Scene Builder previews it with the real look.

All FXML files use only standard JavaFX controls, so Scene Builder opens them directly. They live in a single
folder so `fx:include` works the same in Scene Builder and inside the JAR. The controllers are in
`com.alejandro.pdftool.gui` (`views/` for the screens and `components/` for the components). If you change an
`fx:id` or an `onAction` method, change it in the controller too; `GuiSmokeTest` loads every screen and reports any
mismatch.


## Command line

### Built-in help
`-h`/`--help` prints the command summary:

```
PDF Tool - commands:
  (no arguments)  opens the graphical interface; also: gui
  merge -o <out.pdf> <in1.pdf> <folder> [...]  (accepts files and/or folders)
  split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>   |   split <in.pdf> -every <N> -o <prefix>
  extract <in.pdf> -o <out.pdf> -pages "5,1-3"      (in the given order)
  delete <in.pdf> -o <out.pdf> -pages "2,5-7"
  compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]
  rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]
  watermark <in.pdf> -o <out.pdf> -text "CONFIDENTIAL" [-opacity 0.2] [-color #C80000]
  pagenum <in.pdf> -o <out.pdf> [-format "Page {n} of {total}"] [-pos bottom-center]
          [-size 10] [-margin 10] [-start 1] [-pages "2-*"]
  images2pdf -o <out.pdf> <img1> <folder> [...] [-size a4|letter|image] [-margin 10]
  pdf2images <in.pdf> -o <folder> [-format png|jpg] [-dpi 150] [-pages "1-3"] [-name <base>]
  text <in.pdf> [-o <out.txt>]
  info <in.pdf>
  metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]
  encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>]
          [-perm print,copy,modify,annotate,fill,assemble]
  decrypt <in.pdf> -o <out.pdf> -pwd <owner password>
```

Errors are reported with a clear message and a non-zero exit code: 2 if the command is mistyped and 1 if the
operation fails. For technical details add `-Dpdftool.debug=true` before `-jar`.

In Windows PowerShell (paths with `\`):

```powershell
java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar --help
```

### Commands and examples

#### merge — merge several PDFs or folders
- Syntax: `merge -o <output.pdf> <in1.pdf> <folder> [...]`
  - Accepts individual PDF files and folders. From each folder its `.pdf` files are added in natural order (`doc2`
    before `doc10`); the output file itself is never included.
  - If a file is missing, you are told and nothing is created.
- Examples:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\out\merged.pdf .\docs\a.pdf .\docs\b.pdf
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\out\final.pdf cover.pdf .\docs\content appendix.pdf
  ```

#### split — split by ranges or into equal parts
- Syntax: `split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>` or `split <in.pdf> -every <N> -o <prefix>`
  - `*` means up to the end. Creates `<prefix>_part001.pdf`, `<prefix>_part002.pdf`… and lists them when done.
- Examples:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar split .\docs\source.pdf -ranges "1-3,7,10-*" -o .\out\part
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar split .\docs\source.pdf -every 1 -o .\out\page
  ```

#### extract / delete — extract or delete pages
- `extract <in.pdf> -o <out.pdf> -pages "5,1-3"`: new PDF with only those pages, in the order written.
- `delete <in.pdf> -o <out.pdf> -pages "2,5-7"`: new PDF without those pages.
- Bookmarks (outline) and fillable forms of the original are not kept.

#### compress — recompress the images in the PDF
- Syntax: `compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]`
  - `-q`: JPEG quality (0.1 to 1.0). Default 0.7.
  - `--max-dpi`: maximum image resolution, based on the size at which each image is shown on the page.
  - `--remove-metadata`: removes the document metadata.
- When done it shows the size before and after and how many images were recompressed.
- Example:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar compress .\docs\heavy.pdf -o .\out\light.pdf -q 0.65 --max-dpi 150 --remove-metadata
  ```

#### rotate — rotate pages
- Syntax: `rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]`
  - Rotation is clockwise and must be a multiple of 90 (`-90` is accepted too).
  - `-pages` is optional; without it every page is rotated.
- Example:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar rotate .\docs\source.pdf -o .\out\rotated_sel.pdf -deg 270 -pages "1-3,5"
  ```

#### watermark — text watermark
- Syntax: `watermark <in.pdf> -o <out.pdf> -text "TEXT" [-opacity 0.2] [-color #C80000]`
  - The text is drawn diagonally (45°) and centered on the visible area of each page, rotated pages included.
  - Helvetica Bold font up to 64 pt; long text is shrunk to fit.
  - Supports Western European characters (accents, ñ, ¿¡, €). If the text contains characters the font cannot show
    (emojis, non-Latin scripts…), they are listed.
- Example:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar watermark .\docs\source.pdf -o .\out\wm.pdf -text "CONFIDENTIAL" -opacity 0.25
  ```

#### pagenum — number pages
- Syntax: `pagenum <in.pdf> -o <out.pdf> [-format "Page {n} of {total}"] [-pos bottom-center] [-size 10] [-margin 10] [-start 1] [-pages "2-*"]`
  - `{n}` is the page number and `{total}` the last number.
  - `-pos`: `top-left`, `top-center`, `top-right`, `bottom-left`, `bottom-center` or `bottom-right`.
  - `-margin` in millimeters. `-start` is the number of the first numbered page.
- Example (skipping the cover and starting at 1):
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar pagenum .\docs\report.pdf -o .\out\numbered.pdf -pages "2-*" -start 1
  ```

#### images2pdf / pdf2images — convert between images and PDF
- `images2pdf -o <out.pdf> <img1> <folder> [...] [-size a4|letter|image] [-margin 10]`
  - One image per page, fitted and centered; a multi-page TIFF (scanner, fax) contributes all its pages. JPGs are
    embedded without recompression, and phone photos are rotated according to their EXIF orientation. Margin in
    millimeters.
- `pdf2images <in.pdf> -o <folder> [-format png|jpg] [-dpi 150] [-pages "1-3"] [-name <base>]`
  - Creates `<base>_001.png`, `<base>_002.png`… (the number is the page number).
- Examples:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar images2pdf -o .\out\photos.pdf .\photos
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar pdf2images .\docs\in.pdf -o .\out\pages -format jpg -dpi 200
  ```

#### text — extract text
- Syntax: `text <in.pdf> [-o <out.txt>]`
  - Without `-o`, prints it to the console. With `-o`, saves it as UTF-8.

#### info / metadata — information and metadata
- `info <in.pdf>`: pages, title, author, subject, keywords, producer and creator.
- `metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]`: changes the given fields and
  keeps the rest; an empty value (`-title ""`) clears the field. Both the information dictionary and the XMP
  metadata (what Acrobat and other viewers show) are updated.

#### encrypt / decrypt — encrypt and decrypt
- `encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]`
  - AES-256 encryption. `-ownerPwd` is required and must differ from `-userPwd`.
  - `-userPwd` is optional: if given, it is needed to open the document.
  - `-perm` grants permissions: `print`, `copy`, `modify`, `annotate`, `fill` (forms) and `assemble` (insert,
    rotate or delete pages). Anything not listed is blocked.
- `decrypt <in.pdf> -o <out.pdf> -pwd <owner password>`
- Example:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar encrypt .\docs\in.pdf -o .\out\enc.pdf -ownerPwd MyAdminKey -userPwd ReadOnly -perm print,copy
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar decrypt .\out\enc.pdf -o .\out\dec.pdf -pwd MyAdminKey
  ```


## Behavior changes from the previous version
- **With no arguments the graphical interface opens** (previously the help was shown; `--help` is unchanged and,
  without a graphical environment, the help is shown).
- **PDFs with security restrictions** (those with an owner password even if they open without one): operations
  that modify them are refused with a clear message, instead of failing (`rotate`, `watermark`, `compress`) or
  silently removing the restrictions (`merge`, `split`). Extracting text and exporting images respect the copy
  permission. To modify them, first remove the protection with the owner password.
- **decrypt** requires the owner password. The open password alone is not enough, just like in Acrobat.
- **encrypt** uses AES-256 (previously 128-bit RC4) and grants only the listed permissions; previously annotating,
  filling forms and assembling were allowed even if not requested.
- **compress `--max-dpi`** is computed from the real size at which each image is shown (previously every image was
  scaled by `dpi/300`). An image is only replaced if the result is at least 10% smaller.
- **merge** sorts folder contents in natural order and fails if a file does not exist (previously it was skipped
  and the count was wrong).
- **split** keeps the page size, rotation and fonts that pages inherit from the document (previously they could be
  lost) and lists the files created.


## Design notes
- Safe writes: each output is written to a temporary file in the same folder and only replaces the target at the
  end. If something fails, the target is left untouched. That is why the output may be the input file itself.
- Compression: each distinct image is recompressed only once, even if it appears on many pages, and images inside
  forms and annotations are processed too. Images with transparency and 1-bit images (black-and-white scans) are
  left alone, and grayscale images stay grayscale. CMYK images are converted to RGB. For scanned documents the
  0.5–0.8 range usually works well.
- Watermark and page numbers: drawn in the "visual" coordinates of each page, so they are placed correctly even if
  the page is rotated (`/Rotate`) or its visible area does not start at the origin.
- Metadata: `--remove-metadata` clears the document information and the XMP metadata.


## Troubleshooting
- The interface does not open on double-click: check that you have Java 21 (`java -version`) and that `.jar` files
  open with Java, or run `java -jar pdf-tool-1.0-SNAPSHOT.jar` from a console to see the message. If the JAR was
  built on another operating system, rebuild it on yours with `mvn clean package`.
- "Unknown command": make sure you are using one of the commands listed in the help.
- "Missing -o <out.pdf>" or other usage messages: the command needs those options. See the examples.
- Path problems on Windows: use `\`, and quotes if there are spaces, e.g. `"C:\\My Folder\\in.pdf"`.
- "Cannot write to …": the output file is open in another program (e.g. the PDF viewer) or the folder is
  read-only.
- Protected PDFs: to work on an encrypted PDF, first use `decrypt` (or "Remove password") with the owner password.
- Poor compression results: try the "Smallest size" preset or lower `-q`/`--max-dpi`. PDFs without images, or with
  already highly compressed images, barely shrink.
- "This PDF has no selectable text": it is a scanned document. Its pages are images and an OCR program would be
  needed.


## Development
- Java 21, Apache PDFBox 3, JavaFX 21 (FXML) and JUnit 5.
- Layout of `src/main/java/com/alejandro/pdftool/`:
  - `App` (CLI), `PdfOps` (facade with every operation), `CliUtil` (arguments and page ranges) and core helper
    classes (`SafeOutput`, `Pdfs`, `ImageRecompressor`, `PageStamper`, `MetadataSupport`, `ImageConversion`…).
  - `gui/`: `GuiLauncher`, `PdfToolApp`, `MainController`, `components/` and `views/` (one controller per screen).
- Tests: `mvn test`. `GuiSmokeTest` loads every screen; on Linux without a graphical environment it is skipped, or
  run it with `xvfb-run mvn test`.


## License
This project is provided as is, without warranty. Adjust the license to your needs.
