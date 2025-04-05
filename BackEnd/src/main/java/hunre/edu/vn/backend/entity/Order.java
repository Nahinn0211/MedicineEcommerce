package hunre.edu.vn.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_code", columnList = "order_code", unique = true),
        @Index(name = "idx_order_patient", columnList = "patient_id"),
        @Index(name = "idx_order_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {
    @NotBlank(message = "Mã đơn hàng không được để trống")
    @Pattern(regexp = "^ORD-\\d{6}$", message = "Mã đơn hàng phải có định dạng ORD-XXXXXX")
    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @NotNull(message = "Bệnh nhân không được trống")
    @ManyToOne(fetch = FetchType.EAGER)
    @ToString.Exclude
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @NotNull(message = "Tổng tiền không được trống")
    @DecimalMin(value = "0.0", message = "Tổng tiền phải lớn hơn hoặc bằng 0")
    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "sub_total", nullable = false)
    @DecimalMin(value = "0.0", message = "Tổng tiền hàng phải lớn hơn hoặc bằng 0")
    private BigDecimal subTotal;

    @NotNull(message = "Phương thức thanh toán không được trống")
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @NotNull(message = "Trạng thái đơn hàng không được trống")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "voucher_code", nullable = true)
    private String voucherCode;

    @Column(name = "discount_amount", nullable = true)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "note", nullable = true, columnDefinition = "nvarchar(MAX)")
    private String note;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "nvarchar(MAX)")
    private String shippingAddress;

    @Column(name = "shipping_phone", nullable = false)
    @Pattern(regexp = "^[0-9]{10,12}$", message = "Số điện thoại phải từ 10-12 chữ số")
    private String shippingPhone;

    @Column(name = "shipping_name", nullable = false, columnDefinition = "nvarchar(255)")
    private String shippingName;

    @Column(name = "shipping_fee", nullable = false)
    @DecimalMin(value = "0.0", message = "Phí vận chuyển phải lớn hơn hoặc bằng 0")
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_reason", columnDefinition = "nvarchar(MAX)")
    private String cancelledReason;

    @Formula("(SELECT COUNT(od.id) FROM order_details od WHERE od.order_id = id)")
    private Integer itemCount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderDetail> orderDetails = new ArrayList<>();

    public void addOrderDetail(OrderDetail orderDetail) {
        this.orderDetails.add(orderDetail);
        orderDetail.setOrder(this);

        this.calculateSubTotal();
        this.calculateTotalPrice();
    }

    public void removeOrderDetail(OrderDetail orderDetail) {
        this.orderDetails.remove(orderDetail);
        orderDetail.setOrder(null);

        this.calculateSubTotal();
        this.calculateTotalPrice();
    }

    public void calculateSubTotal() {
        this.subTotal = this.orderDetails.stream()
                .map(detail -> detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void calculateTotalPrice() {
        this.totalPrice = this.subTotal
                .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO)
                .add(this.shippingFee != null ? this.shippingFee : BigDecimal.ZERO);
    }

    public void applyVoucher(Voucher voucher) {
        if (voucher != null) {
            this.voucherCode = voucher.getCode();
            this.discountAmount = this.subTotal.multiply(
                    BigDecimal.valueOf(voucher.getVoucherPercentage() / 100.0));
            this.calculateTotalPrice();
        }
    }

    public void complete() {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.paymentStatus = PaymentStatus.COMPLETED;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledReason = reason;

        if (this.paymentStatus == PaymentStatus.COMPLETED) {
            this.patient.updateAccountBalance(this.totalPrice);
        }
    }

    public void process() {
        this.status = OrderStatus.PROCESSING;
    }

    public void updatePaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
