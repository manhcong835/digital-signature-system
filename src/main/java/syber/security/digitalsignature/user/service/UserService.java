package syber.security.digitalsignature.user.service;

import syber.security.digitalsignature.user.dto.ChangePasswordRequest;
import syber.security.digitalsignature.user.dto.UpdateProfileRequest;
import syber.security.digitalsignature.user.entity.User;

import java.util.Optional;

public interface UserService {

    User createUser(String email, String password, String fullName, String phone, String organization);

    Optional<User> findByEmail(String email);

    User findById(Long id);

    boolean existsByEmail(String email);

    User updateProfile(String email, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);
}
