package com.kamaldairy.kamal_dairy_backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kamaldairy.kamal_dairy_backend.model.Product;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an EMPTY products table with the starter catalogue on startup.
 *
 * A freshly created database has every table and no rows, so a new deployment
 * would otherwise open as a shop with nothing to sell. The catalogue lives in
 * src/main/resources/seed/products.json.
 *
 * It only ever runs against an empty table: once a single product exists it
 * does nothing, so it can never overwrite prices, stock or anything an admin
 * has changed. Set APP_SEED_CATALOGUE=false to switch it off entirely.
 *
 * Image paths in the seed file are relative ("/images/milk/..."). The browser
 * resolves them against the frontend, which is where those files are hosted.
 */
@Component
public class CatalogueSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    private static final String SEED_FILE = "seed/products.json";

    private record SeedProduct(
            String name,
            double price,
            String category,
            String imageUrl,
            String hsnCode,
            boolean trending
    ) {}

    private final ProductRepository products;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    public CatalogueSeeder(
            ProductRepository products,
            ObjectMapper mapper,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            @Value("${app.seed.catalogue:true}") boolean enabled
    ) {
        this.products = products;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (!enabled) {
            return;
        }

        // Seeding is a convenience. If it fails for any reason the app must
        // still start, so nothing here is allowed to escape.
        try {
            Integer inserted = transaction.execute(status -> seedIfEmpty());

            if (inserted != null && inserted > 0) {
                log.info("Products table was empty - seeded {} products from {}", inserted, SEED_FILE);
            }

        } catch (Exception e) {
            log.error("Catalogue seeding failed. The app will run with whatever products exist.", e);
        }
    }

    private int seedIfEmpty() {

        if (products.count() > 0) {
            return 0;
        }

        List<SeedProduct> seed = readSeedFile();

        List<Product> rows = new ArrayList<>(seed.size());
        List<String> trendingNames = new ArrayList<>();

        for (SeedProduct s : seed) {
            Product p = new Product();
            p.setName(s.name());
            p.setPrice(s.price());
            p.setCategory(s.category());
            p.setImageUrl(s.imageUrl());
            p.setHsnCode(s.hsnCode());
            rows.add(p);

            if (s.trending()) {
                trendingNames.add(s.name());
            }
        }

        products.saveAll(rows);

        // Product has no setter for the trending flag, so it is switched on
        // with one update after the rows exist.
        if (!trendingNames.isEmpty()) {
            entityManager.flush();
            entityManager
                    .createQuery("update Product p set p.isTrending = true where p.name in :names")
                    .setParameter("names", trendingNames)
                    .executeUpdate();
        }

        return rows.size();
    }

    private List<SeedProduct> readSeedFile() {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return mapper.readValue(in, new TypeReference<List<SeedProduct>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + SEED_FILE, e);
        }
    }
}
