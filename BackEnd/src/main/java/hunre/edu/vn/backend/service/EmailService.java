package hunre.edu.vn.backend.service;

import hunre.edu.vn.backend.entity.Order;
import hunre.edu.vn.backend.entity.User;
import jakarta.mail.MessagingException;

public interface EmailService {
    /**
     * Gửi email thông báo đơn hàng thành công
     * @param user Thông tin người dùng
     * @param order Đơn hàng
     * @throws MessagingException Lỗi gửi email
     */
    void sendOrderConfirmationEmail(User user, Order order) throws MessagingException;
}