package com.colaborapp.users.web.dto;

import java.util.List;

import com.colaborapp.users.domain.RoleCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        String phone,
        String contactName,
        String description,
        @NotNull RoleCode role,
        List<Long> marketIds,
        List<Long> storeIds,
        Boolean active) {
}
