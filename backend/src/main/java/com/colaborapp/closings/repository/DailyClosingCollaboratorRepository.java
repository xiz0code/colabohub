package com.colaborapp.closings.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.closings.domain.DailyClosingCollaborator;

public interface DailyClosingCollaboratorRepository extends JpaRepository<DailyClosingCollaborator, Long> {

    List<DailyClosingCollaborator> findByDailyClosingIdOrderByCollaboratorNameSnapshotAsc(Long dailyClosingId);
}
