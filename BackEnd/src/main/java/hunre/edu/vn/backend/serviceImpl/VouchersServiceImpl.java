    package hunre.edu.vn.backend.serviceImpl;

    import hunre.edu.vn.backend.dto.VoucherDTO;
    import hunre.edu.vn.backend.entity.Voucher;
    import hunre.edu.vn.backend.mapper.VouchersMapper;
    import hunre.edu.vn.backend.repository.VoucherRepository;
    import hunre.edu.vn.backend.service.VouchersService;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.math.BigDecimal;
    import java.time.LocalDateTime;
    import java.util.List;
    import java.util.Optional;
    import java.util.stream.Collectors;

    @Service
    public class VouchersServiceImpl implements VouchersService {
        private final VouchersMapper vouchersMapper;
        private final VoucherRepository voucherRepository;

        public VouchersServiceImpl(VoucherRepository voucherRepository, VouchersMapper vouchersMapper) {
            this.voucherRepository = voucherRepository;
            this.vouchersMapper = vouchersMapper;
        }

        @Override
        public List<VoucherDTO.GetVoucherDTO> findAll() {
            return voucherRepository.findAllActive()
                    .stream()
                    .map(vouchersMapper::toGetDTO)
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<VoucherDTO.GetVoucherDTO> findById(Long id) {
            return voucherRepository.findActiveById(id).map(vouchersMapper::toGetDTO);
        }

        @Override
        @Transactional
        public VoucherDTO.GetVoucherDTO saveOrUpdate(VoucherDTO.SaveVoucherDTO voucherDTO) {
            Voucher voucher;

            if (voucherDTO.getId() == null || voucherDTO.getId() == 0) {
                // INSERT case
                voucher = new Voucher();
                voucher.setCode(voucherDTO.getCode());
                voucher.setVoucherPercentage(voucherDTO.getVoucherPercentage());
                voucher.setStock(voucherDTO.getStock());

                // Đặt startDate mặc định nếu không có
                if (voucherDTO.getStartDate() == null) {
                    voucher.setStartDate(LocalDateTime.now());
                } else {
                    voucher.setStartDate(voucherDTO.getStartDate());
                }

                voucher.setEndDate(voucherDTO.getEndDate());

                // Đặt giá trị mặc định cho minimumOrderValue nếu null
                if (voucherDTO.getMinimumOrderValue() == null) {
                    voucher.setMinimumOrderValue(BigDecimal.ZERO);
                } else {
                    voucher.setMinimumOrderValue(voucherDTO.getMinimumOrderValue());
                }

                // Đặt giá trị mặc định cho status nếu null
                if (voucherDTO.getStatus() == null) {
                    voucher.setStatus(Voucher.VoucherStatus.ACTIVE);
                } else {
                    voucher.setStatus(voucherDTO.getStatus());
                }

                voucher.setCreatedAt(LocalDateTime.now());
                voucher.setUpdatedAt(LocalDateTime.now());
            } else {
                // UPDATE case
                Optional<Voucher> existingVoucher = voucherRepository.findActiveById(voucherDTO.getId());
                if (existingVoucher.isEmpty()) {
                    throw new RuntimeException("Voucher not found with ID: " + voucherDTO.getId());
                }

                voucher = existingVoucher.get();
                voucher.setCode(voucherDTO.getCode());
                voucher.setVoucherPercentage(voucherDTO.getVoucherPercentage());
                voucher.setStock(voucherDTO.getStock());

                // Cập nhật startDate nếu có
                if (voucherDTO.getStartDate() != null) {
                    voucher.setStartDate(voucherDTO.getStartDate());
                }

                voucher.setEndDate(voucherDTO.getEndDate());

                // Cập nhật minimumOrderValue nếu có
                if (voucherDTO.getMinimumOrderValue() != null) {
                    voucher.setMinimumOrderValue(voucherDTO.getMinimumOrderValue());
                }

                // Cập nhật status nếu có
                if (voucherDTO.getStatus() != null) {
                    voucher.setStatus(voucherDTO.getStatus());
                }

                voucher.setUpdatedAt(LocalDateTime.now());
            }

            Voucher savedVoucher = voucherRepository.save(voucher);
            return vouchersMapper.toGetDTO(savedVoucher);
        }

        @Override
        public String deleteByList(List<Long> ids) {
            int count = 0;
            for (Long id : ids) {
                if (voucherRepository.existsById(id)){
                    voucherRepository.softDelete(id);
                    count++;
                }
            }

            return "Đã xóa " + count + " mã giảm giá";
        }

        @Override
        public Optional<VoucherDTO.GetVoucherDTO> findByCode(String code) {
            return voucherRepository.findByCodeAndIsDeletedFalse(code).map(vouchersMapper::toGetDTO);
        }
    }