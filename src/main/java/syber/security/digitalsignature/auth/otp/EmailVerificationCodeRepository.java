package syber.security.digitalsignature.auth.otp;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    // JPQL doesn't support LIMIT, so use Pageable to cap results at 1.
    // Without this, multiple active codes (which can legitimately exist if the
    // user re-requests before the previous expires) trigger NonUniqueResultException.
    @Query("""
            SELECT c FROM EmailVerificationCode c
            WHERE c.email = :email
              AND c.type = :type
              AND c.consumedAt IS NULL
              AND c.expiresAt > :now
            ORDER BY c.createdAt DESC
            """)
    List<EmailVerificationCode> findActiveByEmailAndType(
            @Param("email") String email,
            @Param("type") CodeType type,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // Derived method — Spring Data emits LIMIT 1 from "findFirst...".
    // Replaces a prior @Query("... ORDER BY createdAt DESC") returning Optional,
    // which crashed with NonUniqueResultException once any email had >1 historical OTP row.
    Optional<EmailVerificationCode> findFirstByEmailAndTypeOrderByCreatedAtDesc(
            String email, CodeType type);

    Optional<EmailVerificationCode> findByChallengeToken(String challengeToken);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
