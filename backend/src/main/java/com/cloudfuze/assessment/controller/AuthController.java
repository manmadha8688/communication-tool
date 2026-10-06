package com.cloudfuze.assessment.controller;

import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.repository.AppUserRepository;
import com.cloudfuze.assessment.security.CurrentUser;
import com.cloudfuze.assessment.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser current;
    private final AppUserRepository users;

    public AuthController(AuthService auth, CurrentUser current, AppUserRepository users) {
        this.auth = auth;
        this.current = current;
        this.users = users;
    }

    @PostMapping("/auth/login")
    public Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest r) {
        return auth.login(r.idToken());
    }

    @GetMapping("/me")
    public Dtos.Me me() {
        return auth.me(current.user());
    }

    @PutMapping("/me/profile")
    public Dtos.Me profile(@Valid @RequestBody Dtos.ProfileRequest r) {
        return auth.saveProfile(current.user(), r);
    }

    /** Teams other people have entered, offered as suggestions so one team is not spelled five ways. */
    @GetMapping("/teams")
    public List<String> teams() {
        return users.findDistinctTeams();
    }
}
