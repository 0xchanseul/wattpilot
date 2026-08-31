package com.wattpilot.common.security;

import com.wattpilot.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from its {@code Authorization: Bearer <jwt>} header.
 *
 * <p>A request without the header passes through unauthenticated; whether that is allowed is
 * decided by the authorization rules, not here. A request carrying an unusable token is rejected
 * immediately, so an expired token is reported as expired instead of as missing authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long userId = jwtTokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()));
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId), null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            // Exceptions thrown inside a filter never reach @RestControllerAdvice, so the resolver
            // is invoked directly to keep the shared problem+json error contract.
            handlerExceptionResolver.resolveException(request, response, null, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
