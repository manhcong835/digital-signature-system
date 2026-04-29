package syber.security.digitalsignature.auth.otp;

public interface OtpService {

    /**
     * Issue a new code for the given email and type.
     * @return the plaintext code to send by email
     */
    String issueCode(String email, CodeType type);

    /**
     * Issue a new 2FA code with a challenge token used as the handle on the client.
     */
    TwoFactorChallenge issue2faChallenge(String email);

    /**
     * Verify a code against the latest active record for (email, type) and consume it.
     * Throws BadRequestException on invalid/expired/over-attempts.
     */
    void verifyAndConsume(String email, CodeType type, String code);

    /**
     * Verify a 2FA code by challenge token. Returns the email of the matching challenge.
     */
    String verify2faAndConsume(String challengeToken, String code);

    record TwoFactorChallenge(String code, String challengeToken) {}
}
