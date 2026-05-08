package com.example.photoapp.service.provisioning;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProvisioningTest {

    private static final UUID SCHOOL = UUID.randomUUID();

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AppUserRepository repo;
    private UserProvisioning provisioning;

    @BeforeEach
    void setUp() {
        repo = mock(AppUserRepository.class);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        provisioning = new UserProvisioning(repo, encoder);
    }

    @Test
    void provision_returns_user_with_role_school_and_hashed_password() {
        AppUser u = provisioning.provision(SCHOOL, "alice@example.com", "secret-pass", Role.STUDENT, "555-0100");

        assertThat(u.getSchoolId()).isEqualTo(SCHOOL);
        assertThat(u.getRole()).isEqualTo(Role.STUDENT);
        assertThat(u.getPasswordHash()).isNotEqualTo("secret-pass");
        assertThat(encoder.matches("secret-pass", u.getPasswordHash())).isTrue();
        assertThat(u.getPhone()).isEqualTo("555-0100");
    }

    @Test
    void provision_translates_unique_violation_to_conflict() {
        doThrow(new DataIntegrityViolationException("dup")).when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> provisioning.provision(SCHOOL, "x@y.test", "pass1234", Role.TEACHER, null))
                .isInstanceOf(Errors.Conflict.class);
    }
}
