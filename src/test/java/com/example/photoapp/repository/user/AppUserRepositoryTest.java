package com.example.photoapp.repository.user;

import com.example.photoapp.TestcontainersConfiguration;
import com.example.photoapp.domain.school.School;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.school.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AppUserRepositoryTest {

    @Autowired AppUserRepository users;
    @Autowired SchoolRepository  schools;

    @Test
    void persists_and_finds_by_school_and_email_case_insensitive() {
        UUID schoolId = newSchool();
        AppUser u = new AppUser(schoolId, "Foo@Bar.com", "hash", Role.ADMIN);
        users.saveAndFlush(u);

        Optional<AppUser> found = users.findActiveBySchoolAndEmail(schoolId, "foo@BAR.COM");

        assertThat(found).isPresent();
        // PrePersist normalised the stored value to lowercase.
        assertThat(found.get().getEmail()).isEqualTo("foo@bar.com");
    }

    @Test
    void duplicate_email_in_same_school_is_rejected_by_unique_index() {
        UUID schoolId = newSchool();
        users.saveAndFlush(new AppUser(schoolId, "dup@example.com", "h1", Role.ADMIN));

        AppUser conflict = new AppUser(schoolId, "DUP@example.com", "h2", Role.TEACHER);

        assertThatThrownBy(() -> users.saveAndFlush(conflict))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void same_email_allowed_across_different_schools() {
        UUID schoolA = newSchool();
        UUID schoolB = newSchool();

        users.saveAndFlush(new AppUser(schoolA, "shared@example.com", "h", Role.ADMIN));
        users.saveAndFlush(new AppUser(schoolB, "shared@example.com", "h", Role.ADMIN));

        assertThat(users.findActiveBySchoolAndEmail(schoolA, "shared@example.com")).isPresent();
        assertThat(users.findActiveBySchoolAndEmail(schoolB, "shared@example.com")).isPresent();
    }

    @Test
    void soft_deleted_user_is_excluded_from_default_finders() {
        UUID schoolId = newSchool();
        AppUser u = new AppUser(schoolId, "ghost@example.com", "h", Role.STUDENT);
        users.saveAndFlush(u);

        u.softDelete(java.time.Instant.now());
        users.saveAndFlush(u);

        assertThat(users.findActiveBySchoolAndEmail(schoolId, "ghost@example.com")).isEmpty();
        assertThat(users.findByIdAndDeletedAtIsNull(u.getId())).isEmpty();
        // findById (the inherited JpaRepository method) still finds it because soft-delete is
        // domain semantics, not JPA semantics — that's deliberate.
        assertThat(users.findById(u.getId())).isPresent();
    }

    private UUID newSchool() {
        School s = new School("Test School " + UUID.randomUUID(), null, null);
        return schools.saveAndFlush(s).getId();
    }
}
