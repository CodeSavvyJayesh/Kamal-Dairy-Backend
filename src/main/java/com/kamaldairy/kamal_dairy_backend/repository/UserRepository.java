package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Integer> {

     // find the user by email (used in signup & login)
    Optional<User> findByEmail(String email);
    // check if email already exist
    boolean existsByEmail(String email);

}
