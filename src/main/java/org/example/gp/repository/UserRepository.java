package org.example.gp.repository;

import org.example.gp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Всички потребители на дадена кантора */
    List<User> findByOfficeId(Long officeId);
}
