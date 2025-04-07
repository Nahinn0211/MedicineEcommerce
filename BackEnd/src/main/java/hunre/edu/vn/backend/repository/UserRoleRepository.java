package hunre.edu.vn.backend.repository;

import hunre.edu.vn.backend.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByRoleId(Long roleId);

    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId")
    void deleteAllByUserId(Long userId);
}