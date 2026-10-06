package com.cloudfuze.assessment.controller;

import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.security.CurrentUser;
import com.cloudfuze.assessment.service.TestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The candidate's test. After it ends, /result gives their marks (no comments or point detail). */
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestService tests;
    private final CurrentUser current;

    public TestController(TestService tests, CurrentUser current) {
        this.tests = tests;
        this.current = current;
    }

    @GetMapping("/overview")
    public Dtos.Overview overview() {
        return tests.overview(current.user());
    }

    @PostMapping("/start")
    public Dtos.TestState start(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        return tests.start(current.user(), req.getHeader("User-Agent"),
                ip != null && !ip.isBlank() ? ip.split(",")[0].trim() : req.getRemoteAddr());
    }

    @PostMapping("/resume")
    public Dtos.TestState resume() {
        return tests.resume(current.user());
    }

    @GetMapping("/state")
    public Dtos.TestState state(@RequestHeader("X-Session-Key") String key) {
        return tests.current(current.user(), key);
    }

    @PostMapping("/answer")
    public void save(@Valid @RequestBody Dtos.SaveRequest r) {
        tests.save(current.user(), r);
    }

    @PostMapping("/next")
    public Dtos.TestState next(@Valid @RequestBody Dtos.SessionRequest r) {
        return tests.next(current.user(), r.sessionKey());
    }

    @PostMapping("/submit")
    public Dtos.TestState submit(@Valid @RequestBody Dtos.SessionRequest r) {
        return tests.submit(current.user(), r.sessionKey());
    }

    @GetMapping("/result")
    public Dtos.MyResult result() {
        return tests.result(current.user());
    }

    @PostMapping("/event")
    public Dtos.ProctorResult event(@Valid @RequestBody Dtos.ProctorRequest r) {
        return tests.proctor(current.user(), r);
    }
}
