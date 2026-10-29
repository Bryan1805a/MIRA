package com.mira.mira_backend.service;

import com.mira.mira_backend.entity.UserAccount;
import com.mira.mira_backend.repository.UserAccountRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAccountService {

    private static final String EMAIL_UNIQUE_INDEX =
            "uq_user_account_email_normalized";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public UserAccountService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        Validator validator
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    // Register service
    @Transactional
    public UserAccount register(String email, String rawPassword) {
        
        // Check valid and then normalise email
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        RegistrationInput input = new RegistrationInput(normalizedEmail, rawPassword);

        Set<ConstraintViolation<RegistrationInput>> violations = validator.validate(input);

        // Throw exeption if violation occurs
        if (!violations.isEmpty()) {
            throw new InvalidRegistrationException(
                    "Email or password does not meet registration requirements."
            );
        }

        // Hashing password
        String passwordHash = passwordEncoder.encode(rawPassword);

        // Create new account
        UserAccount account = new UserAccount(normalizedEmail, passwordHash);

        try {
            // Flush now so database uniqueness violations happen here.
            return userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new DuplicateEmailException();
            }

            // Do not misreport unrelated database failures as duplicates.
            throw exception;
        }
    }

    // Find user by ID
    @Transactional(readOnly = true)
    public UserAccount getById(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private boolean isEmailUniqueViolation(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_INDEX.equals(
                        violation.getConstraintName()
                );
            }

            current = current.getCause();
        }

        return false;
    }

    private record RegistrationInput(
            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotBlank
            @Size(min = 12, max = 128)
            String password
    ) {
    }
}
