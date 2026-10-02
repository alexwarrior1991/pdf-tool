package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpParsingException;
import org.apache.xmpbox.xml.XmpSerializer;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Lectura y escritura de título, autor, asunto y palabras clave. Se guardan en el diccionario /Info y, si el
 * documento tiene metadatos XMP (Acrobat los muestra con preferencia), también allí para que no se contradigan.
 */
final class MetadataSupport {

    private MetadataSupport() {
    }

    static DocumentMetadata read(PDDocument doc) {
        PDDocumentInformation info = doc.getDocumentInformation();
        DocumentMetadata fromInfo = new DocumentMetadata(info.getTitle(), info.getAuthor(), info.getSubject(),
                info.getKeywords());
        XMPMetadata xmp = parseXmp(doc);
        if (xmp == null) {
            return fromInfo;
        }
        DublinCoreSchema dc = xmp.getDublinCoreSchema();
        AdobePDFSchema pdf = xmp.getAdobePDFSchema();
        return new DocumentMetadata(
                fromInfo.title() != null ? fromInfo.title() : dcTitle(dc),
                fromInfo.author() != null ? fromInfo.author() : dcCreators(dc),
                fromInfo.subject() != null ? fromInfo.subject() : dcDescription(dc),
                fromInfo.keywords() != null ? fromInfo.keywords() : pdf == null ? null : pdf.getKeywords());
    }

    /**
     * Aplica los metadatos al documento.
     *
     * @return {@code false} si el documento tenía metadatos XMP que no se han podido leer y se han dejado como
     * estaban (el diccionario /Info sí se actualiza siempre)
     */
    static boolean write(PDDocument doc, DocumentMetadata metadata) throws IOException {
        Calendar now = Calendar.getInstance();
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle(metadata.title());
        info.setAuthor(metadata.author());
        info.setSubject(metadata.subject());
        info.setKeywords(metadata.keywords());
        info.setModificationDate(now);

        PDMetadata stream = doc.getDocumentCatalog().getMetadata();
        if (stream == null) {
            return true;
        }
        XMPMetadata xmp = parseXmp(doc);
        if (xmp == null) {
            return false;
        }
        DublinCoreSchema dc = xmp.getDublinCoreSchema() != null ? xmp.getDublinCoreSchema()
                : xmp.createAndAddDublinCoreSchema();
        dc.getContainer().removePropertiesByName(DublinCoreSchema.TITLE);
        dc.getContainer().removePropertiesByName(DublinCoreSchema.CREATOR);
        dc.getContainer().removePropertiesByName(DublinCoreSchema.DESCRIPTION);
        dc.getContainer().removePropertiesByName(DublinCoreSchema.SUBJECT);
        if (metadata.title() != null) dc.setTitle(metadata.title());
        if (metadata.author() != null) dc.addCreator(metadata.author());
        if (metadata.subject() != null) dc.setDescription(metadata.subject());
        if (metadata.keywords() != null) {
            for (String keyword : splitKeywords(metadata.keywords())) dc.addSubject(keyword);
        }

        AdobePDFSchema pdf = xmp.getAdobePDFSchema() != null ? xmp.getAdobePDFSchema() : xmp.createAndAddAdobePDFSchema();
        pdf.getContainer().removePropertiesByName(AdobePDFSchema.KEYWORDS);
        if (metadata.keywords() != null) pdf.setKeywords(metadata.keywords());

        XMPBasicSchema basic = xmp.getXMPBasicSchema() != null ? xmp.getXMPBasicSchema() : xmp.createAndAddXMPBasicSchema();
        basic.setModifyDate(now);
        basic.setMetadataDate(now);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new XmpSerializer().serialize(xmp, out, true);
        } catch (TransformerException e) {
            return false;
        }
        stream.importXMPMetadata(out.toByteArray());
        return true;
    }

    private static XMPMetadata parseXmp(PDDocument doc) {
        PDMetadata stream = doc.getDocumentCatalog().getMetadata();
        if (stream == null) {
            return null;
        }
        try (InputStream in = stream.exportXMPMetadata()) {
            DomXmpParser parser = new DomXmpParser();
            parser.setStrictParsing(false);
            return parser.parse(in);
        } catch (XmpParsingException | IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<String> splitKeywords(String keywords) {
        List<String> result = new ArrayList<>();
        Arrays.stream(keywords.split("[,;]")).map(String::strip).filter(k -> !k.isEmpty()).forEach(result::add);
        return result;
    }

    private static String dcTitle(DublinCoreSchema dc) {
        try {
            return dc == null ? null : dc.getTitle();
        } catch (BadFieldValueException e) {
            return null;
        }
    }

    private static String dcDescription(DublinCoreSchema dc) {
        try {
            return dc == null ? null : dc.getDescription();
        } catch (BadFieldValueException e) {
            return null;
        }
    }

    private static String dcCreators(DublinCoreSchema dc) {
        List<String> creators = dc == null ? null : dc.getCreators();
        return creators == null || creators.isEmpty() ? null : String.join(", ", creators);
    }
}
