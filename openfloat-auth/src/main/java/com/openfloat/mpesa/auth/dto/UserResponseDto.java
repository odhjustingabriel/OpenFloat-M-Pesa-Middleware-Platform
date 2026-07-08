package com.openfloat.mpesa.auth.dto;

import com.openfloat.mpesa.auth.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private UUID id;
    private String username;
    private String email;
    private UserRole role;
    private String status;
    private Instant lastLogin;
    private Instant createdAt;
}
