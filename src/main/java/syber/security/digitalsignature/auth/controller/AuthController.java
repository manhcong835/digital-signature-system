package syber.security.digitalsignature.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syber.security.digitalsignature.auth.dto.AuthResponse;
import syber.security.digitalsignature.auth.dto.ForgotPasswordRequest;
import syber.security.digitalsignature.auth.dto.LoginRequest;
import syber.security.digitalsignature.auth.dto.RefreshTokenRequest;
import syber.security.digitalsignature.auth.dto.RegisterRequest;
import syber.security.digitalsignature.auth.dto.ResendOtpRequest;
import syber.security.digitalsignature.auth.dto.Verify2faRequest;
import syber.security.digitalsignature.auth.dto.VerifyEmailRequest;
import syber.security.digitalsignature.auth.service.AuthService;
import syber.security.digitalsignature.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account; sends OTP to email for verification")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Verification code sent to email"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify registration OTP and obtain JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Email verified"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password; returns a 2FA challenge")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        String message = Boolean.TRUE.equals(response.getRequires2fa())
                ? "2FA code sent to email"
                : "Email verification required";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Verify login 2FA code and obtain JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> verify2fa(
            @Valid @RequestBody Verify2faRequest request) {
        AuthResponse response = authService.verify2fa(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a temporary password to be emailed")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Nếu email tồn tại, mật khẩu tạm đã được gửi"));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend a registration OTP")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification code resent"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current user")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }
}
