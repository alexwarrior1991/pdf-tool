package com.alejandro.pdftool;

import com.alejandro.pdftool.CliUtil.PageRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliUtilTest {

    @Test
    void parsesRangesWithSpacesAndOpenEnd() {
        assertEquals(List.of(new PageRange(1, 3), new PageRange(7, 7), new PageRange(10, Integer.MAX_VALUE)),
                CliUtil.parseRanges(" 1 - 3, 7 ,10-*"));
    }

    @Test
    void emptySpecMeansNoRanges() {
        assertTrue(CliUtil.parseRanges("  ").isEmpty());
        assertTrue(CliUtil.parseRanges(null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"5-", "1-2-3", "-3", "0", "5-3", "abc", "1;2", "99999999999"})
    void rejectsInvalidRangesWithClearMessage(String spec) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CliUtil.parseRanges(spec));
        assertTrue(e.getMessage().matches(".*([Rr]ange|[Pp]age).*"), e.getMessage());
    }

    @Test
    void resolvesPagesInWrittenOrderWithoutDuplicates() {
        assertEquals(List.of(5, 1, 2, 3), CliUtil.resolvePages(CliUtil.parseRanges("5,1-3,2"), 10));
        assertEquals(List.of(1, 2), CliUtil.resolvePages(CliUtil.parseRanges("1-*"), 2));
        assertEquals(List.of(1, 2, 3), CliUtil.resolvePages(List.of(), 3));
        assertTrue(CliUtil.resolvePages(CliUtil.parseRanges("8-9"), 3).isEmpty());
    }

    @Test
    void buildsCompactRangeSpec() {
        assertEquals("1-3,7,9-10", CliUtil.toRangeSpec(List.of(9, 1, 2, 3, 7, 10, 2)));
        assertEquals("", CliUtil.toRangeSpec(List.of()));
        assertEquals("4", CliUtil.toRangeSpec(Set.of(4)));
    }

    @Test
    void extractsPositionalArguments() {
        assertEquals(List.of("a.pdf", "dir"),
                CliUtil.positionals(List.of("-o", "out.pdf", "a.pdf", "--flag", "dir", "-size", "a4"),
                        Set.of("-o", "-size"), Set.of("--flag")));
    }

    @Test
    void parsesNumbersAndColors() {
        assertEquals(0.6, CliUtil.parseDouble("0,6", "-q", 0.1, 1), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> CliUtil.parseDouble("2", "-q", 0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> CliUtil.parseInt("x", "-dpi", 1, 10));
        assertEquals(new Color(0xC8, 0, 0), CliUtil.parseColor("#C80000", "-color"));
        assertThrows(IllegalArgumentException.class, () -> CliUtil.parseColor("red", "-color"));
    }

    @Test
    void naturalOrderPutsSmallerNumbersFirst() {
        List<String> names = new java.util.ArrayList<>(List.of("doc10.pdf", "Doc2.pdf", "doc1.pdf", "anexo.pdf"));
        names.sort(InputFiles::compareNatural);
        assertEquals(List.of("anexo.pdf", "doc1.pdf", "Doc2.pdf", "doc10.pdf"), names);
    }
}
