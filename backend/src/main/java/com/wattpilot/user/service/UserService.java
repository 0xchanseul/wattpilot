package com.wattpilot.user.service;

import com.wattpilot.common.PriceArea;
import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.user.entity.User;
import com.wattpilot.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Owns account persistence, including password hashing and email normalisation, so no other
 * module has to know how a user is stored.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String name, PriceArea defaultPriceArea) {
        String normalisedEmail = normaliseEmail(email);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.register(normalisedEmail, passwordEncoder.encode(rawPassword), name.trim(), defaultPriceArea);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent sign-ups can both pass the check above; the unique index is what
            // actually enforces it, so the same conflict is reported either way.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(normaliseEmail(email));
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Email addresses are compared case-insensitively, so they are stored in one canonical form
     * rather than relying on a database-specific case-insensitive column type.
     */
    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
