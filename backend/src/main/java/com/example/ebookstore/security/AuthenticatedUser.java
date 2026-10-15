package com.example.ebookstore.security;

import com.example.ebookstore.entity.Role;

/**
 * The logged-in user, as seen by controllers and services.
 *
 * <p>This is the <strong>only</strong> trusted source of "who is calling". It is built from the verified JWT and
 * the database by {@link JwtAuthenticationConverter}. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}. A user id sent in a URL, query string or body must
 * never be used to decide whose data to load.
 *
 * @param id   database id of the user
 * @param role CUSTOMER or ADMIN
 */
public record AuthenticatedUser(long id, Role role) {
}
