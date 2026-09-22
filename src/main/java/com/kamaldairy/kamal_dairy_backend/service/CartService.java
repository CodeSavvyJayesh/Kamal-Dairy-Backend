package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.OutOfStockException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Cart rules:
 *
 *  1. PRICE IS NEVER TAKEN FROM THE CLIENT. The caller sends a productId and a
 *     quantity; name and price are looked up in the products table. Previously
 *     the browser posted its own price, so anyone could buy 90-rupee milk for 1 rupee.
 *
 *  2. EVERY MUTATION IS SCOPED TO THE OWNER. update/remove take the caller's
 *     email and refuse to touch a row belonging to somebody else. Previously a
 *     bare cartItemId was enough to edit or delete another customer's cart.
 */
@Service
public class CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 99;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CartItem addToCart(String userEmail, Integer productId, int quantity) {

        if (productId == null) {
            throw new ApiException("productId is required.", HttpStatus.BAD_REQUEST);
        }

        validateQuantity(quantity);

        // Authoritative product data straight from the database.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        Optional<CartItem> existing =
                cartRepository.findByUserEmailAndProductId(userEmail, productId);

        CartItem item = existing.orElseGet(CartItem::new);

        int newQuantity = existing.isPresent()
                ? existing.get().getQuantity() + quantity
                : quantity;

        if (newQuantity > MAX_QUANTITY_PER_ITEM) {
            newQuantity = MAX_QUANTITY_PER_ITEM;
        }

        requireStock(product, newQuantity, existing.map(CartItem::getQuantity).orElse(0));

        item.setUserEmail(userEmail);
        item.setProductId(productId);
        item.setProductName(product.getName());
        item.setPrice(product.getPrice());
        item.setQuantity(newQuantity);

        return cartRepository.save(item);
    }

    /**
     * Returns the cart with name and price refreshed from the products table,
     * so a stale row can never show (or charge) an out-of-date price.
     */
    @Transactional
    public List<CartItem> getUserCart(String userEmail) {

        List<CartItem> items = cartRepository.findByUserEmail(userEmail);

        for (CartItem item : items) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                item.setProductName(product.getName());
                item.setPrice(product.getPrice());
            });
        }

        List<CartItem> saved = cartRepository.saveAll(items);

        // Not stored: shown so the cart can warn before checkout.
        for (CartItem item : saved) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                item.setStock(product.getStock());
                item.setImageUrl(product.getImageUrl());
            });
        }
        return saved;
    }

    @Transactional
    public CartItem updateQuantity(String userEmail, Integer cartItemId, int quantity) {

        validateQuantity(quantity);

        CartItem item = requireOwnedItem(userEmail, cartItemId);

        // Lowering a quantity is always allowed; raising it must fit the shelf.
        if (quantity > item.getQuantity()) {
            productRepository.findById(item.getProductId())
                    .ifPresent(product -> requireStock(product, quantity, item.getQuantity()));
        }

        item.setQuantity(Math.min(quantity, MAX_QUANTITY_PER_ITEM));

        return cartRepository.save(item);
    }

    @Transactional
    public void removeItem(String userEmail, Integer cartItemId) {
        cartRepository.delete(requireOwnedItem(userEmail, cartItemId));
    }

    @Transactional
    public void clearCart(String userEmail) {
        cartRepository.deleteAll(cartRepository.findByUserEmail(userEmail));
    }

    /**
     * Cart total in paise, computed from live product prices.
     * This is the ONLY number allowed to reach Razorpay.
     */
    @Transactional(readOnly = true)
    public long calculateTotalPaise(String userEmail) {

        List<CartItem> items = cartRepository.findByUserEmail(userEmail);

        if (items.isEmpty()) {
            throw new ApiException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : items) {

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product in your cart (id " + item.getProductId() + ")"));

            total = total.add(
                    BigDecimal.valueOf(product.getPrice())
                            .multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }

        long paise = total.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        if (paise <= 0) {
            throw new ApiException("Cart total must be greater than zero.", HttpStatus.BAD_REQUEST);
        }

        return paise;
    }

    private CartItem requireOwnedItem(String userEmail, Integer cartItemId) {

        if (cartItemId == null) {
            throw new ApiException("cartItemId is required.", HttpStatus.BAD_REQUEST);
        }

        CartItem item = cartRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item"));

        // 404 rather than 403 on purpose: do not confirm that somebody else's
        // cart item with this id exists.
        if (!item.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new ResourceNotFoundException("Cart item");
        }

        return item;
    }

    /** 409 when the cart would hold more than is on the shelf. Untracked products always pass. */
    private static void requireStock(Product product, int wanted, int alreadyInCart) {
        Integer stock = product.getStock();
        if (stock == null || wanted <= stock) {
            return;
        }
        if (stock <= 0) {
            throw new OutOfStockException(product.getName() + " is out of stock right now.");
        }
        throw new OutOfStockException("Only " + stock + " of " + product.getName() + " left"
                + (alreadyInCart > 0 ? " and you already have " + alreadyInCart + " in your cart." : "."));
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw new ApiException(
                    "Quantity must be between 1 and " + MAX_QUANTITY_PER_ITEM + ".",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
