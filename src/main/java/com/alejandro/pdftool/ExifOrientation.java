package com.alejandro.pdftool;

/**
 * Lee la etiqueta de orientación EXIF (0x0112) de un JPEG. Los móviles suelen guardar la foto "tumbada" y
 * anotar en esa etiqueta cómo hay que girarla para verla bien.
 */
final class ExifOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    /**
     * Grados en sentido horario que hay que girar la imagen para verla derecha: 0, 90, 180 o 270. Las variantes
     * con espejo se tratan como su giro equivalente.
     */
    static int rotationDegrees(byte[] jpeg) {
        return switch (read(jpeg)) {
            case 3, 4 -> 180;
            case 6, 7 -> 90;
            case 5, 8 -> 270;
            default -> 0;
        };
    }

    /** Valor EXIF de orientación (1-8), o 1 si no hay o no se puede leer. */
    static int read(byte[] b) {
        try {
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int pos = 2;
            while (pos + 4 <= b.length) {
                if ((b[pos] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = b[pos + 1] & 0xFF;
                if (marker == 0xFF) { // relleno
                    pos++;
                    continue;
                }
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    pos += 2;
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) { // empieza la imagen: ya no hay más metadatos
                    return 1;
                }
                int length = u16(b, pos + 2, false);
                if (marker == 0xE1 && length >= 8 && isExifHeader(b, pos + 4)) {
                    return orientationFromTiff(b, pos + 10, pos + 2 + length);
                }
                pos += 2 + length;
            }
        } catch (IndexOutOfBoundsException e) {
            // datos EXIF truncados o corruptos
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] b, int pos) {
        return b[pos] == 'E' && b[pos + 1] == 'x' && b[pos + 2] == 'i' && b[pos + 3] == 'f'
                && b[pos + 4] == 0 && b[pos + 5] == 0;
    }

    private static int orientationFromTiff(byte[] b, int tiff, int end) {
        boolean littleEndian;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, littleEndian);
        if (ifd + 2 > end) {
            return 1;
        }
        int entries = u16(b, ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            if (u16(b, entry, littleEndian) == TAG_ORIENTATION) {
                int value = u16(b, entry + 8, littleEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int pos, boolean littleEndian) {
        int b0 = b[pos] & 0xFF;
        int b1 = b[pos + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static long u32(byte[] b, int pos, boolean littleEndian) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = littleEndian ? 8 * i : 8 * (3 - i);
            value |= (long) (b[pos + i] & 0xFF) << shift;
        }
        return value;
    }
}
