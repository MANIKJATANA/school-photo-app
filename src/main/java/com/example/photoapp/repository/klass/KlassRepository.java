package com.example.photoapp.repository.klass;

import com.example.photoapp.domain.klass.Klass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface KlassRepository extends JpaRepository<Klass, UUID>, JpaSpecificationExecutor<Klass> {

    Optional<Klass> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Klass> findBySchoolIdAndNameAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, String name, String academicYear);
}
