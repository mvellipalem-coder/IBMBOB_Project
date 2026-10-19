package com.example.ebookstore.dto;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;

import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;

/**
 * The allowed values of the {@code sort} query parameter (see openapi.yaml, parameters BookSort and SearchSort).
 *
 * <p>Only values from this enum can ever reach the database, so a client can never choose an arbitrary column.
 * Every sort ends with the book id so that two books with the same price (or title...) always appear in the
 * same order, which keeps pagination stable.
 */
public enum BookSort {

    /** Search only: title matches first, then author matches, then ISBN matches. Built by the specification. */
    RELEVANCE("relevance", Sort.unsorted()),
    NEWEST("newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    TITLE_ASC("title_asc", Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"))),
    TITLE_DESC("title_desc", Sort.by(Sort.Order.desc("title").ignoreCase(), Sort.Order.asc("id"))),
    PRICE_ASC("price_asc", Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
    PRICE_DESC("price_desc", Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"))),
    RATING_DESC("rating_desc", Sort.by(Sort.Order.desc("ratingAvg"), Sort.Order.desc("ratingCount"),
            Sort.Order.asc("id")));

    private final String parameterValue;
    private final Sort sort;

    BookSort(String parameterValue, Sort sort) {
        this.parameterValue = parameterValue;
        this.sort = sort;
    }

    /** The Spring Data sort. Empty for {@link #RELEVANCE}, which is ordered by the specification instead. */
    public Sort toSort() {
        return sort;
    }

    /**
     * Converts the text of the {@code sort} parameter. {@code null} gives the default.
     *
     * @param allowRelevance {@code true} for the search endpoint, {@code false} for the plain list
     * @throws ApiException {@code VALIDATION_FAILED} (HTTP 400) when the value is not allowed
     */
    public static BookSort fromParameter(String value, boolean allowRelevance) {
        BookSort defaultSort = allowRelevance ? RELEVANCE : NEWEST;
        if (value == null) {
            return defaultSort;
        }
        List<BookSort> allowed = Arrays.stream(values())
                .filter(s -> allowRelevance || s != RELEVANCE)
                .toList();
        return allowed.stream()
                .filter(s -> s.parameterValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                        List.of(new ApiFieldError("sort", "must be one of: " + allowed.stream()
                                .map(s -> s.parameterValue).collect(Collectors.joining(", "))))));
    }
}
