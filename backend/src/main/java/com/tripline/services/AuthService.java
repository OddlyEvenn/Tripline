package com.tripline.services;

import com.tripline.dto.AuthDTO;
import com.tripline.exceptions.BadRequestException;
import com.tripline.exceptions.ConflictException;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.User;
import com.tripline.repositories.UserRepository;
import com.tripline.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    // -------------------------------------------------------
    // Register
    // -------------------------------------------------------

    @Transactional
    public AuthDTO.AuthResponse register(AuthDTO.RegisterRequest request, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists");
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(User.Role.valueOf(request.getRole().toUpperCase()))
                .isVerified(false)
                .verificationOtp(otp)
                .otpExpiry(LocalDateTime.now().plusMinutes(10))
                .build();

        user = userRepository.save(user);

        emailService.sendVerificationOtp(user.getEmail(), user.getName(), otp);

        return new AuthDTO.AuthResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), "Registration successful. Please verify your email with the OTP sent.");
    }

    @CacheEvict(value = "users", key = "#request.email")
    @Transactional
    public AuthDTO.AuthResponse verifyEmail(AuthDTO.VerifyEmailRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getIsVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        if (user.getVerificationOtp() == null || !user.getVerificationOtp().equals(request.getOtp())) {
            throw new BadRequestException("Invalid OTP");
        }

        if (user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new BadRequestException("OTP has expired");
        }

        user.setIsVerified(true);
        user.setVerificationOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        issueTokenCookies(user, response);

        return new AuthDTO.AuthResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), "Email verified successfully");
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------

    @Transactional
    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getIsVerified()) {
            // Generate a fresh 6-digit OTP
            String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
            user.setVerificationOtp(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            emailService.sendVerificationOtp(user.getEmail(), user.getName(), otp);

            throw new BadRequestException("Please verify your email. A fresh OTP has been sent to your email.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        issueTokenCookies(user, response);

        return new AuthDTO.AuthResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), "Login successful");
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------

    public void logout(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
        clearCookie(response, REFRESH_TOKEN_COOKIE);
    }

    // -------------------------------------------------------
    // Get Current User
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public AuthDTO.AuthResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new AuthDTO.AuthResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), null);
    }

    // -------------------------------------------------------
    // Forgot Password
    // -------------------------------------------------------

    @Transactional
    public void forgotPassword(AuthDTO.ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email address"));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token);
    }

    // -------------------------------------------------------
    // Reset Password
    // -------------------------------------------------------

    @CacheEvict(value = "users", allEntries = true)
    @Transactional
    public void resetPassword(AuthDTO.ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getPasswordResetTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getPasswordResetTokenExpiry())) {
            throw new BadRequestException("Password reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    // -------------------------------------------------------
    // Update Profile
    // -------------------------------------------------------

    @CacheEvict(value = "users", key = "#email")
    @Transactional
    public AuthDTO.AuthResponse updateProfile(String email, AuthDTO.UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getName() != null)
            user.setName(request.getName());
        if (request.getPhoneNumber() != null)
            user.setPhoneNumber(request.getPhoneNumber());

        user = userRepository.save(user);
        return new AuthDTO.AuthResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), "Profile updated successfully");
    }

    // -------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------

    private void issueTokenCookies(User user, HttpServletResponse response) {
        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        addCrossOriginCookie(response, buildCookie(ACCESS_TOKEN_COOKIE, accessToken,
                (int) (jwtUtils.getAccessExpirationMs() / 1000)));
        addCrossOriginCookie(response, buildCookie(REFRESH_TOKEN_COOKIE, refreshToken,
                (int) (jwtUtils.getRefreshExpirationMs() / 1000)));
    }

    private Cookie buildCookie(String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Required for HTTPS in production
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // SameSite=None is required for cross-domain requests (Vercel → Render)
        // We set this manually via the Set-Cookie header attribute
        return cookie;
    }

    public void addCrossOriginCookie(HttpServletResponse response, Cookie cookie) {
        // Append SameSite=None to the Set-Cookie header for cross-origin support
        String header = cookie.getName() + "=" + (cookie.getValue() == null ? "" : cookie.getValue())
                + "; Path=" + cookie.getPath()
                + "; Max-Age=" + cookie.getMaxAge()
                + "; HttpOnly"
                + "; Secure"
                + "; SameSite=None";
        response.addHeader("Set-Cookie", header);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        String header = name + "="
                + "; Path=/"
                + "; Max-Age=0"
                + "; HttpOnly"
                + "; Secure"
                + "; SameSite=None";
        response.addHeader("Set-Cookie", header);
    }
}
