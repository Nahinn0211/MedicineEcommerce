package hunre.edu.vn.backend.repository;

import hunre.edu.vn.backend.entity.Brand;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends BaseRepository<Brand> {
    Optional<Brand> findByNameAndIsDeletedFalse(String name);
}