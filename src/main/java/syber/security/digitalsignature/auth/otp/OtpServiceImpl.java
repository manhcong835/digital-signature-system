package syber.security.digitalsignature.auth.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import syber.security.digitalsignature.exception.BadRequestException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final EmailVerificationCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OtpProperties props;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public String issueCode(String email, CodeType type) {
        enforceCooldown(email, type);
        String code = generateNumericCode(props.getCodeLength());
        int ttlMinutes = ttlFor(type);

        EmailVerificationCode entity = EmailVerificationCode.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .attempts(0)
                .build();
        repository.save(entity);
        return code;
    }

    @Override
    @Transactional
    public TwoFactorChallenge issue2faChallenge(String email) {
        enforceCooldown(email, CodeType.LOGIN_2FA);
        String code = generateNumericCode(props.getCodeLength());
        String challengeToken = UUID.randomUUID().toString().replace("-", "");

        EmailVerificationCode entity = EmailVerificationCode.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .type(CodeType.LOGIN_2FA)
                .challengeToken(challengeToken)
                .expiresAt(LocalDateTime.now().plusMinutes(props.getLogin2faTtlMinutes()))
                .attempts(0)
                .build();
        repository.save(entity);
        return new TwoFactorChallenge(code, challengeToken);
    }

    @Override
    @Transactional
    public void verifyAndConsume(String email, CodeType type, String code) {
        EmailVerificationCode entity = repository
                .findActiveByEmailAndType(email, type, LocalDateTime.now(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn"));

        verifyCode(entity, code);
    }

    @Override
    @Transactional
    public String verify2faAndConsume(String challengeToken, String code) {
        EmailVerificationCode entity = repository
                .findByChallengeToken(challengeToken)
                .orElseThrow(() -> new BadRequestException("Phiên xác thực không hợp lệ"));

        if (entity.getConsumedAt() != null || entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Phiên xác thực đã hết hạn, vui lòng đăng nhập lại");
        }
        verifyCode(entity, code);
        return entity.getEmail();
    }

    private void verifyCode(EmailVerificationCode entity, String code) {
        if (entity.getAttempts() >= props.getMaxAttempts()) {
            throw new BadRequestException("Đã vượt quá số lần thử cho phép, vui lòng yêu cầu mã mới");
        }
        entity.setAttempts(entity.getAttempts() + 1);

        if (!passwordEncoder.matches(code, entity.getCodeHash())) {
            repository.save(entity);
            throw new BadRequestException("Mã xác minh không đúng");
        }
        entity.setConsumedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private void enforceCooldown(String email, CodeType type) {
        repository.findFirstByEmailAndTypeOrderByCreatedAtDesc(email, type).ifPresent(latest -> {
            Duration sinceLast = Duration.between(latest.getCreatedAt(), LocalDateTime.now());
            if (sinceLast.getSeconds() < props.getResendCooldownSeconds()) {
                long remaining = props.getResendCooldownSeconds() - sinceLast.getSeconds();
                throw new BadRequestException("Vui lòng đợi " + remaining + " giây trước khi gửi mã mới");
            }
        });
    }

    private int ttlFor(CodeType type) {
        return switch (type) {
            case REGISTRATION -> props.getRegistrationTtlMinutes();
            case LOGIN_2FA -> props.getLogin2faTtlMinutes();
        };
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
