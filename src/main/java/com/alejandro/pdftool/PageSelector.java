package com.alejandro.pdftool;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Crea un documento nuevo solo con las páginas elegidas, en el orden pedido. Se apoya en el {@link Splitter} de
 * PDFBox, que copia bien los atributos heredados, enlaces, estructura y metadatos, y no arrastra el contenido de
 * las páginas descartadas (que podrían seguir dentro del archivo si solo se quitaran del árbol de páginas).
 */
final class PageSelector extends Splitter {

    private final Set<Integer> keep;
    private int pageNumber;

    private PageSelector(Set<Integer> keep) {
        this.keep = keep;
    }

    /**
     * @param pages páginas 1-based, en el orden en que deben quedar (sin repetir)
     * @return documento nuevo; debe guardarse antes de cerrar {@code source}
     */
    static PDDocument select(PDDocument source, List<Integer> pages) throws IOException {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No hay páginas que conservar.");
        }
        List<PDDocument> result = new PageSelector(new TreeSet<>(pages)).split(source);
        PDDocument selected = result.get(0);
        reorder(selected, pages);
        return selected;
    }

    @Override
    protected boolean splitAtPage(int pageNumber) {
        return false; // todo en un único documento
    }

    @Override
    protected void processPage(PDPage page) throws IOException {
        pageNumber++;
        if (keep.contains(pageNumber)) {
            super.processPage(page);
        }
    }

    /** El Splitter conserva el orden original; aquí se aplica el orden pedido. */
    private static void reorder(PDDocument doc, List<Integer> wanted) {
        List<Integer> ascending = new ArrayList<>(new TreeSet<>(wanted));
        if (ascending.equals(wanted)) {
            return;
        }
        PDPageTree tree = doc.getPages();
        Map<Integer, PDPage> byOriginalNumber = new HashMap<>();
        List<PDPage> current = new ArrayList<>();
        tree.forEach(current::add);
        for (int i = 0; i < current.size(); i++) {
            byOriginalNumber.put(ascending.get(i), current.get(i));
        }
        current.forEach(tree::remove);
        for (int number : wanted) {
            tree.add(byOriginalNumber.get(number));
        }
    }
}
