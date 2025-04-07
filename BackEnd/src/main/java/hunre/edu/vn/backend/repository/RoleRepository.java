package hunre.edu.vn.backend.repository;

import hunre.edu.vn.backend.entity.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends BaseRepository<Role> {

    Optional<Role> findByNameAndIsDeletedFalse(String name);

        @Query("SELECT r FROM Role r WHERE r.name = :name AND r.isDeleted = false")
        Optional<Role> findByName(String name);

}