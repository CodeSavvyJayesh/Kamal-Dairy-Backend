package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Review;
import com.kamaldairy.kamal_dairy_backend.model.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByProductIdAndUserEmail(Integer productId, String userEmail);

    List<Review> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findForUpdate(@Param("id") Long id);

    Page<Review> findByProductIdAndStatus(Integer productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByProductIdAndStatusAndRating(Integer productId, ReviewStatus status, int rating, Pageable pageable);

    /** [rating, count] for the published reviews of one product. */
    @Query("select r.rating, count(r) from Review r where r.productId = :pid and r.status = :status group by r.rating")
    List<Object[]> distribution(@Param("pid") Integer productId, @Param("status") ReviewStatus status);

    // ---- admin

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    Page<Review> findByStatusAndRatingLessThanEqual(ReviewStatus status, int rating, Pageable pageable);

    Page<Review> findByStatusAndReplyIsNull(ReviewStatus status, Pageable pageable);

    long countByStatus(ReviewStatus status);

    long countByStatusAndRatingLessThanEqual(ReviewStatus status, int rating);

    long countByStatusAndReplyIsNull(ReviewStatus status);

    @Query("select avg(r.rating) from Review r where r.status = :status")
    Double averageRating(@Param("status") ReviewStatus status);
}
