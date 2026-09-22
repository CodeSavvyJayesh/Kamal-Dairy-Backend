package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.OutOfStockException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.CartItem;
import com.kamaldairy.kamal_dairy_backend.model.OrderItem;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Shelf stock for cart orders.
 *
 * A product with stock = null is not tracked and is always available, so the
 * existing catalogue keeps selling until an admin sets a count.
 *
 * Stock is only ever taken inside the checkout transaction, after the
 * product rows are locked (SELECT ... FOR UPDATE, in id order so two
 * checkouts can never deadlock). Two customers racing for the last pack of
 * paneer therefore cannot both get it, and stock can never go negative.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    public static final int LOW_STOCK_THRESHOLD = 5;
    public static final int MAX_STOCK = 100_000;

    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    public StockService(ProductRepository productRepository, CartRepository cartRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
    }

    // ------------------------------------------------------------ checkout

    /**
     * Quick check before a Razorpay window is opened, so a customer is not
     * asked to pay for something that is already gone. Not a reservation -
     * the real, locked check happens when the order is placed.
     */
    @Transactional(readOnly = true)
    public void requireAvailable(String userEmail) {
        List<CartItem> items = cartRepository.findByUserEmail(userEmail);
        Map<Integer, Product> products = new HashMap<>();
        for (CartItem item : items) {
            productRepository.findById(item.getProductId()).ifPresent(p -> products.put(p.getId(), p));
        }
        List<String> problems = shortages(items, products);
        if (!problems.isEmpty()) {
            throw new OutOfStockException(String.join(" ", problems));
        }
    }

    /**
     * Locks every product in the cart for the rest of the transaction. Must be
     * called before anything else in the transaction reads those products, so
     * the stock seen afterwards is the locked, current value.
     */
    public Map<Integer, Product> lock(List<CartItem> items) {
        Set<Integer> ids = new TreeSet<>();
        for (CartItem item : items) {
            ids.add(item.getProductId());
        }
        Map<Integer, Product> locked = new HashMap<>();
        if (ids.isEmpty()) {
            return locked;
        }
        for (Product p : productRepository.lockAll(ids)) {
            locked.put(p.getId(), p);
        }
        return locked;
    }

    /** Human-readable problems, one per product that cannot be supplied. Empty means all good. */
    public List<String> shortages(List<CartItem> items, Map<Integer, Product> products) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : wanted(items).entrySet()) {
            Product p = products.get(e.getKey());
            if (p == null || p.getStock() == null) {
                continue; // missing products are reported by pricing; untracked is always available
            }
            int have = p.getStock();
            if (have <= 0) {
                problems.add(p.getName() + " is out of stock.");
            } else if (have < e.getValue()) {
                problems.add("Only " + have + " of " + p.getName() + " left.");
            }
        }
        return problems;
    }

    /** Takes the cart's quantities off the locked products. Call only after shortages() came back empty. */
    public void take(List<CartItem> items, Map<Integer, Product> locked) {
        for (Map.Entry<Integer, Integer> e : wanted(items).entrySet()) {
            Product p = locked.get(e.getKey());
            if (p != null && p.getStock() != null) {
                int left = p.getStock() - e.getValue();
                if (left < 0) {
                    throw new IllegalStateException("Stock for product " + p.getId() + " would go negative");
                }
                p.setStock(left);
            }
        }
    }

    /** Returns a cancelled order's items to the shelf. Untracked or deleted products are skipped. */
    public void putBack(List<OrderItem> items) {
        if (items == null) {
            return;
        }
        for (OrderItem item : items) {
            if (item.getProductId() != null && item.getQuantity() > 0) {
                productRepository.restock(item.getProductId(), item.getQuantity());
            }
        }
    }

    // ------------------------------------------------------------- admin

    @Transactional
    public Product setStock(Integer productId, Integer stock) {
        if (stock != null && (stock < 0 || stock > MAX_STOCK)) {
            throw new ApiException("Stock must be between 0 and " + MAX_STOCK + ".", HttpStatus.BAD_REQUEST);
        }
        Product p = productRepository.lockOne(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
        p.setStock(stock);
        log.info("Stock for product {} set to {}", productId, stock == null ? "untracked" : stock);
        return p;
    }

    @Transactional
    public Product addStock(Integer productId, Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_STOCK) {
            throw new ApiException("Enter how many units arrived (1 to " + MAX_STOCK + ").", HttpStatus.BAD_REQUEST);
        }
        Product p = productRepository.lockOne(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
        int current = p.getStock() == null ? 0 : p.getStock();
        if (current + quantity > MAX_STOCK) {
            throw new ApiException("Stock can be at most " + MAX_STOCK + ".", HttpStatus.BAD_REQUEST);
        }
        p.setStock(current + quantity);
        log.info("Restocked product {} by {} to {}", productId, quantity, p.getStock());
        return p;
    }

    @Transactional(readOnly = true)
    public List<Product> lowStock() {
        return productRepository.findLowStock(LOW_STOCK_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public long countLowStock() {
        return productRepository.countLowStock(LOW_STOCK_THRESHOLD);
    }

    // ------------------------------------------------------------ helpers

    private static Map<Integer, Integer> wanted(List<CartItem> items) {
        Map<Integer, Integer> wanted = new TreeMap<>();
        for (CartItem item : items) {
            wanted.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return wanted;
    }
}
