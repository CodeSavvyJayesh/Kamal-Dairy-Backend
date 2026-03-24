package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.service.CartService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // 🛒 Add product to cart
    @PostMapping("/add")
    public CartItem addToCart(
            @RequestParam Integer productId,
            @RequestParam String productName,
            @RequestParam double price,
            Principal principal
    ) {

        String userEmail = principal.getName();

        return cartService.addToCart(userEmail, productId, productName, price);
    }

    // 📦 Get logged-in user's cart
    @GetMapping
    public List<CartItem> getCart(Principal principal) {

        String userEmail = principal.getName();

        return cartService.getUserCart(userEmail);
    }

    // 🔄 Update quantity
    @PutMapping("/update")
    public CartItem updateQuantity(
            @RequestParam Integer cartItemId,
            @RequestParam int quantity
    ) {

        return cartService.updateQuantity(cartItemId, quantity);
    }

    // ❌ Remove item from cart
    @DeleteMapping("/remove/{id}")
    public void removeItem(@PathVariable Integer id) {

        cartService.removeItem(id);
    }
}