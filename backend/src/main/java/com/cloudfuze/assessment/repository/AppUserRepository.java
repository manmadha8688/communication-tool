package com.cloudfuze.assessment.repository;

import com.cloudfuze.assessment.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    java.util.Optional<AppUser> findByEmailIgnoreCase(String email);

    java.util.List<AppUser> findByRoleOrderByCreatedAtDesc(com.cloudfuze.assessment.domain.Role role);

    @org.springframework.data.jpa.repository.Query("select distinct u.team from AppUser u where u.team is not null and u.team <> '' order by u.team")
    java.util.List<String> findDistinctTeams();
}
