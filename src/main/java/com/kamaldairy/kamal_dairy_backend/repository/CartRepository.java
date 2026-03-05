package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<CartItem,Integer> {
    // get all cart items for a specific user
    List<CartItem> findByUserEmail(String userEmail);
    // check if product already exist
    Optional<CartItem> findByUserEmailAndProductId(String userEmail,Integer productId);

}
