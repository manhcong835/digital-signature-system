package syber.security.digitalsignature.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.user.dto.ChangePasswordRequest;
import syber.security.digitalsignature.user.dto.UpdateProfileRequest;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User createUser(String email, String password, String fullName, String phone, String organization) {
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .phone(phone)
                .organization(organization)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {}", email);
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setOrganization(request.getOrganization());

        User updated = userRepository.save(user);
        log.info("Profile updated for user: {}", email);
        return updated;
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        boolean currentMatches = passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash());
        boolean tempMatches = !currentMatches && isTempPasswordValid(user, request.getCurrentPassword());

        if (!currentMatches && !tempMatches) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTempPasswordHash(null);
        user.setTempPasswordExpiresAt(null);
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("Password changed for user: {}", email);
    }

    private boolean isTempPasswordValid(User user, String rawPassword) {
        if (user.getTempPasswordHash() == null || user.getTempPasswordExpiresAt() == null) {
            return false;
        }
        if (user.getTempPasswordExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getTempPasswordHash());
    }
}
