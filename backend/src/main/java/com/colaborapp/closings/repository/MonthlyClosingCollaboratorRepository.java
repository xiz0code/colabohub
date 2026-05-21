package com.colaborapp.closings.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.closings.domain.MonthlyClosingCollaborator;

public interface MonthlyClosingCollaboratorRepository extends JpaRepository<MonthlyClosingCollaborator, Long> {

    List<MonthlyClosingCollaborator> findByMonthlyClosingIdOrderByCollaboratorNameSnapshotAsc(Long monthlyClosingId);

    void deleteByMonthlyClosingId(Long monthlyClosingId);
}
