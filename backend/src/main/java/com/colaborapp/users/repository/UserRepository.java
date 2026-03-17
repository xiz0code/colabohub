package com.colaborapp.users.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.users.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = { "roles", "markets", "stores" })
    Optional<User> findWithAccessByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = { "roles", "markets", "stores" })
    Optional<User> findWithAccessById(Long id);

    @EntityGraph(attributePaths = { "roles", "markets", "stores" })
    List<User> findAllByOrderByFullNameAsc();
}
