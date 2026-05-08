package com.example.photoapp.common.pagination;

import java.util.List;

/**
 * Page of items returned from a cursor-paginated endpoint. {@code nextCursor}
 * is null when the caller has reached the end of the stream.
 */
public record CursorPage<T>(List<T> items, String nextCursor, int limit) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, int limit) {
        return new CursorPage<>(List.copyOf(items), nextCursor, limit);
    }
}
