package com.kamaldairy.kamal_dairy_backend.security;

import com.kamaldairy.kamal_dairy_backend.model.User;
import com.kamaldairy.kamal_dairy_backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtUtil.isTokenValid(token)) {

            String email = jwtUtil.extractEmail(token);

            User user = userRepository.findByEmail(email).orElse(null);

            // Only authenticate verified accounts. A disabled account must not
            // be able to keep using a token issued before it was disabled.
            // A password reset bumps the user's token version, so every token
            // issued before it stops working at once.
            int current = user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();

            if (user != null && user.isEnabled() && jwtUtil.extractTokenVersion(token) == current) {

                // The role is taken from the DATABASE, not from the token claim.
                // If an admin is demoted, the change takes effect immediately
                // instead of waiting for the old token to expire.
                String role = user.getRole() == null ? "ROLE_USER" : user.getRole();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                List.of(new SimpleGrantedAuthority(role))
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
