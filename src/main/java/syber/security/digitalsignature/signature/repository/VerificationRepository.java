package syber.security.digitalsignature.signature.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import syber.security.digitalsignature.signature.entity.Verification;

import java.util.List;

@Repository
public interface VerificationRepository extends JpaRepository<Verification, Long> {

    @Query("SELECT v FROM Verification v " +
           "LEFT JOIN FETCH v.signature s " +
           "LEFT JOIN FETCH v.user u " +
           "LEFT JOIN FETCH s.user su " +
           "WHERE s.document.id = :documentId " +
           "ORDER BY v.verifiedAt DESC")
    List<Verification> findByDocumentIdOrderByVerifiedAtDesc(@Param("documentId") Long documentId);

    List<Verification> findBySignatureIdOrderByVerifiedAtDesc(Long signatureId);
}
