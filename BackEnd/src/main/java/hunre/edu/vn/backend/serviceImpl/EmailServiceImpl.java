package hunre.edu.vn.backend.serviceImpl;

import hunre.edu.vn.backend.entity.Order;
import hunre.edu.vn.backend.entity.User;
import hunre.edu.vn.backend.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * Gửi email xác nhận đơn hàng
     * @param user Thông tin người dùng
     * @param order Đơn hàng
     * @throws MessagingException Lỗi gửi email
     */
    @Override
    public void sendOrderConfirmationEmail(User user, Order order) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // Thiết lập thông tin email
        helper.setFrom(senderEmail);
        helper.setTo(user.getEmail());
        helper.setSubject("Xác nhận đơn hàng #" + order.getOrderCode());

        // Chuẩn bị Context cho Thymeleaf template
        Context context = new Context();
        context.setVariable("name", user.getFullName());
        context.setVariable("orderCode", order.getOrderCode());
        context.setVariable("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        context.setVariable("totalPrice", formatCurrency(order.getTotalPrice()));
        context.setVariable("paymentMethod", getPaymentMethodName(order.getPaymentMethod().toString()));
        context.setVariable("status", getOrderStatusName(order.getStatus().toString()));
        context.setVariable("orderDetails", order.getOrderDetails());
        context.setVariable("discountAmount", order.getDiscountAmount() != null ? formatCurrency(order.getDiscountAmount()) : "0 VNĐ");
        context.setVariable("note", order.getNote());

        // Biến đổi template thành nội dung HTML
        String emailContent = templateEngine.process("order-confirmation-template", context);
        helper.setText(emailContent, true);

        // Gửi email
        mailSender.send(message);
    }

    // Hàm định dạng tiền tệ VND
    private String formatCurrency(BigDecimal amount) {
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return currencyFormatter.format(amount);
    }

    private String getPaymentMethodName(String method) {
        switch (method) {
            case "PAYPAL":
                return "PayPal";
            case "CASH":
                return "Tiền mặt";
            case "BALANCEACCOUNT":
                return "Ví Nahinn Pharmacity";
            default:
                return method;
        }
    }

    // Chuyển đổi mã trạng thái đơn hàng thành tên hiển thị
    private String getOrderStatusName(String status) {
        switch (status) {
            case "PENDING":
                return "Đang xử lý";
            case "CONFIRMED":
                return "Đã xác nhận";
            case "SHIPPING":
                return "Đang giao hàng";
            case "COMPLETED":
                return "Đã hoàn thành";
            case "CANCELLED":
                return "Đã hủy";
            default:
                return status;
        }
    }
}