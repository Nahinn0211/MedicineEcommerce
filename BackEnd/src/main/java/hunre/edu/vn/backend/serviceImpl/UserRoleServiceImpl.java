package hunre.edu.vn.backend.serviceImpl;

import hunre.edu.vn.backend.dto.UserRoleDTO;
import hunre.edu.vn.backend.entity.Role;
import hunre.edu.vn.backend.entity.User;
import hunre.edu.vn.backend.entity.UserRole;
import hunre.edu.vn.backend.exception.ResourceNotFoundException;
import hunre.edu.vn.backend.repository.RoleRepository;
import hunre.edu.vn.backend.repository.UserRepository;
import hunre.edu.vn.backend.repository.UserRoleRepository;
import hunre.edu.vn.backend.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation cho quản lý UserRole
 */
@Service
public class UserRoleServiceImpl implements UserRoleService {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    @Override
    public List<UserRoleDTO.GetUserRoleDTO> findAll() {
        return userRoleRepository.findAll().stream()
                .map(UserRoleDTO::fromEntity)
                .collect(Collectors.toList());
    }
    @Override
    @Transactional
    public UserRoleDTO.GetUserRoleDTO save(UserRoleDTO.SaveUserRoleDTO userRoleDTO) {
        User user = userRepository.findById(userRoleDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng có ID: " + userRoleDTO.getUserId()));

        Role role = roleRepository.findById(userRoleDTO.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò có ID: " + userRoleDTO.getRoleId()));

        // Kiểm tra nếu đã tồn tại
        Optional<UserRole> existingUserRole = userRoleRepository.findByUserIdAndRoleId(
                userRoleDTO.getUserId(), userRoleDTO.getRoleId());

        if (existingUserRole.isPresent()) {
            return UserRoleDTO.fromEntity(existingUserRole.get());
        }

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setCreatedAt(LocalDateTime.now());
        userRole.setUpdatedAt(LocalDateTime.now());

        UserRole savedUserRole = userRoleRepository.save(userRole);
        return UserRoleDTO.fromEntity(savedUserRole);
    }

    @Override
    public Optional<UserRoleDTO.GetUserRoleDTO> findById(Long id) {
        return userRoleRepository.findById(id)
                .map(UserRoleDTO::fromEntity);
    }

    @Override
    public List<UserRoleDTO.GetUserRoleDTO> findByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserRoleDTO.GetUserRoleDTO> findByRoleId(Long roleId) {
        return userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRoleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAllByUserId(Long userId) {
        userRoleRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        userRoleRepository.deleteById(id);
    }
}