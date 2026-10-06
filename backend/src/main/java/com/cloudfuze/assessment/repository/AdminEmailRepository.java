package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.AdminEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminEmailRepository extends JpaRepository<AdminEmail, Long> {

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<AdminEmail> findAllByOrderByEmailAsc();
}
