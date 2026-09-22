package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product,Integer> {

    List<Product> findByCategory(String category);
    List<Product> findTop3ByIsTrendingTrue();

    /** SELECT ... FOR UPDATE, always in id order so concurrent checkouts cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAll(@Param("ids") Collection<Integer> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockOne(@Param("id") Integer id);

    /** Puts units back. Untracked products (stock null) are left alone. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id and p.stock is not null")
    int restock(@Param("id") Integer id, @Param("qty") int qty);

    @Query("select p from Product p where p.stock is not null and p.stock <= :threshold order by p.stock asc, p.name asc")
    List<Product> findLowStock(@Param("threshold") int threshold);

    @Query("select count(p) from Product p where p.stock is not null and p.stock <= :threshold")
    long countLowStock(@Param("threshold") int threshold);

     // here we have to check how much time we are requiring before optimization in order to retrival of data

}
