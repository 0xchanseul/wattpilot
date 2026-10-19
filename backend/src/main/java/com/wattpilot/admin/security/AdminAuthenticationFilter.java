package com.wattpilot.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request to the admin API from the admin session cookie.
 *
 * <p>It acts only on {@code /api/v1/admin} paths. A missing or invalid cookie simply leaves the request
 * unauthenticated, and the authorization rules then answer 401.
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_ROLE = "ADMIN";

    private final AdminTokenProvider adminTokenProvider;

    public AdminAuthenticationFilter(AdminTokenProvider adminTokenProvider) {
        this.adminTokenProvider = adminTokenProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(AdminTokenProvider.COOKIE_PATH) || path.startsWith(AdminTokenProvider.COOKIE_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = cookieValue(request);
        if (token != null && adminTokenProvider.isValid(token)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE))));
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AdminTokenProvider.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
