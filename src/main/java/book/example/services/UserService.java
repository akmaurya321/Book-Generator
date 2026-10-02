package book.example.services;

import book.example.Entity.AppUser;
import book.example.Repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser registerLocalUser(String email, String name, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("An account with this email already exists.");
        }
        if (password == null || password.length() < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be 12 or more characters and at most 72 UTF-8 bytes.");
        }

        AppUser user = new AppUser(normalizedEmail, name.trim(), "local", null, null);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> authenticateLocalUser(String email, String password) {
        if (email == null || password == null ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.empty();
        }

        return userRepository.findByEmail(normalizeEmail(email))
                .filter(user -> user.getPasswordHash() != null &&
                        !user.getPasswordHash().isBlank() &&
                        passwordEncoder.matches(password, user.getPasswordHash()));
    }

    @Transactional
    public AppUser upsertOAuth2User(OAuth2User oauthUser) {
        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google user email is required for sign in.");
        }
        email = normalizeEmail(email);

        String name = oauthUser.getAttribute("name");
        String avatarUrl = oauthUser.getAttribute("picture");
        String providerUserId = oauthUser.getName();

        Optional<AppUser> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            user.setName(name != null ? name : user.getName());
            if (!"/api/auth/avatar".equals(user.getAvatarUrl())) {
                user.setAvatarUrl(avatarUrl != null ? avatarUrl : user.getAvatarUrl());
            }
            user.setProvider("google");
            user.setProviderUserId(providerUserId);
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        }

        AppUser newUser = new AppUser(email, name != null ? name : "Google User", "google", providerUserId, avatarUrl);
        return userRepository.save(newUser);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
