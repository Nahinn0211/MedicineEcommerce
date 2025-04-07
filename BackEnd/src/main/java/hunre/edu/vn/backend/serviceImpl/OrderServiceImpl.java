package hunre.edu.vn.backend.serviceImpl;

import hunre.edu.vn.backend.dto.OrderDTO;
import hunre.edu.vn.backend.dto.OrderDetailDTO;
import hunre.edu.vn.backend.entity.*;
import hunre.edu.vn.backend.mapper.OrderDetailMapper;
import hunre.edu.vn.backend.mapper.OrderMapper;
import hunre.edu.vn.backend.repository.MedicineRepository;
import hunre.edu.vn.backend.repository.OrderRepository;
import hunre.edu.vn.backend.repository.PatientProfileRepository;
import hunre.edu.vn.backend.repository.UserRepository;
import hunre.edu.vn.backend.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PatientProfileRepository patientRepository;
    private final OrderMapper orderMapper;
    private final UserRepository userRepository;
    private final OrderDetailMapper orderDetailMapper;
    private final MedicineRepository medicineRepository;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            PatientProfileRepository patientRepository,
            OrderMapper orderMapper, UserRepository userRepository, OrderDetailMapper orderDetailMapper, MedicineRepository medicineRepository) {
        this.orderRepository = orderRepository;
        this.patientRepository = patientRepository;
        this.orderMapper = orderMapper;
        this.userRepository = userRepository;
        this.orderDetailMapper = orderDetailMapper;
        this.medicineRepository = medicineRepository;
    }
    @Override
    public BigDecimal getTotalRevenue() {
        return orderRepository.calculateTotalRevenue();
    }

    @Override
    public Long getTotalOrders() {
        return orderRepository.countTotalOrders();
    }
    @Override
    public List<OrderDTO.GetOrderDTO> findAll() {
        return orderRepository.findAll()
                .stream()
                .map(order -> {
                    OrderDTO.GetOrderDTO orderDto = orderMapper.toGetOrderDTO(order);
                    Optional<User> userOptional = userRepository.findById(order.getPatient().getUser().getId());
                    if (userOptional.isPresent()) {
                        orderDto.setPatientName(userOptional.get().getFullName());
                    }

                    return orderDto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OrderDTO.GetOrderDTO> findById(Long id) {
        return orderRepository.findById(id)
                .map(order -> {
                    OrderDTO.GetOrderDTO orderDto = orderMapper.toGetOrderDTO(order);
                    List<OrderDetailDTO.GetOrderDetailDTO> orderDetailDtos = order.getOrderDetails().stream()
                            .map(orderDetailMapper::toGetOrderDetailDTO)
                            .collect(Collectors.toList());
                    orderDto.setOrderDetails(orderDetailDtos);

                    Optional<User> userOptional = userRepository.findActiveById(order.getPatient().getUser().getId());
                    if (userOptional.isPresent()) {
                        orderDto.setPatientName(userOptional.get().getFullName());
                    }

                    return orderDto;
                });
    }

    @Override
    public OrderDTO.GetOrderDTO saveOrUpdate(OrderDTO.SaveOrderDTO orderDTO) {
        Order order;

        if (orderDTO.getId() == null || orderDTO.getId() == 0) {
            // INSERT case
            order = new Order();
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // Tạo mã đơn hàng nếu chưa có
            if (orderDTO.getOrderCode() == null || orderDTO.getOrderCode().isEmpty()) {
                order.setOrderCode(generateOrderCode());
            } else {
                order.setOrderCode(orderDTO.getOrderCode());
            }
        } else {
            // UPDATE case
            Optional<Order> existingOrder = orderRepository.findActiveById(orderDTO.getId());
            if (existingOrder.isEmpty()) {
                throw new RuntimeException("Order not found with ID: " + orderDTO.getId());
            }
            order = existingOrder.get();
            order.setUpdatedAt(LocalDateTime.now());
        }

        // Xử lý patient relationship
        if (orderDTO.getPatientId() != null) {
            PatientProfile patient = patientRepository.findActiveById(orderDTO.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + orderDTO.getPatientId()));
            order.setPatient(patient);
        }

        // Cập nhật các trường khác
        order.setTotalPrice(orderDTO.getTotalPrice());
        order.setPaymentMethod(orderDTO.getPaymentMethod());
        order.setStatus(orderDTO.getStatus());
        order.setVoucherCode(orderDTO.getVoucherCode());
        order.setDiscountAmount(orderDTO.getDiscountAmount());
        order.setNote(orderDTO.getNote());

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toGetOrderDTO(savedOrder);
    }

    @Override
    public String deleteByList(List<Long> ids) {
        for (Long id : ids) {
            if (orderRepository.existsById(id)) {
                orderRepository.softDelete(id);
            }
        }

        return "Đã xóa " + ids.size() + " đơn hàng";
    }

    @Override
    public Optional<OrderDTO.GetOrderDTO> findByOrderCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .map(orderMapper::toGetOrderDTO);
    }

    @Override
    public List<OrderDTO.GetOrderDTO> findByPatientId(Long patientId) {
        return orderRepository.findByPatient_Id(patientId)
                .stream()
                .map(orderMapper::toGetOrderDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDTO.GetOrderDTO> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status)
                .stream()
                .map(orderMapper::toGetOrderDTO)
                .collect(Collectors.toList());
    }

    @Override
    public OrderDTO.GetOrderDTO updateStatus(OrderStatus status, Long id) {
        // Find the order, throwing a specific exception if not found
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + id));

        existingOrder.setStatus(status);
        System.out.println(existingOrder);

        Order savedOrder = orderRepository.save(existingOrder);

        return orderMapper.toGetOrderDTO(savedOrder);
    }

    // Hàm tiện ích để tạo mã đơn hàng
    private String generateOrderCode() {
        String prefix = "ORD";
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(5);
        return prefix + timestamp;
    }

    @Override
    public Optional<OrderDTO.OrderFullDetailDto> findLatestOrderFullDetailByUserId(Long userId) {
        // Tìm đơn hàng mới nhất của user
        Optional<Order> latestOrderOptional = orderRepository.findTopByPatient_User_IdOrderByCreatedAtDesc(userId);

        return latestOrderOptional.map(order -> {
            OrderDTO.OrderFullDetailDto fullDetailDto = new OrderDTO.OrderFullDetailDto();

            // Mapping thông tin đơn hàng
            fullDetailDto.setId(order.getId());
            fullDetailDto.setOrderCode(order.getOrderCode());
            fullDetailDto.setCreatedAt(order.getCreatedAt());
            fullDetailDto.setUpdatedAt(order.getUpdatedAt());
            fullDetailDto.setTotalPrice(order.getTotalPrice());
            fullDetailDto.setStatus(order.getStatus());
            fullDetailDto.setPaymentMethod(order.getPaymentMethod());
            fullDetailDto.setDiscountAmount(order.getDiscountAmount());
            fullDetailDto.setNote(order.getNote());
            fullDetailDto.setVoucherCode(order.getVoucherCode());

            // Thông tin người dùng
            User user = order.getPatient().getUser();
            fullDetailDto.setPatientName(user.getFullName());
            fullDetailDto.setPatientEmail(user.getEmail());

            // Chi tiết đơn hàng
            List<OrderDTO.OrderFullDetailDto.OrderDetailFullInfoDto> orderDetailDtos = order.getOrderDetails().stream()
                    .map(orderDetail -> {
                        OrderDTO.OrderFullDetailDto.OrderDetailFullInfoDto detailDto = new OrderDTO.OrderFullDetailDto.OrderDetailFullInfoDto();

                        // Thông tin chi tiết đơn hàng
                        detailDto.setId(orderDetail.getId());
                        detailDto.setQuantity(orderDetail.getQuantity());
                        detailDto.setUnitPrice(orderDetail.getUnitPrice());

                        if (orderDetail.getMedicine() != null) {
                            Medicine medicine = orderDetail.getMedicine();
                            orderDetail.setMedicine(medicine);
                        }
                        // Thông tin thuộc tính
                        if (orderDetail.getAttribute() != null) {
                            Attribute attribute = orderDetail.getAttribute();
                            orderDetail.setAttribute(attribute);
                        }

                        // Tính tổng tiền của dòng sản phẩm
                        detailDto.setLineTotal(orderDetail.getUnitPrice()
                                .multiply(BigDecimal.valueOf(orderDetail.getQuantity())));

                        return detailDto;
                    })
                    .collect(Collectors.toList());

            fullDetailDto.setOrderDetails(orderDetailDtos);

            return fullDetailDto;
        });
    }
}