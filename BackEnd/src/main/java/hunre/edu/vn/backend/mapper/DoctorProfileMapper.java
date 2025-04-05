package hunre.edu.vn.backend.mapper;


import hunre.edu.vn.backend.dto.DoctorProfileDTO;
import hunre.edu.vn.backend.entity.DoctorProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DoctorProfileMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    DoctorProfileDTO.GetDoctorProfileDTO toGetDoctorProfileDTO(DoctorProfile entity);

    DoctorProfile toEntity(DoctorProfileDTO.SaveDoctorProfileDTO dto);
}