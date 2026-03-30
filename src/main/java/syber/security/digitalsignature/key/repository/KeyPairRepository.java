package syber.security.digitalsignature.key.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import syber.security.digitalsignature.key.entity.KeyPair;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeyPairRepository extends JpaRepository<KeyPair, Long> {

    List<KeyPair> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KeyPair> findByUserIdAndIsDefaultTrue(Long userId);

    Optional<KeyPair> findByIdAndUserId(Long id, Long userId);
}
