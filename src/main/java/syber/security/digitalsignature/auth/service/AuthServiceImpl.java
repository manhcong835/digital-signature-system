package syber.security.digitalsignature.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syber.security.digitalsignature.auth.dto.AuthResponse;
import syber.security.digitalsignature.auth.dto.ForgotPasswordRequest;
import syber.security.digitalsignature.auth.dto.LoginRequest;
import syber.security.digitalsignature.auth.dto.RefreshTokenRequest;
import syber.security.digitalsignature.auth.dto.RegisterRequest;
import syber.security.digitalsignature.auth.dto.ResendOtpRequest;
import syber.security.digitalsignature.auth.dto.Verify2faRequest;
import syber.security.digitalsignature.auth.dto.VerifyEmailRequest;
import syber.security.digitalsignature.auth.jwt.JwtUtil;
import syber.security.digitalsignature.auth.otp.CodeType;
import syber.security.digitalsignature.auth.otp.OtpProperties;
import syber.security.digitalsignature.auth.otp.OtpService;
import syber.security.digitalsignature.email.EmailService;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.UnauthorizedException;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;
import syber.security.digitalsignature.user.service.UserService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TEMP_PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final OtpProperties otpProperties;
    private final EmailService emailService;

    // -------- Register --------

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Optional<User> existing = userService.findByEmail(request.getEmail());
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                throw new BadRequestException("Email is already registered");
            }
            // Re-registration of an unverified account: update credentials & profile
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setFullName(request.getFullName());
            user.setPhone(request.getPhone());
            user.setOrganization(request.getOrganization());
            userRepository.save(user);
            log.info("Pending registration refreshed for {}", user.getEmail());
        } else {
            user = userService.createUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getOrganization()
            );
            user.setIsEmailVerified(false);
            userRepository.save(user);
        }

        String code = otpService.issueCode(user.getEmail(), CodeType.REGISTRATION);
        emailService.sendRegistrationOtp(user.getEmail(), code, otpProperties.getRegistrationTtlMinutes());

        return AuthResponse.builder()
                .requiresEmailVerification(true)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại"));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new BadRequestException("Email đã được xác minh");
        }

        otpService.verifyAndConsume(user.getEmail(), CodeType.REGISTRATION, request.getCode());

        user.setIsEmailVerified(true);
        userRepository.save(user);

        return issueTokens(user);
    }

    // -------- Login + 2FA --------

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));

        boolean primaryMatch = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        boolean tempMatch = !primaryMatch && isTempPasswordValid(user, request.getPassword());

        if (!primaryMatch && !tempMatch) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng");
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            // Re-send verification code so the user can complete onboarding
            String code = otpService.issueCode(user.getEmail(), CodeType.REGISTRATION);
            emailService.sendRegistrationOtp(user.getEmail(), code, otpProperties.getRegistrationTtlMinutes());
            return AuthResponse.builder()
                    .requiresEmailVerification(true)
                    .user(AuthResponse.UserInfo.builder()
                            .email(user.getEmail())
                            .build())
                    .build();
        }

        if (tempMatch) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }

        OtpService.TwoFactorChallenge challenge = otpService.issue2faChallenge(user.getEmail());
        emailService.sendLogin2faCode(user.getEmail(), challenge.code(), otpProperties.getLogin2faTtlMinutes());

        return AuthResponse.builder()
                .requires2fa(true)
                .challengeToken(challenge.challengeToken())
                .user(AuthResponse.UserInfo.builder()
                        .email(user.getEmail())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse verify2fa(Verify2faRequest request) {
        String email = otpService.verify2faAndConsume(request.getChallengeToken(), request.getCode());
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return issueTokens(user);
    }

    // -------- Refresh / Forgot / Resend --------

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        try {
            String email = jwtUtil.extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtUtil.isTokenValid(refreshToken, userDetails)) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            return issueTokens(user);
        } catch (Exception e) {
            log.error("Refresh token error: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always succeed silently to avoid leaking account existence
        Optional<User> maybeUser = userService.findByEmail(request.getEmail());
        if (maybeUser.isEmpty()) {
            log.info("Forgot-password requested for non-existing email {}", request.getEmail());
            return;
        }
        User user = maybeUser.get();
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            log.info("Forgot-password requested for unverified email {}", user.getEmail());
            return;
        }

        String tempPassword = generateTempPassword();
        user.setTempPasswordHash(passwordEncoder.encode(tempPassword));
        user.setTempPasswordExpiresAt(LocalDateTime.now().plusMinutes(otpProperties.getTempPasswordTtlMinutes()));
        user.setMustChangePassword(true);
        userRepository.save(user);

        emailService.sendTemporaryPassword(user.getEmail(), tempPassword, otpProperties.getTempPasswordTtlMinutes());
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        if (request.getType() == CodeType.LOGIN_2FA) {
            throw new BadRequestException("Vui lòng đăng nhập lại để nhận mã 2FA mới");
        }
        Optional<User> maybeUser = userService.findByEmail(request.getEmail());
        if (maybeUser.isEmpty()) {
            return; // silent
        }
        User user = maybeUser.get();
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return;
        }
        String code = otpService.issueCode(user.getEmail(), CodeType.REGISTRATION);
        emailService.sendRegistrationOtp(user.getEmail(), code, otpProperties.getRegistrationTtlMinutes());
    }

    // -------- Helpers --------

    private boolean isTempPasswordValid(User user, String rawPassword) {
        if (user.getTempPasswordHash() == null || user.getTempPasswordExpiresAt() == null) {
            return false;
        }
        if (user.getTempPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getTempPasswordHash());
    }

    private AuthResponse issueTokens(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        String token = jwtUtil.generateToken(userDetails, user.getId(), mustChange);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getId());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getExpirationTime())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .organization(user.getOrganization())
                        .mustChangePassword(mustChange ? true : null)
                        .build())
                .build();
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARSET.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARSET.length())));
        }
        return sb.toString();
    }
}
