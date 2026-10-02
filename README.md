# PDF Tool

Utilidad para trabajar con PDFs usando Apache PDFBox, con **interfaz gráfica (JavaFX)** y **línea de comandos (CLI)**.
Permite: unir, dividir, extraer o eliminar páginas, rotar, comprimir imágenes, añadir marca de agua, numerar páginas,
convertir imágenes en PDF y PDF en imágenes, extraer texto, ver y editar metadatos, y proteger o desproteger con
contraseña.


## Requisitos
- Java 21 o superior
- Maven 3.8+ (para compilar)


## Compilación y generación del JAR
En la raíz del proyecto:

```bash
mvn clean package
```

El JAR quedará en `target/pdf-tool-1.0-SNAPSHOT.jar` (y también `target/original-pdf-tool-1.0-SNAPSHOT.jar`, sin
dependencias). El JAR incluye JavaFX para el sistema operativo en el que se compila: un JAR generado en Windows abre
la interfaz en Windows. La parte de línea de comandos funciona en cualquier sistema. Para generar el JAR de otro
sistema añade `-Djavafx.platform=win`, `mac`, `mac-aarch64` o `linux`.


## Interfaz gráfica
Para abrirla, cualquiera de estas opciones:

- Doble clic en `pdf-tool-1.0-SNAPSHOT.jar` (si los `.jar` están asociados a Java 21).
- `java -jar target/pdf-tool-1.0-SNAPSHOT.jar` (sin argumentos) o `java -jar target/pdf-tool-1.0-SNAPSHOT.jar gui`.
- Durante el desarrollo, sin empaquetar: `mvn javafx:run`.

La ventana tiene una barra lateral con todas las herramientas, agrupadas en Organizar, Convertir, Editar, Seguridad
y Documento, y una pantalla de inicio con una tarjeta por herramienta:

| Pantalla | Qué hace |
|---|---|
| Unir PDF | Lista ordenable de PDFs (o carpetas enteras) que se combinan en uno. |
| Dividir PDF | Por rangos (`1-3, 4-10, 11-*`) o en partes de N páginas. |
| Extraer o eliminar páginas | Haz clic en las miniaturas o escribe rangos; para extraer en otro orden escribe `5, 1-3`. |
| Rotar páginas | 90° a la derecha, 180° o 90° a la izquierda, en todas o en algunas páginas. Las miniaturas muestran el giro. |
| Imágenes a PDF | JPG, PNG, GIF, BMP o TIFF → un PDF (A4, Carta o tamaño de la imagen). |
| PDF a imágenes | Páginas → PNG o JPG con la resolución elegida. |
| Extraer texto | Muestra el texto, lo copia al portapapeles o lo guarda como `.txt`. |
| Comprimir | Preajustes (Alta calidad, Equilibrado, Máxima compresión) o calidad y resolución a medida. |
| Marca de agua | Texto en diagonal con opacidad y color, con vista previa. |
| Numerar páginas | «Página 1 de 10» u otro formato, en 6 posiciones; puede saltarse la portada. |
| Proteger con contraseña | AES-256 con contraseña de apertura opcional, de propietario y permisos. |
| Quitar contraseña | Copia sin protección (con la contraseña de propietario). |
| Información y metadatos | Páginas, tamaño, versión, permisos, fechas… y edición de título, autor, asunto y palabras clave. |

Funcionamiento común a todas las pantallas:

- Los archivos se pueden **arrastrar y soltar** sobre cada campo o lista, o elegir con «Examinar…». Se recuerda la
  última carpeta usada.
- Al elegir un PDF se muestra su número de páginas y tamaño y, en las pantallas que lo necesitan, sus **miniaturas**.
- La salida se propone automáticamente junto al original (`informe_rotado.pdf`, `informe_comprimido.pdf`…) sin pisar
  archivos existentes. Si escribes una ruta que ya existe, se pide confirmación antes de reemplazarla.
- Las operaciones se ejecutan en segundo plano con barra de progreso. Al terminar aparece un aviso con botones para
  **abrir el archivo o la carpeta** resultante; si algo falla, un mensaje claro y un enlace «Detalles» con la
  información técnica.
- El PDF original nunca se modifica, salvo que elijas sobrescribirlo. Las salidas se escriben primero en un archivo
  temporal, así que un error nunca deja un PDF a medias.

### Editar las pantallas con Scene Builder
Las pantallas están en FXML, en `src/main/resources/com/alejandro/pdftool/gui/`:

- `main.fxml`: ventana principal (barra lateral y pantalla de inicio). Cada botón de la barra lleva en `userData` el
  nombre del FXML de su pantalla (`merge` → `merge.fxml`).
