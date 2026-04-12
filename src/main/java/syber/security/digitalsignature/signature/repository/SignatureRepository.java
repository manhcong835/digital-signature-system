package syber.security.digitalsignature.signature.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syber.security.digitalsignature.signature.entity.Signature;

import java.util.List;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, Long> {

    @Query("SELECT s FROM Signature s " +
           "LEFT JOIN FETCH s.document " +
           "LEFT JOIN FETCH s.user " +
           "LEFT JOIN FETCH s.keyPair " +
           "WHERE s.document.id = :documentId " +
           "ORDER BY s.signedAt DESC")
    List<Signature> findByDocumentIdOrderBySignedAtDesc(@Param("documentId") Long documentId);

    @Query("SELECT s FROM Signature s " +
           "LEFT JOIN FETCH s.document " +
           "LEFT JOIN FETCH s.user " +
           "LEFT JOIN FETCH s.keyPair " +
           "WHERE s.user.id = :userId " +
           "ORDER BY s.signedAt DESC")
    List<Signature> findByUserIdOrderBySignedAtDesc(@Param("userId") Long userId);

    long countByUserId(Long userId);

    boolean existsByKeyPairId(Long keyPairId);
}
