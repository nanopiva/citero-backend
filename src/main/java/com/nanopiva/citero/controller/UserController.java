package com.nanopiva.citero.controller;

import com.nanopiva.citero.dto.user.UserResponseDto;
import com.nanopiva.citero.dto.user.UserUpdateDto;
import com.nanopiva.citero.exception.ForbiddenException;
import com.nanopiva.citero.security.UserDetailsImpl;
import com.nanopiva.citero.service.UserService;
import com.nanopiva.citero.dto.user.WorkspaceResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(userService.getUserById(userDetails.getId()));
    }

    @GetMapping("/me/workspaces")
    public ResponseEntity<List<WorkspaceResponseDto>> getMyWorkspaces(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(userService.getWorkspacesForUser(userDetails.getId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UserUpdateDto updateDto) {
        return ResponseEntity.ok(userService.updateUser(userDetails.getId(), updateDto));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        userService.deleteAccount(userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (!id.equals(userDetails.getId())) {
            throw new ForbiddenException("No tenés permiso para acceder a este usuario.");
        }
        return ResponseEntity.ok(userService.getUserById(id));
    }
}