- Una pantalla por herramienta: `merge.fxml`, `split.fxml`, `pages.fxml`, `rotate.fxml`, `compress.fxml`,
  `watermark.fxml`, `page-numbers.fxml`, `images-to-pdf.fxml`, `pdf-to-images.fxml`, `text.fxml`, `info.fxml`,
  `encrypt.fxml` y `decrypt.fxml`.
- Componentes reutilizables que las pantallas incluyen con `fx:include`: `file-field.fxml` (campo de archivo),
  `file-list.fxml` (lista de archivos), `run-bar.fxml` (botón principal, progreso y avisos) y `thumbnails.fxml`
  (miniaturas).
- `app.css`: estilos. Cada FXML lo referencia para que Scene Builder muestre la vista previa con el aspecto real.

Todos los FXML usan solo controles estándar de JavaFX, así que Scene Builder los abre directamente. Están en una
misma carpeta para que los `fx:include` funcionen igual en Scene Builder y dentro del JAR. Los controladores están
en `com.alejandro.pdftool.gui` (`views/` para las pantallas y `components/` para los componentes). Si cambias un
`fx:id` o un método `onAction`, cámbialo también en el controlador; `GuiSmokeTest` carga todas las pantallas y avisa
si algo no casa.


## Línea de comandos

### Ayuda integrada
Con `-h`/`--help` verás el resumen de comandos:

```
PDF Tool - comandos:
  (sin argumentos)  abre la interfaz gráfica; también: gui
  merge -o <out.pdf> <in1.pdf> <carpeta> [...]  (Acepta archivos y/o carpetas)
  split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>   |   split <in.pdf> -every <N> -o <prefix>
  extract <in.pdf> -o <out.pdf> -pages "5,1-3"      (en el orden indicado)
  delete <in.pdf> -o <out.pdf> -pages "2,5-7"
  compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]
  rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]
  watermark <in.pdf> -o <out.pdf> -text "CONFIDENTIAL" [-opacity 0.2] [-color #C80000]
  pagenum <in.pdf> -o <out.pdf> [-format "Página {n} de {total}"] [-pos bottom-center]
          [-size 10] [-margin 10] [-start 1] [-pages "2-*"]
  images2pdf -o <out.pdf> <img1> <carpeta> [...] [-size a4|letter|image] [-margin 10]
  pdf2images <in.pdf> -o <carpeta> [-format png|jpg] [-dpi 150] [-pages "1-3"] [-name <base>]
  text <in.pdf> [-o <out.txt>]
  info <in.pdf>
  metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]
  encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>]
          [-perm print,copy,modify,annotate,fill,assemble]
  decrypt <in.pdf> -o <out.pdf> -pwd <contraseña de propietario>
```

Los errores se muestran con un mensaje claro y un código de salida distinto de 0: 2 si el comando está mal escrito y
1 si falla la operación. Para ver el detalle técnico añade `-Dpdftool.debug=true` antes de `-jar`.

En Windows PowerShell (rutas con `\`):

```powershell
java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar --help
```

### Comandos y ejemplos

#### merge — unir varios PDFs o carpetas
- Sintaxis: `merge -o <salida.pdf> <in1.pdf> <carpeta> [...]`
  - Acepta archivos PDF individuales y carpetas. De cada carpeta se añaden sus `.pdf` en orden natural (`doc2`
    antes que `doc10`); el propio archivo de salida nunca se incluye.
  - Si falta algún archivo, se avisa sin generar nada.
- Ejemplos:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\salida\unido.pdf .\docs\a.pdf .\docs\b.pdf
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\salida\final.pdf portada.pdf .\docs\contenido anexo.pdf
  ```

#### split — dividir por rangos o en partes iguales
- Sintaxis: `split <in.pdf> -ranges "1-3,7,10-*" -o <prefijo>` o `split <in.pdf> -every <N> -o <prefijo>`
  - `*` indica hasta el final. Crea `<prefijo>_part001.pdf`, `<prefijo>_part002.pdf`… y los lista al terminar.
- Ejemplos:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar split .\docs\origen.pdf -ranges "1-3,7,10-*" -o .\salida\corte
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar split .\docs\origen.pdf -every 1 -o .\salida\pagina
  ```

#### extract / delete — extraer o eliminar páginas
- `extract <in.pdf> -o <out.pdf> -pages "5,1-3"`: PDF nuevo solo con esas páginas, en el orden escrito.
- `delete <in.pdf> -o <out.pdf> -pages "2,5-7"`: PDF nuevo sin esas páginas.
- Los marcadores (índice) y los formularios rellenables del original no se conservan.

#### compress — recomprimir imágenes del PDF
- Sintaxis: `compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]`
  - `-q`: calidad JPEG (0.1 a 1.0). Por defecto 0.7.
  - `--max-dpi`: resolución máxima de las imágenes según el tamaño al que se muestran en la página.
  - `--remove-metadata`: elimina los metadatos del documento.
- Al terminar muestra el tamaño antes y después y cuántas imágenes se han recomprimido.
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar compress .\docs\pesado.pdf -o .\salida\ligero.pdf -q 0.65 --max-dpi 150 --remove-metadata
  ```

