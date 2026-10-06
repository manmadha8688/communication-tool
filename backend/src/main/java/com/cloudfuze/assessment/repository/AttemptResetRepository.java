package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.AttemptReset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptResetRepository extends JpaRepository<AttemptReset, Long> {

    java.util.List<AttemptReset> findByCandidateEmailIgnoreCaseOrderByClearedAtDesc(String email);

    java.util.List<AttemptReset> findAllByOrderByClearedAtDesc();
}
