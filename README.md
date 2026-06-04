# PDF Tool

Pequeña utilidad CLI (línea de comandos) para trabajar con PDFs usando Apache PDFBox.
Permite: unir, dividir por rangos, comprimir imágenes del PDF, rotar páginas, añadir marcas de agua de texto centradas, extraer texto, ver información y cifrar/descifrar.


## Requisitos
- Java 17 o superior (recomendado)
- Maven 3.8+ (para compilar)


## Compilación y generación del JAR
En la raíz del proyecto:

```bash
mvn clean package
```

El JAR quedará en `target/pdf-tool-1.0-SNAPSHOT.jar` (y también `target/original-pdf-tool-1.0-SNAPSHOT.jar`).


## Ejecución
Ejecuta el JAR con Java:

```bash
java -jar target/pdf-tool-1.0-SNAPSHOT.jar --help
```

En Windows PowerShell (rutas con `\`):

```powershell
java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar --help
```


## Ayuda integrada
Si llamas sin argumentos, o con `-h`/`--help`, verás el resumen de comandos:

```
PDF Tool - comandos:
  merge -o <out.pdf> <in1.pdf> <carpeta> [...]  (Acepta archivos y/o carpetas)
  split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>
  compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]
  rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]
  watermark <in.pdf> -o <out.pdf> -text "CONFIDENTIAL" [-opacity 0.2]
  text <in.pdf> [-o <out.txt>]
  info <in.pdf>
  encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]
  decrypt <in.pdf> -o <out.pdf> -pwd <password>
```


## Comandos y ejemplos
A continuación, cada comando con su sintaxis y ejemplos prácticos.

### 1) merge — unir varios PDFs o carpetas
- Sintaxis: `merge -o <salida.pdf> <in1.pdf> <carpeta> [...]`
  - Acepta tanto archivos PDF individuales como rutas a carpetas.
  - Si se indica una carpeta, se añaden todos los archivos `.pdf` de su interior ordenados alfabéticamente.
- Ejemplos:
  ```powershell
  # Mezclando archivos individuales
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\salida\unido.pdf .\docs\a.pdf .\docs\b.pdf

  # Uniendo todos los PDFs de una carpeta
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\salida\unido.pdf .\docs\carpeta_pdfs

  # Uso mixto
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar merge -o .\salida\final.pdf portada.pdf .\docs\contenido anexo.pdf
  ```

### 2) split — dividir por rangos de páginas
- Sintaxis: `split <in.pdf> -ranges "1-3,7,10-*" -o <prefijo>`
  - `*` indica hasta el final.
  - Crea ficheros: `<prefijo>_part001.pdf`, `<prefijo>_part002.pdf`, ...
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar split .\docs\origen.pdf -ranges "1-3,7,10-*" -o .\salida\corte
  ```

### 3) compress — recomprimir imágenes del PDF
- Sintaxis: `compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]`
  - `-q`: calidad JPEG (0.1 a 1.0). Por defecto 0.7
  - `--max-dpi`: reducción heurística simple. Si se indica, puede reducir el tamaño efectivo de las imágenes.
  - `--remove-metadata`: elimina metadatos del documento.
- Notas técnicas importantes:
  - Solo se recomprimen imágenes raster incrustadas. Imágenes con canal alfa se ignoran por defecto para evitar artefactos (puedes aplanarlas en el código si lo necesitas).
  - Se usa `JPEGFactory.createFromImage(...)` de PDFBox para generar XObjects válidos.
  - La reducción es heurística y conservadora; puedes ajustar la calidad y el parámetro `--max-dpi` según el caso.
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar compress .\docs\pesado.pdf -o .\salida\ligero.pdf -q 0.65 --max-dpi 150 --remove-metadata
  ```

### 4) rotate — rotar páginas
- Sintaxis: `rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]`
  - `-pages` es opcional; si no se indica, se aplica a todas las páginas.
- Ejemplos:
  ```powershell
  # Rotar todo el documento 90 grados
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar rotate .\docs\origen.pdf -o .\salida\rotado.pdf -deg 90

  # Rotar solo páginas 1 a 3 y 5
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar rotate .\docs\origen.pdf -o .\salida\rotado_sel.pdf -deg 270 -pages "1-3,5"
  ```

### 5) watermark — marca de agua de texto centrada
- Sintaxis: `watermark <in.pdf> -o <out.pdf> -text "TEXTO" [-opacity 0.2]`
  - El texto se coloca real y visualmente centrado en cada página, con rotación de 45º por defecto y opacidad configurable.
  - Fuente por defecto: Helvetica Bold, tamaño 64. Puedes ajustar en el código si lo necesitas.
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar watermark .\docs\origen.pdf -o .\salida\wm.pdf -text "CONFIDENCIAL" -opacity 0.25
  ```

### 6) text — extraer texto
- Sintaxis: `text <in.pdf> [-o <out.txt>]`
  - Sin `-o`, lo imprime por consola.
- Ejemplos:
  ```powershell
  # Imprimir por consola
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar text .\docs\in.pdf

  # Guardar a archivo
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar text .\docs\in.pdf -o .\salida\texto.txt
  ```

### 7) info — información básica del PDF
- Sintaxis: `info <in.pdf>`
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar info .\docs\in.pdf
  ```

### 8) encrypt — cifrar PDF con permisos
- Sintaxis: `encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]`
  - `-ownerPwd` requerido.
  - `-userPwd` opcional (para abrir el documento).
  - `-perm` establece permisos: admite `print`, `copy`, `modify` (separados por comas).
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar encrypt .\docs\in.pdf -o .\salida\enc.pdf -ownerPwd MiClaveAdmin -userPwd Lectura -perm print,copy
  ```

### 9) decrypt — quitar cifrado
- Sintaxis: `decrypt <in.pdf> -o <out.pdf> -pwd <password>`
- Ejemplo:
  ```powershell
  java -jar .\target\pdf-tool-1.0-SNAPSHOT.jar decrypt .\docs\enc.pdf -o .\salida\dec.pdf -pwd MiClaveAdmin
  ```


## Notas de diseño y comportamiento
- Marca de agua centrada: el texto se centra de forma geométrica y visual, compensando ancho/alto del texto tras aplicar la rotación.
- Compresión de imágenes: evita recomprimir imágenes con alpha y usa JPEG con calidad ajustable. Para documentos escaneados suele funcionar bien en el rango 0.5–0.8. Si la calidad visual cae, sube `-q` o elimina `--max-dpi`.
- Metadatos: `--remove-metadata` limpia la información del documento para reducir tamaño y privacidad.


## Solución de problemas
- «Comando no reconocido»: revisa que estás usando alguno de los listados en la ayuda.
- «Debe indicar -o <out.pdf>» u otros mensajes de uso: el comando requiere esas opciones. Mira los ejemplos.
- Problemas de rutas en Windows: usa `\` o comillas si hay espacios, por ejemplo `"C:\\Mi Carpeta\\in.pdf"`.
- PDFs protegidos: para operar sobre un PDF cifrado quizá necesites usar `decrypt` primero con la contraseña correcta.
- Resultados de compresión extraños: prueba sin `--max-dpi`, incrementa `-q`, o evita recomprimir documentos con imágenes muy pequeñas o con transparencias.


## Desarrollo
- Código Java 17, usa Apache PDFBox.
- Estructura principal en `src/main/java/com/alejandro/pdftool/` (`App.java`, `PdfOps.java`, `CliUtil.java`).


## Licencia
Este proyecto se distribuye tal cual, sin garantías. Ajusta la licencia según tus necesidades.
