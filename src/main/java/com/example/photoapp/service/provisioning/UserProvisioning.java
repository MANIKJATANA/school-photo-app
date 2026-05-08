package com.example.photoapp.service.provisioning;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.user.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Creates an {@link AppUser} for a freshly-provisioned student or teacher.
 * Centralises the (encode password, save, translate duplicate-email DB error
 * to {@link Errors.Conflict}) sequence so both {@code StudentService} and
 * {@code TeacherService} consume one code path.
 */
@Service
public class UserProvisioning {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioning(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser provision(UUID schoolId, String email, String password, Role role, String phone) {
        AppUser user = new AppUser(schoolId, email, passwordEncoder.encode(password), role);
        user.setPhone(phone);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new Errors.Conflict("A user with that email already exists in this school");
        }
    }
}
