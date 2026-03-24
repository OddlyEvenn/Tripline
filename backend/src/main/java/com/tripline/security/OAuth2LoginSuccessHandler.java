package com.tripline.security;

import com.tripline.models.User;
import com.tripline.repositories.UserRepository;
import com.tripline.utils.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            log.error("OAuth2 user has no email attribute");
            response.sendRedirect(frontendUrl + "/login?error=no_email");
            return;
        }

        // Find or create user
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
        } else {
            // Create new OAuth user
            user = User.builder()
                    .email(email)
                    .name(name != null ? name : "Google User")
                    // generate random unguessable password for oauth users
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(User.Role.USER)
                    .isVerified(true)
                    .build();
            user = userRepository.save(user);
            log.info("Created new user via Google OAuth: {}", email);
        }

        // Generate standard JWT cookies
        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        Cookie ac = new Cookie("access_token", accessToken);
        ac.setHttpOnly(true);
        ac.setPath("/");
        ac.setMaxAge((int) (jwtUtils.getAccessExpirationMs() / 1000));

        Cookie rc = new Cookie("refresh_token", refreshToken);
        rc.setHttpOnly(true);
        rc.setPath("/");
        rc.setMaxAge((int) (jwtUtils.getRefreshExpirationMs() / 1000));

        response.addCookie(ac);
        response.addCookie(rc);

        // Redirect to frontend dashboard (logged in)
        response.sendRedirect(frontendUrl + "/dashboard");
    }
}
