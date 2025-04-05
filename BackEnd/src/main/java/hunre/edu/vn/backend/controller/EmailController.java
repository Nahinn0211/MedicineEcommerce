package hunre.edu.vn.backend.controller;

import hunre.edu.vn.backend.dto.OrderDTO;
import hunre.edu.vn.backend.entity.User;
import hunre.edu.vn.backend.repository.UserRepository;
import hunre.edu.vn.backend.service.EmailService;
import hunre.edu.vn.backend.service.InvoiceService;
import hunre.edu.vn.backend.service.OrderService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.MessagingException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private InvoiceService invoiceService;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * Gửi email xác nhận đơn hàng mới nhất của người dùng hiện tại
     * @return ResponseEntity chứa kết quả gửi email
     */
    @GetMapping("/send-latest-order")
    public ResponseEntity<String> sendLatestOrderEmail() {
        try {
            // Lấy thông tin người dùng đang đăng nhập
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();

            // Tìm user bằng email
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                return ResponseEntity.badRequest().body("Không tìm thấy thông tin người dùng");
            }

            User user = userOptional.get();

            // Lấy thông tin đơn hàng mới nhất
            Optional<OrderDTO.OrderFullDetailDto> latestOrderOptional =
                    orderService.findLatestOrderFullDetailByUserId(user.getId());

            if (latestOrderOptional.isEmpty()) {
                return ResponseEntity.badRequest().body("Không tìm thấy đơn hàng nào của bạn");
            }

            OrderDTO.OrderFullDetailDto orderDetail = latestOrderOptional.get();

            // Tạo PDF
            byte[] invoicePdf = invoiceService.generateInvoicePdf(orderDetail.getId());

            // Tạo Context cho email
            Context context = new Context();
            context.setVariable("name", orderDetail.getPatientName());
            context.setVariable("orderCode", orderDetail.getOrderCode());
            context.setVariable("orderDate", orderDetail.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            context.setVariable("totalPrice", formatCurrency(orderDetail.getTotalPrice()));
            context.setVariable("paymentMethod", getPaymentMethodName(orderDetail.getPaymentMethod().toString()));
            context.setVariable("status", getOrderStatusName(orderDetail.getStatus().toString()));
            context.setVariable("orderDetails", orderDetail.getOrderDetails());
            context.setVariable("discountAmount",
                    orderDetail.getDiscountAmount() != null ?
                            formatCurrency(orderDetail.getDiscountAmount()) :
                            "0 VNĐ"
            );
            context.setVariable("note", orderDetail.getNote());

            // Tạo MimeMessage
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mailMessage, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(orderDetail.getPatientEmail());
            helper.setSubject("Xác nhận đơn hàng #" + orderDetail.getOrderCode());

            // Đính kèm PDF
            helper.addAttachment(
                    "HoaDon_" + orderDetail.getOrderCode() + ".pdf",
                    new ByteArrayResource(invoicePdf)
            );

            // Biến đổi template thành nội dung HTML
            String emailContent = templateEngine.process("order-confirmation-template", context);
            helper.setText(emailContent, true);

            // Gửi email
            mailSender.send(mailMessage);

            return ResponseEntity.ok("Email xác nhận đơn hàng đã được gửi thành công");
        } catch (MessagingException e) {
            return ResponseEntity.internalServerError().body("Lỗi khi gửi email: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Có lỗi xảy ra: " + e.getMessage());
        }
    }

    /**
     * Gửi email xác nhận đơn hàng cho một đơn hàng cụ thể
     * @param orderId ID của đơn hàng cần gửi email
     * @return ResponseEntity chứa kết quả gửi email
     */
    @GetMapping("/send-order/{orderId}")
    public ResponseEntity<String> sendOrderEmail(@PathVariable Long orderId) {
        try {
            // Lấy thông tin đơn hàng
            Optional<OrderDTO.OrderFullDetailDto> orderOptional =
                    orderService.findLatestOrderFullDetailByUserId(orderId);

            if (orderOptional.isEmpty()) {
                return ResponseEntity.badRequest().body("Không tìm thấy đơn hàng");
            }

            OrderDTO.OrderFullDetailDto orderDetail = orderOptional.get();

            // Tạo PDF
            byte[] invoicePdf = invoiceService.generateInvoicePdf(orderDetail.getId());

            // Tạo Context cho email
            Context context = new Context();
            context.setVariable("name", orderDetail.getPatientName());
            context.setVariable("orderCode", orderDetail.getOrderCode());
            context.setVariable("orderDate", orderDetail.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            context.setVariable("totalPrice", formatCurrency(orderDetail.getTotalPrice()));
            context.setVariable("paymentMethod", getPaymentMethodName(orderDetail.getPaymentMethod().toString()));
            context.setVariable("status", getOrderStatusName(orderDetail.getStatus().toString()));
            context.setVariable("orderDetails", orderDetail.getOrderDetails());
            context.setVariable("discountAmount",
                    orderDetail.getDiscountAmount() != null ?
                            formatCurrency(orderDetail.getDiscountAmount()) :
                            "0 VNĐ"
            );
            context.setVariable("note", orderDetail.getNote());

            // Tạo MimeMessage
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mailMessage, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(orderDetail.getPatientEmail());
            helper.setSubject("Xác nhận đơn hàng #" + orderDetail.getOrderCode());

            // Đính kèm PDF
            helper.addAttachment(
                    "HoaDon_" + orderDetail.getOrderCode() + ".pdf",
                    new ByteArrayResource(invoicePdf)
            );

            // Biến đổi template thành nội dung HTML
            String emailContent = templateEngine.process("order-confirmation-template", context);
            helper.setText(emailContent, true);

            // Gửi email
            mailSender.send(mailMessage);

            return ResponseEntity.ok("Email xác nhận đơn hàng đã được gửi thành công");
        } catch (MessagingException e) {
            return ResponseEntity.internalServerError().body("Lỗi khi gửi email: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Có lỗi xảy ra: " + e.getMessage());
        }
    }

    // Các phương thức hỗ trợ định dạng
    private String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(amount);
    }

    private String getPaymentMethodName(String method) {
        switch (method) {
            case "PAYPAL": return "PayPal";
            case "CREDIT_CARD": return "Thẻ tín dụng";
            case "BANK_TRANSFER": return "Chuyển khoản ngân hàng";
            case "CASH": return "Tiền mặt";
            case "MOMO": return "Ví MoMo";
            case "VNPAY": return "VNPay";
            default: return method;
        }
    }

    private String getOrderStatusName(String status) {
        switch (status) {
            case "PENDING": return "Đang xử lý";
            case "CONFIRMED": return "Đã xác nhận";
            case "SHIPPING": return "Đang giao hàng";
            case "COMPLETED": return "Đã hoàn thành";
            case "CANCELLED": return "Đã hủy";
            default: return status;
        }
    }
}