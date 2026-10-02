package book.example.services;

import book.example.Entity.AppUser;
import book.example.Entity.PasswordResetToken;
import book.example.Repository.PasswordResetTokenRepository;
import book.example.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Value("${spring.mail.username:}")
    private String senderAddress;

    public PasswordResetService(UserRepository userRepository,
                               PasswordResetTokenRepository tokenRepository,
                               PasswordEncoder passwordEncoder,
                               JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        // Check service configuration before looking up the account so a
        // misconfigured mail service cannot reveal whether an email exists.
        if (senderAddress == null || senderAddress.isBlank()) {
            throw new IllegalStateException("Password reset email is not configured.");
        }
        AppUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return;
        }

        tokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        PasswordResetToken resetToken = new PasswordResetToken(
                user.getId(), hash(rawToken), LocalDateTime.now().plusMinutes(30));
        tokenRepository.save(resetToken);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(user.getEmail());
        message.setSubject("Reset your DocuAI password");
        message.setText("Use this link within 30 minutes to set a new password:\n\n"
                + publicUrl.replaceAll("/$", "") + "/?resetToken=" + rawToken
                + "\n\nIf you did not request this, ignore this email.");
        mailSender.send(message);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Password reset link is invalid.");
        }
        PasswordResetToken resetToken = tokenRepository.findByTokenHashAndUsedAtIsNull(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Password reset link is invalid or expired."));
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Password reset link is invalid or expired.");
        }

        AppUser user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Password reset link is invalid."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be 12 or more characters and at most 72 UTF-8 bytes.");
        }
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to secure password reset token.", exception);
        }
    }
}