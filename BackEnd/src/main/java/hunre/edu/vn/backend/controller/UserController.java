package hunre.edu.vn.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hunre.edu.vn.backend.dto.UserDTO;
import hunre.edu.vn.backend.dto.UserRoleDTO;
import hunre.edu.vn.backend.service.UserRoleService;
import hunre.edu.vn.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "API để quản lý người dùng")
public class UserController {

    private final UserService userService;

    @Autowired
    private UserRoleService userRoleService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Lấy tất cả người dùng", description = "Trả về danh sách tất cả người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách người dùng thành công")
    })
    @GetMapping
    public ResponseEntity<List<UserDTO.GetUserDTO>> getAllUsers() {
        List<UserDTO.GetUserDTO> users = userService.findAll();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Lấy người dùng theo ID", description = "Trả về người dùng theo ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy người dùng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO.GetUserDTO> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Tạo hoặc cập nhật người dùng với vai trò",
            description = "Tạo mới hoặc cập nhật người dùng với vai trò được chỉ định")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lưu người dùng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<UserDTO.GetUserDTO> saveOrUpdateUser(
            @RequestBody UserDTO.SaveUserWithRolesDTO userWithRolesDto) {
        try {
            UserDTO.SaveUserDTO userDto = userWithRolesDto.getUser();
            List<Long> roleIds = userWithRolesDto.getRoleIds();

            if (userDto.getId() != null && userDto.getId() > 0) {
                // Cập nhật người dùng hiện có
                Optional<UserDTO.GetUserDTO> existingUserOpt = userService.findById(userDto.getId());
                if (existingUserOpt.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }

                // Đặt thời gian cập nhật
                userDto.setUpdatedAt(LocalDateTime.now());
            } else {
                // Người dùng mới
                userDto.setCreatedAt(LocalDateTime.now());
                userDto.setUpdatedAt(LocalDateTime.now());
            }

            // Lưu thông tin người dùng
            UserDTO.GetUserDTO savedUser = userService.saveOrUpdate(userDto);

            // Nếu có danh sách vai trò được chỉ định, lưu thông tin vai trò
            if (roleIds != null && !roleIds.isEmpty()) {
                // Nếu là cập nhật, xóa tất cả vai trò hiện tại
                if (userDto.getId() != null && userDto.getId() > 0) {
                    userRoleService.deleteAllByUserId(savedUser.getId());
                }

                // Thêm các vai trò mới
                for (Long roleId : roleIds) {
                    UserRoleDTO.SaveUserRoleDTO userRoleDTO = new UserRoleDTO.SaveUserRoleDTO();
                    userRoleDTO.setUserId(savedUser.getId());
                    userRoleDTO.setRoleId(roleId);
                    userRoleService.save(userRoleDTO);
                }
            }

            // Lấy thông tin người dùng đã cập nhật để trả về
            return userService.findById(savedUser.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "Tạo hoặc cập nhật người dùng với avatar và vai trò",
            description = "Tạo mới hoặc cập nhật người dùng với avatar và vai trò được chỉ định")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lưu người dùng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    })
    @PostMapping(value= "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<UserDTO.GetUserDTO> saveOrUpdateUserWithAvatar(
            @RequestPart("user") String userJson,
            @RequestPart(value = "roleIds", required = false) String roleIdsJson,
            @RequestPart(value = "file", required = false) MultipartFile file){
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule()); // Để xử lý LocalDateTime
            UserDTO.SaveUserDTO userDto = objectMapper.readValue(userJson, UserDTO.SaveUserDTO.class);

            // Đọc danh sách roleIds nếu có
            List<Long> roleIds = null;
            if (roleIdsJson != null && !roleIdsJson.isEmpty()) {
                roleIds = objectMapper.readValue(roleIdsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            }

            if (userDto.getId() != null && userDto.getId() > 0) {
                // Cập nhật người dùng hiện có
                Optional<UserDTO.GetUserDTO> existingUserOpt = userService.findById(userDto.getId());
                if (existingUserOpt.isPresent()) {
                    // Xử lý cập nhật avatar
                    if (file != null && !file.isEmpty()) {
                        String currentAvatar = existingUserOpt.get().getAvatar();
                        if (currentAvatar != null && !currentAvatar.equals("default-avatar.png")) {
                            userService.deleteAvatar(currentAvatar);
                        }
                        String newImageUrl = userService.uploadAvatar(file);
                        userDto.setAvatar(newImageUrl);
                    } else {
                        // Giữ nguyên avatar hiện tại
                        userDto.setAvatar(existingUserOpt.get().getAvatar());
                    }
                    userDto.setUpdatedAt(LocalDateTime.now());
                } else {
                    return ResponseEntity.notFound().build();
                }
            } else {
                // Người dùng mới
                if (file != null && !file.isEmpty()) {
                    String newImageUrl = userService.uploadAvatar(file);
                    userDto.setAvatar(newImageUrl);
                } else {
                    userDto.setAvatar("default-avatar.png");
                }
                userDto.setCreatedAt(LocalDateTime.now());
                userDto.setUpdatedAt(LocalDateTime.now());
            }

            UserDTO.GetUserDTO savedUser = userService.saveOrUpdate(userDto);

            // Nếu có danh sách vai trò được chỉ định, lưu thông tin vai trò
            if (roleIds != null && !roleIds.isEmpty()) {
                // Nếu là cập nhật, xóa tất cả vai trò hiện tại
                if (userDto.getId() != null && userDto.getId() > 0) {
                    userRoleService.deleteAllByUserId(savedUser.getId());
                }

                // Thêm các vai trò mới
                for (Long roleId : roleIds) {
                    UserRoleDTO.SaveUserRoleDTO userRoleDTO = new UserRoleDTO.SaveUserRoleDTO();
                    userRoleDTO.setUserId(savedUser.getId());
                    userRoleDTO.setRoleId(roleId);
                    userRoleService.save(userRoleDTO);
                }
            }

            // Lấy thông tin người dùng đã cập nhật để trả về
            return userService.findById(savedUser.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Operation(summary = "Xóa người dùng", description = "Xóa người dùng theo danh sách ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Xóa người dùng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @DeleteMapping
    public String deleteUser(@RequestBody List<Long> ids) {
        return userService.deleteByList(ids);
    }

    @Operation(summary = "Lấy người dùng theo email", description = "Trả về người dùng theo địa chỉ email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy người dùng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @GetMapping("/by-email")
    public ResponseEntity<UserDTO.GetUserDTO> getUserByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Tìm kiếm người dùng", description = "Tìm kiếm người dùng theo họ tên, số điện thoại hoặc địa chỉ")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tìm kiếm người dùng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số tìm kiếm không hợp lệ")
    })
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO.GetUserDTO>> searchUsers(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address) {
        if (fullName != null) {
            return ResponseEntity.ok(userService.findByFullNameContaining(fullName));
        } else if (phone != null) {
            return ResponseEntity.ok(userService.findByPhone(phone));
        } else if (address != null) {
            return ResponseEntity.ok(userService.findByAddressContaining(address));
        }
        return ResponseEntity.badRequest().build();
    }

    @Operation(summary = "Lấy người dùng theo trạng thái kích hoạt", description = "Trả về người dùng theo trạng thái kích hoạt")
    @GetMapping("/by-enabled")
    public ResponseEntity<List<UserDTO.GetUserDTO>> getUsersByEnabled(@RequestParam Boolean enabled) {
        return ResponseEntity.ok(userService.findByEnabled(enabled));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Đổi mật khẩu cho người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu cũ không chính xác")
    })
    @PutMapping("/change-password/{id}")
    public ResponseEntity<Optional<UserDTO.GetUserDTO>> changePassword(
            @PathVariable Long id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {

        Optional<UserDTO.GetUserDTO> updatedUser = userService.changePassword(oldPassword, newPassword, id);

        if (!updatedUser.isPresent()) {
            return ResponseEntity.status(400).body(updatedUser);
        }

        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Lấy người dùng theo trạng thái khóa", description = "Trả về người dùng theo trạng thái khóa")
    @GetMapping("/by-locked")
    public ResponseEntity<List<UserDTO.GetUserDTO>> getUsersByLocked(@RequestParam Boolean locked) {
        return ResponseEntity.ok(userService.findByLocked(locked));
    }
}