package com.colaborapp.users.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);
}
