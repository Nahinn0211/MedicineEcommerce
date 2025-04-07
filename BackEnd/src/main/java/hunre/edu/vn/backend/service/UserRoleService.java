package hunre.edu.vn.backend.service;

import hunre.edu.vn.backend.dto.UserRoleDTO;

import java.util.List;
import java.util.Optional;

public interface UserRoleService {


    UserRoleDTO.GetUserRoleDTO save(UserRoleDTO.SaveUserRoleDTO userRoleDTO);


    Optional<UserRoleDTO.GetUserRoleDTO> findById(Long id);

    List<UserRoleDTO.GetUserRoleDTO> findByUserId(Long userId);

    List<UserRoleDTO.GetUserRoleDTO> findAll();

    List<UserRoleDTO.GetUserRoleDTO> findByRoleId(Long roleId);


    void deleteAllByUserId(Long userId);


    void deleteById(Long id);
}