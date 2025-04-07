package hunre.edu.vn.backend.controller;

import hunre.edu.vn.backend.dto.UserDTO;
import hunre.edu.vn.backend.payload.LoginRequest;
import hunre.edu.vn.backend.payload.SocialLoginRequest;
import hunre.edu.vn.backend.service.UserService;
import hunre.edu.vn.backend.utils.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Xác thực", description = "Các API xác thực và quản lý người dùng")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthController(JwtTokenProvider jwtTokenProvider, UserService userService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập người dùng", description = "Xác thực người dùng và tạo mã JWT")
    public ResponseEntity<String> loginUser(@RequestBody LoginRequest loginRequest) {
        String token = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login/google")
    @Operation(summary = "Đăng nhập bằng Google", description = "Xác thực người dùng bằng Google OAuth")
    public ResponseEntity<String> loginWithGoogle(@RequestBody SocialLoginRequest socialLoginRequest) {
        try {
            String token = userService.loginWithGoogle(socialLoginRequest.getAccessToken());
            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Đăng nhập Google thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/login/facebook")
    @Operation(summary = "Đăng nhập bằng Facebook", description = "Xác thực người dùng bằng Facebook OAuth")
    public ResponseEntity<String> loginWithFacebook(@RequestBody SocialLoginRequest socialLoginRequest) {
        try {
            String token = userService.loginWithFacebook(socialLoginRequest.getAccessToken());
            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Đăng nhập Facebook thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký người dùng", description = "Đăng ký người dùng mới")
    public ResponseEntity<UserDTO.GetUserDTO> registerUser(@RequestBody UserDTO.SaveUserDTO userDTO) {
        UserDTO.GetUserDTO registeredUser = userService.register(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại", description = "Truy xuất thông tin về người dùng đang đăng nhập")
    public ResponseEntity<?> getCurrentUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Chưa đăng nhập"));
        }

        // Lấy email từ Authentication
        String email = authentication.getName();

        Optional<UserDTO.GetUserDTO> userOptional = userService.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Không tìm thấy thông tin người dùng"));
        }

        // Tạo đối tượng response chi tiết
        UserInfoResponse userInfo = new UserInfoResponse(
                userOptional.get().getId(),
                userOptional.get().getEmail(),
                userOptional.get().getFullName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        );

        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/has-role")
    @Operation(summary = "Kiểm tra vai trò người dùng", description = "Kiểm tra xem người dùng hiện tại có vai trò được chỉ định không")
    public ResponseEntity<Map<String, Boolean>> checkUserRole(@RequestParam String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean hasRole = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        grantedAuthority.getAuthority().equalsIgnoreCase("ROLE_" + roleName));

        return ResponseEntity.ok(Collections.singletonMap("hasRole", hasRole));
    }

    // Lớp nội để trả về thông tin người dùng
    public static class UserInfoResponse {
        private Long id;
        private String email;
        private String fullName;
        private List<String> roles;

        public UserInfoResponse(Long id, String email, String fullName, List<String> roles) {
            this.id = id;
            this.email = email;
            this.fullName = fullName;
            this.roles = roles;
        }

        // Getters
        public Long getId() { return id; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
        public List<String> getRoles() { return roles; }
    }
}