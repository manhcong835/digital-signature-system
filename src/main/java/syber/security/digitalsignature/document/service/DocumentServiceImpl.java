package syber.security.digitalsignature.document.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.common.util.FileUtil;
import syber.security.digitalsignature.config.FileStorageProperties;
import syber.security.digitalsignature.crypto.service.CryptoService;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.repository.DocumentRepository;
import syber.security.digitalsignature.exception.BadRequestException;
import syber.security.digitalsignature.exception.FileStorageException;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final FileStorageProperties storageProperties;

    private Path uploadRoot;

    @PostConstruct
    public void init() {
        try {
            uploadRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadRoot);
            log.info("Upload directory ready: {}", uploadRoot);
        } catch (IOException e) {
            throw new FileStorageException("Failed to create upload directory", e);
        }
    }

    @Override
    @Transactional
    public Document upload(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > storageProperties.getMaxFileSize()) {
            throw new BadRequestException("File exceeds the maximum allowed size");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BadRequestException("File name is missing");
        }
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BadRequestException("Invalid file name");
        }

        String ext = FileUtil.getExtension(originalName);
        if (!FileUtil.isAllowedExtension(ext)) {
            throw new BadRequestException("Unsupported file type. Allowed: pdf, docx, txt");
        }

        String mimeType = file.getContentType();
        if (mimeType != null && !FileUtil.isAllowedMimeType(mimeType)) {
            throw new BadRequestException("Unsupported MIME type: " + mimeType);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        String storedName = FileUtil.generateStoredFilename(originalName);
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid storage path");
        }

        byte[] content;
        try (var in = file.getInputStream()) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded file", e);
        }

        String hash = cryptoService.hashSha512(content);

        try {
            Files.copy(new java.io.ByteArrayInputStream(content), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file on disk", e);
        }

        Document document = Document.builder()
                .user(user)
                .originalName(originalName)
                .storedPath(storedName)
                .mimeType(mimeType)
                .size(file.getSize())
                .sha512Hash(hash)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document uploaded by {}: id={}, original={}, size={}", email, saved.getId(), originalName, file.getSize());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> list(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Document get(String email, Long documentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFile(String email, Long documentId) {
        Document document = get(email, documentId);
        Path filePath = uploadRoot.resolve(document.getStoredPath()).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid stored path");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File not found or unreadable: " + document.getOriginalName());
            }
            return resource;
        } catch (Exception e) {
            throw new FileStorageException("Failed to load file: " + document.getOriginalName(), e);
        }
    }

    @Override
    @Transactional
    public void delete(String email, Long documentId) {
        Document document = get(email, documentId);
        Path filePath = uploadRoot.resolve(document.getStoredPath()).normalize();
        if (filePath.startsWith(uploadRoot)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete file from disk: {}", filePath, e);
            }
        }
        documentRepository.delete(document);
        log.info("Document deleted by {}: id={}", email, documentId);
    }
}
