package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.AttemptPart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptPartRepository extends JpaRepository<AttemptPart, Long> {

    java.util.Optional<AttemptPart> findByAttemptIdAndPart(Long attemptId, com.cloudfuze.assessment.domain.Part part);

    java.util.List<AttemptPart> findByAttemptIdOrderByIdAsc(Long attemptId);
}
