package hunre.edu.vn.backend.serviceImpl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import hunre.edu.vn.backend.dto.RoleDTO;
import hunre.edu.vn.backend.dto.SocialAccountDTO;
import hunre.edu.vn.backend.dto.UserDTO;
import hunre.edu.vn.backend.entity.*;
import hunre.edu.vn.backend.exception.ResourceNotFoundException;
import hunre.edu.vn.backend.mapper.UserMapper;
import hunre.edu.vn.backend.repository.*;
import hunre.edu.vn.backend.service.UserService;
import hunre.edu.vn.backend.utils.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;
    private final AuthenticationManager authenticationManager;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${facebook.app-id}")
    private String facebookAppId;

    @Value("${facebook.app-secret}")
    private String facebookAppSecret;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PatientProfileRepository patientProfileRepository;

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    public UserServiceImpl(UserMapper userMapper, UserRepository userRepository, PasswordEncoder passwordEncoder,
                           S3Service s3Service, AuthenticationManager authenticationManager) {
        this.userMapper = userMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.s3Service = s3Service;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public boolean isUserInRole(Long userId, String roleName) {
        return userRepository.findById(userId)
                .map(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals(roleName)))
                .orElse(false);
    }

    /**
     * Phương thức tiện ích để gán vai trò mặc định cho người dùng mới
     */
    private void assignDefaultRoleToUser(User user) {
        // Tìm vai trò USER (ưu tiên tìm theo tên)
        Optional<Role> roleOptional = roleRepository.findByName("USER");

        if (roleOptional.isEmpty()) {
            // Nếu không tìm thấy theo tên, thử tìm theo ID 3
            roleOptional = roleRepository.findById(3L);
        }

        Role role = roleOptional.orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò USER"));

        // Kiểm tra xem người dùng đã có vai trò này chưa
        boolean hasRole = false;
        if (user.getRoles() != null) {
            hasRole = user.getRoles().stream()
                    .anyMatch(r -> r.getId().equals(role.getId()));
        }

        if (!hasRole) {
            // Tạo liên kết vai trò
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setCreatedAt(LocalDateTime.now());
            userRole.setUpdatedAt(LocalDateTime.now());
            userRoleRepository.save(userRole);
        }
    }

    /**
     * Phương thức tiện ích để tạo hồ sơ bệnh nhân mặc định cho người dùng mới
     */
    private void createDefaultPatientProfile(User user) {
        // Kiểm tra xem người dùng đã có hồ sơ bệnh nhân chưa
        if (patientProfileRepository.findByUserId(user.getId()).isEmpty()) {
            PatientProfile patientProfile = new PatientProfile();
            patientProfile.setUser(user);
            patientProfile.setCreatedAt(LocalDateTime.now());
            patientProfile.setUpdatedAt(LocalDateTime.now());
            patientProfileRepository.save(patientProfile);
        }
    }

    // CREATE - Đăng ký người dùng mới
    @Override
    @Transactional
    public UserDTO.GetUserDTO register(UserDTO.SaveUserDTO userDTO) {
        // Kiểm tra email đã tồn tại
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại: " + userDTO.getEmail());
        }

        // Mã hóa mật khẩu
        userDTO.setPassword(passwordEncoder.encode(userDTO.getPassword()));

        // Đặt giá trị mặc định
        userDTO.setEnabled(true);
        userDTO.setLocked(false);
        userDTO.setCreatedAt(LocalDateTime.now());
        userDTO.setUpdatedAt(LocalDateTime.now());

        // Lưu người dùng
        User savedUser = userRepository.save(userDtoToEntity(userDTO));

        // Gán vai trò mặc định
        assignDefaultRoleToUser(savedUser);

        // Tạo hồ sơ bệnh nhân mặc định
        createDefaultPatientProfile(savedUser);

        return entityToGetUserDTO(savedUser);
    }

    // CREATE/UPDATE - Tạo mới hoặc cập nhật người dùng
    @Override
    @Transactional
    public UserDTO.GetUserDTO saveOrUpdate(UserDTO.SaveUserDTO userDTO) {
        // Kiểm tra email đã tồn tại (đối với người dùng mới)
        if (userDTO.getId() == null && userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại: " + userDTO.getEmail());
        }

        User user;

        if (userDTO.getId() == null || userDTO.getId() == 0) {
            // Tạo mới người dùng
            user = new User();
            user.setCreatedAt(userDTO.getCreatedAt() != null ? userDTO.getCreatedAt() : LocalDateTime.now());

            // Đảm bảo mật khẩu được cung cấp cho người dùng mới
            if (userDTO.getPassword() == null || userDTO.getPassword().isEmpty()) {
                throw new IllegalArgumentException("Mật khẩu không được để trống khi tạo người dùng mới");
            }
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));

            // Đặt giá trị mặc định nếu không được cung cấp
            user.setEnabled(userDTO.getEnabled() != null ? userDTO.getEnabled() : true);
            user.setLocked(userDTO.getLocked() != null ? userDTO.getLocked() : false);
            user.setCountLock(userDTO.getCountLock() != null ? userDTO.getCountLock() : 0);
        } else {
            // Cập nhật người dùng hiện có
            user = userRepository.findById(userDTO.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng có ID: " + userDTO.getId()));

            // Kiểm tra tính duy nhất của email nếu đã thay đổi
            if (!user.getEmail().equals(userDTO.getEmail()) &&
                    userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
                throw new IllegalArgumentException("Email đã tồn tại: " + userDTO.getEmail());
            }

            // Chỉ cập nhật mật khẩu nếu được cung cấp
            if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            }

            // Chỉ cập nhật các trường được cung cấp
            if (userDTO.getEnabled() != null) user.setEnabled(userDTO.getEnabled());
            if (userDTO.getLocked() != null) user.setLocked(userDTO.getLocked());
            if (userDTO.getCountLock() != null) user.setCountLock(userDTO.getCountLock());
        }

        // Cập nhật các trường thông thường
        user.setUpdatedAt(userDTO.getUpdatedAt() != null ? userDTO.getUpdatedAt() : LocalDateTime.now());
        user.setFullName(userDTO.getFullName());
        if (userDTO.getPhone() != null) user.setPhone(userDTO.getPhone());
        if (userDTO.getAddress() != null) user.setAddress(userDTO.getAddress());
        user.setEmail(userDTO.getEmail());

        // Xử lý avatar
        if (userDTO.getAvatar() != null && !userDTO.getAvatar().trim().isEmpty()) {
            user.setAvatar(userDTO.getAvatar());
        } else if (user.getAvatar() == null) {
            user.setAvatar("default-avatar.png");
        }

        // Lưu người dùng
        User savedUser = userRepository.save(user);

        // Nếu là người dùng mới, tạo các thông tin liên quan mặc định
        if (userDTO.getId() == null || userDTO.getId() == 0) {
            // Gán vai trò mặc định
            assignDefaultRoleToUser(savedUser);

            // Tạo hồ sơ bệnh nhân mặc định
            createDefaultPatientProfile(savedUser);
        }

        return entityToGetUserDTO(savedUser);
    }

    // READ - Lấy tất cả người dùng
    @Override
    public List<UserDTO.GetUserDTO> findAll() {
        return userRepository.findAllActive()
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // READ - Lấy người dùng theo ID
    @Override
    public Optional<UserDTO.GetUserDTO> findById(Long id) {
        return userRepository.findActiveById(id)
                .map(userMapper::toGetUserDTO);

    }

    // READ - Lấy người dùng theo email
    @Override
    public Optional<UserDTO.GetUserDTO> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toGetUserDTO);
    }

    // READ - Lấy người dùng theo tên
    @Override
    public List<UserDTO.GetUserDTO> findByFullNameContaining(String fullName) {
        return userRepository.findByFullNameContaining(fullName)
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // READ - Lấy người dùng theo số điện thoại
    @Override
    public List<UserDTO.GetUserDTO> findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // READ - Lấy người dùng theo địa chỉ
    @Override
    public List<UserDTO.GetUserDTO> findByAddressContaining(String address) {
        return userRepository.findByAddressContaining(address)
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // READ - Lấy người dùng theo trạng thái kích hoạt
    @Override
    public List<UserDTO.GetUserDTO> findByEnabled(Boolean enabled) {
        return userRepository.findByEnabled(enabled)
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // READ - Lấy người dùng theo trạng thái khóa
    @Override
    public List<UserDTO.GetUserDTO> findByLocked(Boolean locked) {
        return userRepository.findByLocked(locked)
                .stream()
                .map(userMapper::toGetUserDTO)
                .collect(Collectors.toList());
    }

    // DELETE - Xóa nhiều người dùng theo danh sách ID
    @Override
    @Transactional
    public String deleteByList(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (userRepository.existsById(id)) {
                userRepository.softDelete(id);
                count++;
            }
        }
        return "Đã xóa " + count + " tài khoản";
    }

    // Đăng nhập thông thường
    @Override
    public String login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        return jwtTokenProvider.generateToken(authentication);
    }

    // Đăng nhập Google
    @Override
    @Transactional
    public String loginWithGoogle(String googleAccessToken) {
        try {
            // Xác thực token Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(googleAccessToken);
            if (idToken == null) {
                throw new IllegalArgumentException("Mã truy cập Google không hợp lệ");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String fullName = (String) payload.get("name");
            String avatarUrl = (String) payload.get("picture");

            // Tìm hoặc tạo người dùng
            Optional<User> existingUser = userRepository.findByEmail(email);
            User user;

            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                // Tạo người dùng mới cho đăng nhập Google
                user = new User();
                user.setEmail(email);
                user.setFullName(fullName);
                user.setEnabled(true);
                user.setLocked(false);
                user.setAvatar(avatarUrl);
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());

                // Tạo mật khẩu ngẫu nhiên cho người dùng đăng nhập xã hội
                String randomPassword = passwordEncoder.encode(
                        "GOOGLE_" + System.currentTimeMillis()
                );
                user.setPassword(randomPassword);

                user = userRepository.save(user);

                // Gán vai trò mặc định
                assignDefaultRoleToUser(user);

                // Tạo hồ sơ bệnh nhân mặc định
                createDefaultPatientProfile(user);
            }

            // Tạo token xác thực
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    user.getPassword(),
                    user.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Tạo JWT token
            return jwtTokenProvider.generateToken(authentication);

        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Lỗi xác thực Google: " + e.getMessage());
        }
    }

    // Đăng nhập Facebook
    @Override
    @Transactional
    public String loginWithFacebook(String facebookAccessToken) {
        try {
            // TODO: Implement actual Facebook authentication logic
            String facebookGraphUrl = String.format(
                    "https://graph.facebook.com/me?fields=id,name,email&access_token=%s",
                    facebookAccessToken
            );

            // Giả sử dữ liệu đã được lấy từ Facebook
            String email = "facebook-user@example.com"; // Thay thế bằng dữ liệu thực từ Facebook
            String fullName = "Facebook User"; // Thay thế bằng dữ liệu thực từ Facebook

            // Tìm hoặc tạo người dùng
            Optional<User> existingUser = userRepository.findByEmail(email);
            User user;

            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                user = new User();
                user.setEmail(email);
                user.setFullName(fullName);
                user.setEnabled(true);
                user.setLocked(false);
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());

                // Tạo mật khẩu ngẫu nhiên cho người dùng đăng nhập xã hội
                String randomPassword = passwordEncoder.encode(
                        "FACEBOOK_" + System.currentTimeMillis()
                );
                user.setPassword(randomPassword);

                user = userRepository.save(user);

                // Gán vai trò mặc định
                assignDefaultRoleToUser(user);

                // Tạo hồ sơ bệnh nhân mặc định
                createDefaultPatientProfile(user);
            }

            // Tạo token xác thực
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    user.getPassword(),
                    user.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Tạo JWT token
            return jwtTokenProvider.generateToken(authentication);

        } catch (Exception e) {
            throw new IllegalArgumentException("Lỗi xác thực Facebook: " + e.getMessage());
        }
    }

    // Đổi mật khẩu
    @Override
    @Transactional
    public Optional<UserDTO.GetUserDTO> changePassword(String oldPassword, String newPassword, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng có ID: " + id));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return Optional.of(entityToGetUserDTO(user));
    }

    // Upload avatar
    @Override
    public String uploadAvatar(MultipartFile file) throws IOException {
        return s3Service.uploadFile(file);
    }

    // Xóa avatar
    @Override
    public void deleteAvatar(String avatar) {
        if (avatar != null && !avatar.equals("default-avatar.png")) {
            s3Service.deleteFile(avatar);
        }
    }

    // Lấy ID hồ sơ bác sĩ từ ID người dùng
    @Override
    public Long getDoctorProfileIdByUserId(Long userId) {
        return doctorProfileRepository.findByUser_Id(userId)
                .map(DoctorProfile::getId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ cho người dùng này"));
    }

    // Lấy ID hồ sơ bệnh nhân từ ID người dùng
    @Override
    public Long getPatientProfileIdByUserId(Long userId) {
        return patientProfileRepository.findByUserId(userId)
                .map(PatientProfile::getId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân cho người dùng này"));
    }

    // Helper method: Chuyển đổi từ DTO sang Entity
    private User userDtoToEntity(UserDTO.SaveUserDTO userDTO) {
        User user = new User();
        user.setId(userDTO.getId());
        user.setEmail(userDTO.getEmail());
        user.setPassword(userDTO.getPassword()); // Lưu ý: Mật khẩu chưa được mã hóa tại đây
        user.setFullName(userDTO.getFullName());
        user.setPhone(userDTO.getPhone());
        user.setAddress(userDTO.getAddress());
        user.setEnabled(userDTO.getEnabled());
        user.setLocked(userDTO.getLocked());
        user.setAvatar(userDTO.getAvatar());
        user.setCreatedAt(userDTO.getCreatedAt());
        user.setUpdatedAt(userDTO.getUpdatedAt());
        return user;
    }

    // Helper method: Chuyển đổi từ Entity sang DTO với đầy đủ thông tin liên quan
    private UserDTO.GetUserDTO entityToGetUserDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO.GetUserDTO dto = new UserDTO.GetUserDTO();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setLastLogin(user.getLastLogin());
        dto.setEnabled(user.isEnabled());
        dto.setLocked(user.isLocked());
        dto.setAvatar(user.getAvatar());
        dto.setCountLock(user.getCountLock());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        return dto;
    }
}