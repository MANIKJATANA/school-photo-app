package com.example.photoapp.repository.user;

import com.example.photoapp.domain.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Looks up a user by (school_id, email). Email is matched case-insensitively
     * because {@link AppUser#normaliseEmail} stores it lowercased; this query
     * lowercases the input for symmetry so callers don't need to.
     */
    @Query("""
            SELECT u FROM AppUser u
             WHERE u.schoolId = :schoolId
               AND lower(u.email) = lower(:email)
               AND u.deletedAt IS NULL
            """)
    Optional<AppUser> findActiveBySchoolAndEmail(@Param("schoolId") UUID schoolId,
                                                 @Param("email") String email);
}
