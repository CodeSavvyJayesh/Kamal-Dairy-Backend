package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.service.CartService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Every endpoint derives the user from the authenticated principal.
 * Nothing here accepts an email, a price or a product name from the caller.
 *
 * Note: no @CrossOrigin("*") any more - CORS is centralised in SecurityConfig.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public CartItem addToCart(
            @RequestParam Integer productId,
            @RequestParam(defaultValue = "1") int quantity,
            Authentication authentication
    ) {
        return cartService.addToCart(authentication.getName(), productId, quantity);
    }

    @GetMapping
    public List<CartItem> getCart(Authentication authentication) {
        return cartService.getUserCart(authentication.getName());
    }

    @PutMapping("/update")
    public CartItem updateQuantity(
            @RequestParam Integer cartItemId,
            @RequestParam int quantity,
            Authentication authentication
    ) {
        return cartService.updateQuantity(authentication.getName(), cartItemId, quantity);
    }

    @DeleteMapping("/remove/{id}")
    public void removeItem(@PathVariable Integer id, Authentication authentication) {
        cartService.removeItem(authentication.getName(), id);
    }
}
