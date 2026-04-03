package syber.security.digitalsignature.key.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syber.security.digitalsignature.common.dto.ApiResponse;
import syber.security.digitalsignature.common.util.SecurityUtil;
import syber.security.digitalsignature.key.dto.CreateKeyRequest;
import syber.security.digitalsignature.key.dto.KeyResponse;
import syber.security.digitalsignature.key.entity.KeyPair;
import syber.security.digitalsignature.key.service.KeyService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
@Tag(name = "Keys", description = "RSA key pair management APIs")
@SecurityRequirement(name = "bearerAuth")
public class KeyController {

    private final KeyService keyService;

    @PostMapping
    @Operation(summary = "Generate a new RSA key pair")
    public ResponseEntity<ApiResponse<KeyResponse>> createKey(@Valid @RequestBody CreateKeyRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.createKey(email, request);
        return ResponseEntity.ok(
                ApiResponse.success(KeyResponse.from(key), "Key pair generated successfully"));
    }

    @GetMapping
    @Operation(summary = "List all key pairs for current user")
    public ResponseEntity<ApiResponse<List<KeyResponse>>> listKeys() {
        String email = SecurityUtil.getCurrentUserEmail();
        List<KeyResponse> keys = keyService.listKeys(email).stream()
                .map(KeyResponse::summary)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get key pair details (includes public key)")
    public ResponseEntity<ApiResponse<KeyResponse>> getKey(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.getKey(email, id);
        return ResponseEntity.ok(ApiResponse.success(KeyResponse.from(key)));
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Set a key as the default for signing")
    public ResponseEntity<ApiResponse<KeyResponse>> setDefault(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.setDefault(email, id);
        return ResponseEntity.ok(
                ApiResponse.success(KeyResponse.from(key), "Default key updated"));
    }

    @PutMapping("/{id}/revoke")
    @Operation(summary = "Revoke a key pair")
    public ResponseEntity<ApiResponse<KeyResponse>> revokeKey(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.revokeKey(email, id);
        return ResponseEntity.ok(
                ApiResponse.success(KeyResponse.from(key), "Key revoked successfully"));
    }

    @GetMapping("/{id}/public-key")
    @Operation(summary = "Download public key as PEM file")
    public ResponseEntity<Resource> downloadPublicKey(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.getKey(email, id);

        String pem = formatAsPem(key.getPublicKey());
        ByteArrayResource resource = new ByteArrayResource(pem.getBytes());
        String filename = sanitizeFilename(key.getName()) + "_public.pem";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @GetMapping("/{id}/certificate")
    @Operation(summary = "Download X.509 certificate (PEM) for this key")
    public ResponseEntity<Resource> downloadCertificate(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        KeyPair key = keyService.getKey(email, id);
        if (key.getCertificate() == null) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(key.getCertificate().getBytes());
        String filename = sanitizeFilename(key.getName()) + ".pem";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a key pair")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable Long id) {
        String email = SecurityUtil.getCurrentUserEmail();
        keyService.deleteKey(email, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Key deleted successfully"));
    }

    private String formatAsPem(String base64) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        int length = base64.length();
        for (int i = 0; i < length; i += 64) {
            sb.append(base64, i, Math.min(i + 64, length)).append('\n');
        }
        sb.append("-----END PUBLIC KEY-----\n");
        return sb.toString();
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
