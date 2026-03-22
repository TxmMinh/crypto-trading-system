package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores the best aggregated price for each trading pair.
 * - bidPrice: highest bid price across exchanges -> used for SELL orders
 * - askPrice: lowest ask price across exchanges  -> used for BUY orders
 */
@Entity
@Table(name = "aggregated_price")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal bidPrice;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal askPrice;

    @Column(nullable = false, length = 20)
    private String bidSource;

    @Column(nullable = false, length = 20)
    private String askSource;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
