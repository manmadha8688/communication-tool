package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.Part;
import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.entity.Question;
import com.cloudfuze.assessment.repository.QuestionRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Loads the question bank (seed/questions.json, built from the Neutara 150-scenario sheet) on start.
 *
 * <p>ADDS ONLY WHAT IS MISSING, by code. A restart inserts nothing twice, and a question an admin has
 * edited or switched off is never put back the way it was.
 */
@Component
public class QuestionSeeder {

    private static final Logger log = LoggerFactory.getLogger(QuestionSeeder.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Seed(String code, String part, int ordinal, String category, String title, String scenario,
                String task, List<Dtos.Metric> metrics) {
    }

    private final QuestionRepository questions;
    private final ScoringService scoring;

    public QuestionSeeder(QuestionRepository questions, ScoringService scoring) {
        this.questions = questions;
        this.scoring = scoring;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        ClassPathResource file = new ClassPathResource("seed/questions.json");
        if (!file.exists()) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            List<Seed> seeds = new ObjectMapper().readValue(in, new TypeReference<>() { });
            int added = 0;
            for (Seed s : seeds) {
                if (questions.findByCode(s.code()).isPresent()) continue;
                Question q = new Question();
                q.setCode(s.code());
                q.setPart(Part.valueOf(s.part()));
                q.setOrdinal(s.ordinal());
                q.setCategory(s.category());
                q.setTitle(s.title());
                q.setScenario(s.scenario());
                q.setTask(s.task());
                q.setMetricsJson(scoring.metricsJson(s.metrics()));
                q.setActive(true);
                q.setCreatedAt(Instant.now());
                q.setUpdatedAt(Instant.now());
                questions.save(q);
                added++;
            }
            log.info("Question bank: {} in file, {} added", seeds.size(), added);
        } catch (Exception e) {
            log.error("Could not load the question bank", e);
        }
    }
}
