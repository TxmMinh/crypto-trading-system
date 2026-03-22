package com.example.repository;

import com.example.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Trade> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Trade> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);
}
