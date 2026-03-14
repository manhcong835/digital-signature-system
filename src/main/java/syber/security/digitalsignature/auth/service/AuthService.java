package syber.security.digitalsignature.auth.service;

import syber.security.digitalsignature.auth.dto.AuthResponse;
import syber.security.digitalsignature.auth.dto.ForgotPasswordRequest;
import syber.security.digitalsignature.auth.dto.LoginRequest;
import syber.security.digitalsignature.auth.dto.RefreshTokenRequest;
import syber.security.digitalsignature.auth.dto.RegisterRequest;
import syber.security.digitalsignature.auth.dto.ResendOtpRequest;
import syber.security.digitalsignature.auth.dto.Verify2faRequest;
import syber.security.digitalsignature.auth.dto.VerifyEmailRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse verifyEmail(VerifyEmailRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse verify2fa(Verify2faRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resendOtp(ResendOtpRequest request);
}
