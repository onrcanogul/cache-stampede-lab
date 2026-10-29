package com.onurcanogul.stampede.report;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class Table {

    private final String[] header;
    private final List<String[]> rows = new ArrayList<>();

    public Table(String... header) {
        this.header = header;
    }

    public Table row(Object... cells) {
        if (cells.length != header.length) {
            throw new IllegalArgumentException("expected " + header.length + " cells, got " + cells.length);
        }
        String[] row = new String[cells.length];
        for (int i = 0; i < cells.length; i++) {
            row[i] = format(cells[i]);
        }
        rows.add(row);
        return this;
    }

    public void print(PrintStream out) {
        int[] widths = new int[header.length];
        for (int i = 0; i < header.length; i++) {
            widths[i] = header[i].length();
            for (String[] row : rows) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        out.println(line(header, widths));
        out.println(separator(widths));
        for (String[] row : rows) {
            out.println(line(row, widths));
        }
    }

    public static String millis(Duration duration) {
        return Math.round(duration.toNanos() / 1_000_000.0) + " ms";
    }

    private static String format(Object cell) {
        if (cell instanceof Duration duration) {
            return millis(duration);
        }
        return String.valueOf(cell);
    }

    private static String line(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("| ");
        for (int i = 0; i < cells.length; i++) {
            String padded = i == 0 ? padRight(cells[i], widths[i]) : padLeft(cells[i], widths[i]);
            sb.append(padded).append(" |");
            if (i < cells.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String separator(int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            sb.append(i == 0 ? "-" : "-").append("-".repeat(widths[i])).append(i == 0 ? "-|" : ":|");
        }
        return sb.toString();
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return " ".repeat(width - text.length()) + text;
    }
}
