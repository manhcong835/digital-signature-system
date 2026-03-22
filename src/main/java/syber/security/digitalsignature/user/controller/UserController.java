package syber.security.digitalsignature.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import syber.security.digitalsignature.common.dto.ApiResponse;
import syber.security.digitalsignature.common.util.SecurityUtil;
import syber.security.digitalsignature.exception.ResourceNotFoundException;
import syber.security.digitalsignature.user.dto.ChangePasswordRequest;
import syber.security.digitalsignature.user.dto.UpdateProfileRequest;
import syber.security.digitalsignature.user.dto.UserProfileResponse;
import syber.security.digitalsignature.user.entity.User;
import syber.security.digitalsignature.user.service.UserService;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile management APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser() {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)));
    }

    @PutMapping
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        User updated = userService.updateProfile(email, request);
        return ResponseEntity.ok(
                ApiResponse.success(UserProfileResponse.from(updated), "Profile updated successfully"));
    }

    @PutMapping("/password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        userService.changePassword(email, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
