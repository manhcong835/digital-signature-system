package syber.security.digitalsignature.email;

public interface EmailService {

    void sendRegistrationOtp(String to, String code, int ttlMinutes);

    void sendLogin2faCode(String to, String code, int ttlMinutes);

    void sendTemporaryPassword(String to, String tempPassword, int ttlMinutes);
}
