package com.wattpilot.common.security;

/**
 * Principal placed in the security context for an authenticated request.
 *
 * <p>Holds only the user id: the access token carries no profile data, so anything else
 * would be a stale copy of the database. Controllers obtain it with
 * {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(Long userId) {
}
