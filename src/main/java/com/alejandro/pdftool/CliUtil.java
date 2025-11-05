package com.alejandro.pdftool;

import java.util.Arrays;
import java.util.List;

public class CliUtil {
    public static String optValue(List<String> args, String flag) {
        int i = args.indexOf(flag);
        return (i >= 0 && i < args.size() - 1) ? args.get(i + 1) : null;
    }

    public record PageRange(int start, int end) {
    }

    public static List<PageRange> parseRanges(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        return Arrays.stream(spec.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(p -> p.contains("-") ? p.split("-") : new String[]{p})
                .map(ab -> ab.length == 2
                        ? new PageRange(Integer.parseInt(ab[0]), "*".equals(ab[1]) ? Integer.MAX_VALUE : Integer.parseInt(ab[1]))
                        : new PageRange(Integer.parseInt(ab[0]), Integer.parseInt(ab[0]))
                )
                .toList();
    }

    public static boolean containsPage(List<PageRange> ranges, int pageIndex1Based) {
        return ranges.stream().anyMatch(r -> pageIndex1Based >= r.start() && pageIndex1Based <= r.end());
    }
}
