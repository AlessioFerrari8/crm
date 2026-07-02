package it.northleap.backend.controllers;

import it.northleap.backend.dtos.AcceptInviteDto;
import it.northleap.backend.dtos.AcceptedUserResponse;
import it.northleap.backend.dtos.InviteCreatedResponse;
import it.northleap.backend.dtos.InviteUserDto;
import it.northleap.backend.dtos.ResetPasswordDto;
import it.northleap.backend.dtos.UpdateUserDto;
import it.northleap.backend.dtos.UserSummary;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Porting di UsersController (users.module.ts). POST /accept-invite e' pubblico (deve essere
// aggiunto al matcher list di SecurityConfig, stesso trattamento di /onboarding/login/refresh).
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @RequirePerm(resource = "user", action = PermAction.READ)
    public ResponseEntity<List<UserSummary>> list() {
        return ResponseEntity.ok(userService.list());
    }

    @PostMapping("/invite")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<InviteCreatedResponse> invite(@CurrentActor Actor actor, @Valid @RequestBody InviteUserDto dto) {
        return ResponseEntity.ok(userService.invite(actor, dto));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AcceptedUserResponse> accept(@Valid @RequestBody AcceptInviteDto dto) {
        return ResponseEntity.ok(userService.accept(dto));
    }

    @PatchMapping("/{id}")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<Void> update(@PathVariable UUID id, @RequestBody UpdateUserDto dto) {
        userService.update(id, dto);
        return ResponseEntity.noContent().build();
    }

    // non un vero delete: soft-deactivate (isActive=false + revoca sessioni attive), stesso
    // comportamento dell'originale
    @DeleteMapping("/{id}")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<Void> resetPassword(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordDto dto
    ) {
        userService.resetPassword(actor, id, dto.getPassword());
        return ResponseEntity.noContent().build();
    }
}
