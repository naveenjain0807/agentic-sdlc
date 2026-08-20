package com.agentic.urlshortener.repository;

import com.agentic.urlshortener.domain.ShortUrl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Optional<ShortUrl> findFirstByIdempotencyKeyOrderByIdAsc(String idempotencyKey);

    /** Re-use an existing, still-usable, never-expiring link for the same URL. */
    Optional<ShortUrl> findFirstByUrlHashAndActiveTrueAndExpiresAtIsNullOrderByIdAsc(String urlHash);

    Page<ShortUrl> findAllByActiveTrue(Pageable pageable);

    long countByActiveTrue();

    long countByActiveTrueAndExpiresAtBefore(Instant now);

    List<ShortUrl> findTop10ByActiveTrueOrderByTotalClicksDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShortUrl s set s.totalClicks = s.totalClicks + 1, s.lastAccessedAt = :clickedAt, s.updatedAt = :clickedAt where s.id = :id")
    int incrementClickCount(@Param("id") Long id, @Param("clickedAt") Instant clickedAt);

    /**
     * Pulls the next value from the H2 sequence created in migration V1.
     * A sequence keeps code generation collision-free without a read-before-write.
     */
    @Query(value = "SELECT NEXT VALUE FOR short_code_seq", nativeQuery = true)
    long nextShortCodeSequence();
}
