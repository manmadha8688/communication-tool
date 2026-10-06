package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    java.util.List<Question> findByPartAndActiveTrueOrderByOrdinalAscIdAsc(com.cloudfuze.assessment.domain.Part part);

    java.util.List<Question> findAllByOrderByPartAscOrdinalAscIdAsc();

    java.util.Optional<Question> findByCode(String code);

    /**
     * Locks a part's active questions. Two candidates pressing Start together would otherwise both
     * see the same question as unused and be given it; with the lock the second waits and sees it taken.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select q from Question q where q.part = :part and q.active = true order by q.ordinal, q.id")
    java.util.List<Question> lockActiveByPart(@org.springframework.data.repository.query.Param("part") com.cloudfuze.assessment.domain.Part part);
}
