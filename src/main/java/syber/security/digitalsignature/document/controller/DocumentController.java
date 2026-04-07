package syber.security.digitalsignature.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.common.dto.ApiResponse;
import syber.security.digitalsignature.common.util.SecurityUtil;
import syber.security.digitalsignature.document.dto.DocumentResponse;
import syber.security.digitalsignature.document.entity.Document;
import syber.security.digitalsignature.document.service.DocumentService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload and management APIs")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam("file") MultipartFile file) {
        String email = SecurityUtil.getCurrentUserEmail();
        Document saved = documentService.upload(email, file);
        return ResponseEntity.ok(
                ApiResponse.success(DocumentResponse.from(saved), "Document uploaded successfully"));
    }

    @GetMapping
    @Operation(summary = "List documents owned by the current user")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list() {
        String email = SecurityUtil.getCurrentUserEmail();
        List<DocumentResponse> documents = documentService.list(email).stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details")
    public ResponseEntity<ApiResponse<DocumentResponse>> get(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        Document document = documentService.get(email, id);
        return ResponseEntity.ok(ApiResponse.success(DocumentResponse.from(document)));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the original document")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        Document document = documentService.get(email, id);
        Resource resource = documentService.loadFile(email, id);

        String contentType = document.getMimeType() != null
                ? document.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String encoded = URLEncoder.encode(document.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getOriginalName() + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        documentService.delete(email, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted successfully"));
    }
}
