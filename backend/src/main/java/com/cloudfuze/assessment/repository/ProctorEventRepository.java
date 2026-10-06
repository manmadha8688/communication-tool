package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.ProctorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProctorEventRepository extends JpaRepository<ProctorEvent, Long> {

    java.util.List<ProctorEvent> findByAttemptIdOrderByAtAsc(Long attemptId);
}
