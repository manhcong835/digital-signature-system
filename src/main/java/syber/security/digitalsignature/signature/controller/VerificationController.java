package syber.security.digitalsignature.signature.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import syber.security.digitalsignature.common.dto.ApiResponse;
import syber.security.digitalsignature.common.util.SecurityUtil;
import syber.security.digitalsignature.signature.dto.PadesVerifyResponse;
import syber.security.digitalsignature.signature.dto.VerifyRequest;
import syber.security.digitalsignature.signature.dto.VerifyResponse;
import syber.security.digitalsignature.signature.dto.VerificationHistoryResponse;
import syber.security.digitalsignature.signature.service.VerificationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Verification", description = "Signature verification APIs")
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/documents/{id}/verify")
    @Operation(summary = "Verify all signatures on a document, or a specific one if signatureId is provided")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<VerifyResponse>> verifyDocument(
            @PathVariable("id") Long documentId,
            @RequestBody(required = false) VerifyRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        Long signatureId = (request != null) ? request.getSignatureId() : null;
        VerifyResponse result = verificationService.verifyDocument(email, documentId, signatureId);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @GetMapping("/documents/{id}/verifications")
    @Operation(summary = "Get signature verification history for a document")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<VerificationHistoryResponse>>> getVerificationHistory(
            @PathVariable("id") Long documentId) {
        String email = SecurityUtil.getCurrentUserEmail();
        List<VerificationHistoryResponse> history = verificationService.getVerificationHistory(email, documentId)
                .stream()
                .map(VerificationHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping(value = "/verify/upload", consumes = "multipart/form-data")
    @Operation(summary = "Public: verify an uploaded file against a supplied public key and signature")
    public ResponseEntity<ApiResponse<VerifyResponse>> verifyUpload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("publicKey") String publicKey,
            @RequestPart("signature") String signature,
            @RequestPart(value = "algorithm", required = false) String algorithm) {
        VerifyResponse result = verificationService.verifyExternal(file, publicKey, signature, algorithm);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }

    @PostMapping(value = "/verify/pades-pdf", consumes = "multipart/form-data")
    @Operation(summary = "Public: verify all signatures embedded in a PDF")
    public ResponseEntity<ApiResponse<PadesVerifyResponse>> verifyPadesPdf(
            @RequestPart("file") MultipartFile file) {
        PadesVerifyResponse result = verificationService.verifyPadesPdf(file);
        String message = result.isAllSignaturesValid()
                ? "All signatures valid"
                : (result.getSignatureCount() == 0 ? "No signatures found" : "One or more signatures failed");
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }
}
