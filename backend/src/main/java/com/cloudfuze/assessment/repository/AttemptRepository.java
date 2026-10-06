package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    java.util.Optional<Attempt> findByUserId(Long userId);

    /** Row lock: a save, a violation and the timer can arrive together and must not race. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Attempt a where a.user.id = :userId")
    java.util.Optional<Attempt> lockByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Attempt a where a.id = :id")
    java.util.Optional<Attempt> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.List<Attempt> findByStatus(com.cloudfuze.assessment.domain.AttemptStatus status);

    @org.springframework.data.jpa.repository.Query("select a from Attempt a join fetch a.user order by a.startedAt desc")
    java.util.List<Attempt> findAllWithUser();
}