#### rotate — rotar páginas
- Sintaxis: `rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]`
  - El giro es en sentido horario y debe ser múltiplo de 90 (también se admite `-90`).
  - `-pages` es opcional; si no se indica, se aplica a todas las páginas.
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar rotate .\docs\origen.pdf -o .\salida\rotado_sel.pdf -deg 270 -pages "1-3,5"
  ```

#### watermark — marca de agua de texto
- Sintaxis: `watermark <in.pdf> -o <out.pdf> -text "TEXTO" [-opacity 0.2] [-color #C80000]`
  - El texto se dibuja en diagonal (45°) y centrado en la zona visible de cada página, también en páginas giradas.
  - Fuente Helvetica Bold de hasta 64 pt; si el texto es largo, se reduce para que quepa.
  - Admite los caracteres del español (tildes, ñ, ¿¡, €). Si el texto tiene caracteres que la fuente no puede
    mostrar (emojis, alfabetos no latinos…), se indican cuáles.
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar watermark .\docs\origen.pdf -o .\salida\wm.pdf -text "CONFIDENCIAL" -opacity 0.25
  ```

#### pagenum — numerar páginas
- Sintaxis: `pagenum <in.pdf> -o <out.pdf> [-format "Página {n} de {total}"] [-pos bottom-center] [-size 10] [-margin 10] [-start 1] [-pages "2-*"]`
  - `{n}` es el número de página y `{total}` el último número.
  - `-pos`: `top-left`, `top-center`, `top-right`, `bottom-left`, `bottom-center` o `bottom-right`.
  - `-margin` en milímetros. `-start` es el número de la primera página numerada.
- Ejemplo (sin numerar la portada y empezando en 1):
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar pagenum .\docs\informe.pdf -o .\salida\numerado.pdf -pages "2-*" -start 1
  ```

#### images2pdf / pdf2images — convertir entre imágenes y PDF
- `images2pdf -o <out.pdf> <img1> <carpeta> [...] [-size a4|letter|image] [-margin 10]`
  - Una imagen por página, ajustada y centrada. Los JPG se incluyen sin recomprimir, y las fotos del móvil se giran
    según su orientación EXIF. Margen en milímetros.
- `pdf2images <in.pdf> -o <carpeta> [-format png|jpg] [-dpi 150] [-pages "1-3"] [-name <base>]`
  - Crea `<base>_001.png`, `<base>_002.png`… (el número es el de la página).
- Ejemplos:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar images2pdf -o .\salida\fotos.pdf .\fotos
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar pdf2images .\docs\in.pdf -o .\salida\paginas -format jpg -dpi 200
  ```

#### text — extraer texto
- Sintaxis: `text <in.pdf> [-o <out.txt>]`
  - Sin `-o`, lo imprime por consola. Con `-o` lo guarda en UTF-8.

#### info / metadata — información y metadatos
- `info <in.pdf>`: páginas, título, autor, asunto, palabras clave, productor y creador.
- `metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]`: cambia los campos indicados y
  conserva los demás; un valor vacío (`-title ""`) borra el campo. Se actualizan tanto el diccionario de información
  como los metadatos XMP, que es lo que muestran Acrobat y otros visores.

#### encrypt / decrypt — cifrar y descifrar
- `encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]`
  - Cifrado AES-256. `-ownerPwd` es obligatoria y debe ser distinta de `-userPwd`.
  - `-userPwd` es opcional: si se indica, hará falta para abrir el documento.
  - `-perm` concede permisos: `print`, `copy`, `modify`, `annotate`, `fill` (formularios) y `assemble` (insertar,
    girar o eliminar páginas). Lo que no se indica queda bloqueado.
- `decrypt <in.pdf> -o <out.pdf> -pwd <contraseña de propietario>`
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar encrypt .\docs\in.pdf -o .\salida\enc.pdf -ownerPwd MiClaveAdmin -userPwd Lectura -perm print,copy
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar decrypt .\salida\enc.pdf -o .\salida\dec.pdf -pwd MiClaveAdmin
  ```


## Cambios de comportamiento respecto a la versión anterior
- **Sin argumentos se abre la interfaz gráfica** (antes se mostraba la ayuda; `--help` sigue igual y, si no hay
  entorno gráfico, se muestra la ayuda).
