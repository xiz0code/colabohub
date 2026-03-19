package com.colaborapp.security.web;

import java.util.List;

import com.colaborapp.security.web.dto.CurrentUserResponse;
import com.colaborapp.security.AuthenticatedUserService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public CurrentUserResponse me() {
        var snapshot = authenticatedUserService.getCurrentUserSnapshot();
        return new CurrentUserResponse(
                snapshot.user().getId(),
                snapshot.user().getEmail(),
                snapshot.user().getFullName(),
                snapshot.active(),
                snapshot.roles() == null ? List.of() : List.copyOf(snapshot.roles()),
                snapshot.activeMarketId(),
                snapshot.activeMarketName(),
                snapshot.marketIds() == null ? List.of() : List.copyOf(snapshot.marketIds()),
                snapshot.storeIds() == null ? List.of() : List.copyOf(snapshot.storeIds()));
    }
}
