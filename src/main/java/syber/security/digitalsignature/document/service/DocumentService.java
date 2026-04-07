package syber.security.digitalsignature.document.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.document.entity.Document;

import java.util.List;

public interface DocumentService {

    Document upload(String email, MultipartFile file);

    List<Document> list(String email);

    Document get(String email, Long documentId);

    Resource loadFile(String email, Long documentId);

    void delete(String email, Long documentId);
}
