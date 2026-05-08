package com.example.photoapp.repository.school;

import com.example.photoapp.domain.school.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID>, JpaSpecificationExecutor<School> {

    Optional<School> findByIdAndDeletedAtIsNull(UUID id);
}
