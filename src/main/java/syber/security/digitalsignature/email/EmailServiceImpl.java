package syber.security.digitalsignature.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailProperties props;

    @Override
    @Async
    public void sendRegistrationOtp(String to, String code, int ttlMinutes) {
        String subject = "[" + props.getAppName() + "] Mã xác minh tài khoản";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #f8fafc;">
                  <div style="background: white; border-radius: 8px; padding: 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);">
                    <h2 style="color: #0f172a; margin-top: 0;">Xác minh email của bạn</h2>
                    <p style="color: #475569; line-height: 1.6;">
                      Cảm ơn bạn đã đăng ký <strong>%s</strong>. Vui lòng nhập mã sau để hoàn tất:
                    </p>
                    <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #0ea5e9; text-align: center; padding: 24px; background: #f0f9ff; border-radius: 6px; margin: 24px 0;">
                      %s
                    </div>
                    <p style="color: #64748b; font-size: 14px;">
                      Mã có hiệu lực trong <strong>%d phút</strong>. Nếu bạn không yêu cầu, hãy bỏ qua email này.
                    </p>
                  </div>
                </div>
                """.formatted(props.getAppName(), code, ttlMinutes);

        send(to, subject, body);
    }

    @Override
    @Async
    public void sendLogin2faCode(String to, String code, int ttlMinutes) {
        String subject = "[" + props.getAppName() + "] Mã đăng nhập";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #f8fafc;">
                  <div style="background: white; border-radius: 8px; padding: 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);">
                    <h2 style="color: #0f172a; margin-top: 0;">Mã xác thực đăng nhập</h2>
                    <p style="color: #475569; line-height: 1.6;">
                      Có yêu cầu đăng nhập vào tài khoản của bạn. Nhập mã sau để tiếp tục:
                    </p>
                    <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #10b981; text-align: center; padding: 24px; background: #f0fdf4; border-radius: 6px; margin: 24px 0;">
                      %s
                    </div>
                    <p style="color: #64748b; font-size: 14px;">
                      Mã có hiệu lực trong <strong>%d phút</strong>.
                    </p>
                    <p style="color: #b91c1c; font-size: 14px; padding: 12px; background: #fef2f2; border-radius: 6px;">
                      Nếu bạn không thực hiện đăng nhập này, vui lòng đổi mật khẩu ngay.
                    </p>
                  </div>
                </div>
                """.formatted(code, ttlMinutes);

        send(to, subject, body);
    }

    @Override
    @Async
    public void sendTemporaryPassword(String to, String tempPassword, int ttlMinutes) {
        String subject = "[" + props.getAppName() + "] Mật khẩu tạm thời";
        String body = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #f8fafc;">
                  <div style="background: white; border-radius: 8px; padding: 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);">
                    <h2 style="color: #0f172a; margin-top: 0;">Đặt lại mật khẩu</h2>
                    <p style="color: #475569; line-height: 1.6;">
                      Mật khẩu tạm thời của bạn:
                    </p>
                    <div style="font-size: 22px; font-family: 'Courier New', monospace; font-weight: bold; color: #b45309; text-align: center; padding: 20px; background: #fffbeb; border: 1px dashed #f59e0b; border-radius: 6px; margin: 24px 0; word-break: break-all;">
                      %s
                    </div>
                    <p style="color: #64748b; font-size: 14px;">
                      Mật khẩu này có hiệu lực trong <strong>%d phút</strong>. Sau khi đăng nhập, bạn sẽ bị yêu cầu đổi sang mật khẩu mới trước khi sử dụng ứng dụng.
                    </p>
                    <p style="color: #b91c1c; font-size: 14px; padding: 12px; background: #fef2f2; border-radius: 6px;">
                      Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu cũ của bạn vẫn dùng được cho đến khi hết hạn mật khẩu tạm.
                    </p>
                  </div>
                </div>
                """.formatted(tempPassword, ttlMinutes);

        send(to, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        if (!props.isEnabled()) {
            log.warn("Mail is disabled (application.auth.mail.enabled=false). Would send to {}: {}", to, subject);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("JavaMailSender not configured — cannot send to {}: {}", to, subject);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
