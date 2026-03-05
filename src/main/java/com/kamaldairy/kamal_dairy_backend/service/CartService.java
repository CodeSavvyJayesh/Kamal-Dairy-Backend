package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    // 🛒 Add product to cart
    public CartItem addToCart(String userEmail, Integer productId, String productName, double price) {

        Optional<CartItem> existingItem =
                cartRepository.findByUserEmailAndProductId(userEmail, productId);

        // If product already exists → increase quantity
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + 1);
            return cartRepository.save(item);
        }

        // Otherwise create new cart item
        CartItem newItem = new CartItem();
        newItem.setUserEmail(userEmail);
        newItem.setProductId(productId);
        newItem.setProductName(productName);
        newItem.setPrice(price);
        newItem.setQuantity(1);

        return cartRepository.save(newItem);
    }

    //  Get user's cart
    public List<CartItem> getUserCart(String userEmail) {
        return cartRepository.findByUserEmail(userEmail);
    }

    //  Update quantity
    public CartItem updateQuantity(Integer cartItemId, int quantity) {

        CartItem item = cartRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        item.setQuantity(quantity);

        return cartRepository.save(item);
    }

    // ❌ Remove item from cart
    public void removeItem(Integer cartItemId) {
        cartRepository.deleteById(cartItemId);
    }
}