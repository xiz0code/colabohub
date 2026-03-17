package com.colaborapp.closings.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.closings.domain.DailyClosingStore;

public interface DailyClosingStoreRepository extends JpaRepository<DailyClosingStore, Long> {

    List<DailyClosingStore> findByDailyClosingIdOrderByStoreNameSnapshotAsc(Long dailyClosingId);
}
