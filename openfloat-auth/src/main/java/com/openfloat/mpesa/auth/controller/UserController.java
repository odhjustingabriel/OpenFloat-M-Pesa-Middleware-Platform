package com.openfloat.mpesa.auth.controller;

import com.openfloat.mpesa.auth.dto.UserCreateDto;
import com.openfloat.mpesa.auth.dto.UserResponseDto;
import com.openfloat.mpesa.auth.entity.User;
import com.openfloat.mpesa.auth.repository.UserRepository;
import com.openfloat.mpesa.common.dto.ApiResponse;
import com.openfloat.mpesa.common.exception.DuplicateResourceException;
import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SuppressWarnings("null")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDto>> createUser(@Valid @RequestBody UserCreateDto dto) {
        log.info("Admin creating user: {}", dto.getUsername());
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        User user = User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(toResponseDto(user), "User created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> getUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return ResponseEntity.ok(ApiResponse.success(toResponseDto(user)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponseDto>>> getAllUsers() {
        List<UserResponseDto> users = userRepository.findAll().stream()
                .map(this::toResponseDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateUserStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        log.info("Admin updating user [{}] status to: {}", id, status);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setStatus(status.toUpperCase());
        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(toResponseDto(user), "User status updated successfully"));
    }

    private UserResponseDto toResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
