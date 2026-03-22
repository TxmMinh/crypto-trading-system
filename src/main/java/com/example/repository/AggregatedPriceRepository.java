package com.example.repository;

import com.example.entity.AggregatedPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AggregatedPriceRepository extends JpaRepository<AggregatedPrice, Long> {

    Optional<AggregatedPrice> findBySymbol(String symbol);

    List<AggregatedPrice> findAllByOrderBySymbolAsc();
}
