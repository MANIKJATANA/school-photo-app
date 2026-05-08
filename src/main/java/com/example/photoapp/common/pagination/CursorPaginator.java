package com.example.photoapp.common.pagination;

import com.example.photoapp.common.error.Errors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cursor-paginated list helper. Collapses the (decode cursor → compose
 * predicate → fetch limit+1 → trim → encode next cursor → map to DTOs)
 * sequence that every Phase 1 list endpoint repeats.
 *
 * <p>The cursor sort is fixed at {@code (createdAt DESC, id DESC)} — the
 * standard "newest first, deterministic tie-break" pattern. Entities expose
 * {@code createdAt} and {@code id} via the {@code RowKeys} extractor so this
 * helper stays generic across entity types that don't share a base class
 * (e.g., {@code Student} extends {@code Auditable} but {@code StudentClass}
 * does not).
 */
@Component
public class CursorPaginator {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    /** Extracts the (createdAt, id) keys of an entity row. */
    public interface RowKeys<T> {
        Instant createdAt(T row);
        UUID id(T row);
    }

    private final CursorCodec cursorCodec;

    public CursorPaginator(CursorCodec cursorCodec) {
        this.cursorCodec = cursorCodec;
    }

    public <T, R> CursorPage<R> paginate(
            JpaSpecificationExecutor<T> repo,
            Specification<T> baseSpec,
            String cursor,
            Integer requestedLimit,
            RowKeys<T> keys,
            Function<T, R> toDto) {

        int limit = clampLimit(requestedLimit);
        CursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        Specification<T> spec = baseSpec;
        if (decoded != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), decoded.sortKey()),
                    cb.and(
                            cb.equal(root.get("createdAt"), decoded.sortKey()),
                            cb.lessThan(root.get("id"), decoded.id()))));
        }

        var page = repo.findAll(
                spec,
                PageRequest.of(0, limit + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<T> rows = page.getContent();
        boolean hasMore = rows.size() > limit;
        List<T> trimmed = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore) {
            T last = trimmed.get(trimmed.size() - 1);
            nextCursor = cursorCodec.encode(new CursorCodec.Cursor(keys.createdAt(last), keys.id(last)));
        }
        return CursorPage.of(trimmed.stream().map(toDto).toList(), nextCursor, limit);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1)     throw new Errors.BadRequest("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }
}
