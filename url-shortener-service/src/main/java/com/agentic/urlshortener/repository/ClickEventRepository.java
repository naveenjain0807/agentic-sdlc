package com.agentic.urlshortener.repository;

import com.agentic.urlshortener.domain.ClickEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByShortUrlIdOrderByClickedAtDesc(Long shortUrlId, Pageable pageable);

    long countByShortUrlId(Long shortUrlId);
}
