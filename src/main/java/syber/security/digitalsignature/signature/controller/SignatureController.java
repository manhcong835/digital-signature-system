package syber.security.digitalsignature.signature.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syber.security.digitalsignature.common.dto.ApiResponse;
import syber.security.digitalsignature.common.util.SecurityUtil;
import syber.security.digitalsignature.signature.dto.SignDocumentRequest;
import syber.security.digitalsignature.signature.dto.SignatureResponse;
import syber.security.digitalsignature.signature.entity.Signature;
import syber.security.digitalsignature.signature.service.SignatureService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Signatures", description = "Digital signing APIs")
@SecurityRequirement(name = "bearerAuth")
public class SignatureController {

    private final SignatureService signatureService;

    @PostMapping("/documents/{id}/sign")
    @Operation(summary = "Sign a document with the selected key")
    public ResponseEntity<ApiResponse<SignatureResponse>> sign(
            @PathVariable("id") Long documentId,
            @Valid @RequestBody SignDocumentRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        Signature signature = signatureService.signDocument(email, documentId, request);
        return ResponseEntity.ok(
                ApiResponse.success(SignatureResponse.from(signature), "Document signed successfully"));
    }

    @GetMapping("/documents/{id}/signatures")
    @Operation(summary = "List signatures for a document")
    public ResponseEntity<ApiResponse<List<SignatureResponse>>> listForDocument(
            @PathVariable("id") Long documentId) {
        String email = SecurityUtil.getCurrentUserEmail();
        List<SignatureResponse> signatures = signatureService.listByDocument(email, documentId).stream()
                .map(SignatureResponse::summary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(signatures));
    }

    @GetMapping("/signatures")
    @Operation(summary = "List all signatures created by the current user")
    public ResponseEntity<ApiResponse<List<SignatureResponse>>> listForUser() {
        String email = SecurityUtil.getCurrentUserEmail();
        List<SignatureResponse> signatures = signatureService.listByUser(email).stream()
                .map(SignatureResponse::summary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(signatures));
    }

    @GetMapping("/signatures/{id}/pades-pdf")
    @Operation(summary = "Download the signed PDF (visible in Adobe Reader)")
    public ResponseEntity<Resource> downloadPadesPdf(@PathVariable("id") Long signatureId) {
        String email = SecurityUtil.getCurrentUserEmail();
        SignatureService.PadesDownload download = signatureService.resolvePadesPdf(email, signatureId);
        FileSystemResource resource = new FileSystemResource(download.file());
        // RFC 6266: ContentDisposition handles UTF-8 filenames (Vietnamese, etc.)
        // by emitting both filename= (ASCII fallback) and filename*=UTF-8'' forms.
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(download.suggestedFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