- **PDFs con restricciones de seguridad** (los que tienen contraseña de propietario aunque se abran sin contraseña):
  las operaciones que los modifican se niegan con un mensaje claro, en lugar de fallar (`rotate`, `watermark`,
  `compress`) o de quitar las restricciones sin avisar (`merge`, `split`). Extraer texto y exportar imágenes respetan
  el permiso de copia. Para modificarlos, primero quita la protección con la contraseña de propietario.
- **decrypt** exige la contraseña de propietario. La de apertura sola no basta, igual que en Acrobat.
- **encrypt** usa AES-256 (antes RC4 de 128 bits) y concede solo los permisos indicados; antes anotar, rellenar
  formularios y ensamblar quedaban permitidos aunque no se pidieran.
- **compress `--max-dpi`** se calcula con el tamaño real al que se muestra cada imagen (antes reducía todas las
  imágenes en la proporción `dpi/300`). Una imagen solo se sustituye si el resultado es al menos un 10 % más pequeño.
- **merge** ordena el contenido de las carpetas en orden natural y falla si algún archivo no existe (antes se omitía
  y el recuento era incorrecto).
- **split** conserva el tamaño de página, el giro y las fuentes que las páginas heredan del documento (antes se
  podían perder) y lista los archivos creados.


## Notas de diseño y comportamiento
- Escritura segura: cada salida se escribe en un temporal de la misma carpeta y solo al final sustituye al destino.
  Si algo falla, el destino queda intacto. Por eso la salida puede ser el mismo archivo de entrada.
- Compresión: cada imagen distinta se recomprime una sola vez, aunque aparezca en muchas páginas, y se procesan
  también las imágenes dentro de formularios y anotaciones. No se tocan las imágenes con transparencia ni las de
  1 bit (escaneos en blanco y negro), y las imágenes en gris siguen en gris. Las imágenes CMYK se convierten a RGB.
  Para documentos escaneados suele funcionar bien en el rango 0.5–0.8.
- Marca de agua y numeración: se dibujan en las coordenadas «visuales» de cada página, así que salen bien colocadas
  aunque la página esté girada (`/Rotate`) o su zona visible no empiece en el origen.
- Metadatos: `--remove-metadata` limpia la información del documento y los metadatos XMP.


## Solución de problemas
- La interfaz no se abre con doble clic: comprueba que tienes Java 21 (`java -version`) y que los `.jar` se abren con
  Java, o ejecuta `java -jar pdf-tool-1.0-SNAPSHOT.jar` desde una consola para ver el mensaje. Si el JAR se generó en
  otro sistema operativo, vuelve a generarlo en el tuyo con `mvn clean package`.
- «Comando no reconocido»: revisa que estás usando alguno de los listados en la ayuda.
- «Debe indicar -o <out.pdf>» u otros mensajes de uso: el comando requiere esas opciones. Mira los ejemplos.
- Problemas de rutas en Windows: usa `\` o comillas si hay espacios, por ejemplo `"C:\\Mi Carpeta\\in.pdf"`.
- «No se puede escribir en …»: el archivo de salida está abierto en otro programa (por ejemplo, el visor de PDF) o
  la carpeta es de solo lectura.
- PDFs protegidos: para operar sobre un PDF cifrado usa antes `decrypt` (o «Quitar contraseña») con la contraseña
  de propietario.
- Resultados de compresión pobres: prueba el preajuste «Máxima compresión» o baja `-q`/`--max-dpi`. Los PDF sin
  imágenes, o con imágenes ya muy comprimidas, apenas se reducen.
- «El PDF no contiene texto seleccionable»: es un documento escaneado. Sus páginas son imágenes y haría falta un
  programa de OCR.


## Desarrollo
- Java 21, Apache PDFBox 3, JavaFX 21 (FXML) y JUnit 5.
- Estructura en `src/main/java/com/alejandro/pdftool/`:
  - `App` (CLI), `PdfOps` (fachada con todas las operaciones), `CliUtil` (argumentos y rangos de páginas) y clases
    auxiliares del núcleo (`SafeOutput`, `Pdfs`, `ImageRecompressor`, `PageStamper`, `MetadataSupport`,
    `ImageConversion`…).
  - `gui/`: `GuiLauncher`, `PdfToolApp`, `MainController`, `components/` y `views/` (un controlador por pantalla).
- Tests: `mvn test`. `GuiSmokeTest` carga todas las pantallas; en un Linux sin entorno gráfico se omite, o se puede
  ejecutar con `xvfb-run mvn test`.


## Licencia
Este proyecto se distribuye tal cual, sin garantías. Ajusta la licencia según tus necesidades.
