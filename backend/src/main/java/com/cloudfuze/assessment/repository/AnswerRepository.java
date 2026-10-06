package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    java.util.Optional<Answer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    java.util.List<Answer> findByAttemptIdOrderByIdAsc(Long attemptId);

    boolean existsByQuestionId(Long questionId);

    /** How many attempts each question has been given to: [questionId, count]. */
    @org.springframework.data.jpa.repository.Query("select a.question.id, count(a) from Answer a group by a.question.id")
    java.util.List<Object[]> usage();

    java.util.List<Answer> findByScoreStatus(com.cloudfuze.assessment.domain.ScoreStatus status);
}
