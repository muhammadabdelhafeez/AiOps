package org.kfh.aiops.commandcenter.dto;

import java.util.List;

/** Lightweight page contract used by the static Command Center frontend. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(List<T> rows, int page, int size) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.min(Math.max(size, 1), 100);
        var from = Math.min(safePage * safeSize, rows.size());
        var to = Math.min(from + safeSize, rows.size());
        var pages = rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / safeSize);
        return new PageResponse<>(List.copyOf(rows.subList(from, to)), safePage, safeSize, rows.size(), pages);
    }
}

