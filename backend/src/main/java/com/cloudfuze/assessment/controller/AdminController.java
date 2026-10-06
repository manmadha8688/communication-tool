package com.cloudfuze.assessment.controller;

import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Admins only (enforced in SecurityConfig): the report and the question bank. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final com.cloudfuze.assessment.service.ReportService reports;
    private final com.cloudfuze.assessment.security.CurrentUser current;

    public AdminController(AdminService admin, com.cloudfuze.assessment.service.ReportService reports,
                           com.cloudfuze.assessment.security.CurrentUser current) {
        this.admin = admin;
        this.reports = reports;
        this.current = current;
    }

    /** The formatted Excel report: every candidate, marks per exam and overall, and a team summary. */
    @GetMapping("/report.xlsx")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) String team) throws Exception {
        String name = "Communication Assessment Report" + (team == null || team.isBlank() ? "" : " - " + team.replaceAll("[^A-Za-z0-9 _-]", "")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reports.excel(team));
    }

    @GetMapping("/summary")
    public Dtos.Summary summary(@RequestParam(required = false) String team) {
        return admin.summary(team);
    }

    @GetMapping("/attempts")
    public List<Dtos.AttemptRow> attempts(@RequestParam(required = false) String team) {
        return admin.rows(team);
    }

    @GetMapping("/attempts/{id}")
    public Dtos.AttemptDetail attempt(@PathVariable Long id) {
        return admin.detail(id);
    }

    /** Clears the candidate's test so they can sit it again. The old result is kept in the reset log. */
    @PostMapping("/attempts/{id}/reset")
    public void reset(@PathVariable Long id, @Valid @RequestBody(required = false) Dtos.ResetRequest r) {
        admin.reset(id, current.principal().email(), r == null ? null : r.reason());
    }

    @PostMapping("/attempts/{id}/rescore")
    public Dtos.AttemptDetail rescore(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean all) {
        admin.rescore(id, all);
        return admin.detail(id);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String team) {
        byte[] body = ("﻿" + admin.csv(team)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"communication-assessment.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(body);
    }

    @GetMapping("/questions")
    public List<Dtos.QuestionDto> questions() {
        return admin.questions();
    }

    @PostMapping("/questions")
    public Dtos.QuestionDto create(@Valid @RequestBody Dtos.QuestionRequest r) {
        return admin.saveQuestion(null, r);
    }

    @PutMapping("/questions/{id}")
    public Dtos.QuestionDto update(@PathVariable Long id, @Valid @RequestBody Dtos.QuestionRequest r) {
        return admin.saveQuestion(id, r);
    }

    @DeleteMapping("/questions/{id}")
    public void delete(@PathVariable Long id) {
        admin.deleteQuestion(id);
    }
}
