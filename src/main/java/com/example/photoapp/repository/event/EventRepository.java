package com.example.photoapp.repository.event;

import com.example.photoapp.domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Event> findBySchoolIdAndIsDefaultTrueAndDeletedAtIsNull(UUID schoolId);
}
