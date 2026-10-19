package com.example.ebookstore.dto;

import java.time.Instant;

/**
 * Whether cancelling is allowed right now (schema {@code CancellationInfo}).
 *
 * @param cancellableUntil always {@code createdAt} + the cancellation window (48 hours), whatever the status
 * @param reason           why cancelling is not possible, or {@code null} when {@code cancellable} is true
 */
public record CancellationInfo(boolean cancellable, Instant cancellableUntil, String reason) {
}
