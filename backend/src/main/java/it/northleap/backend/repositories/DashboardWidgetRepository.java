package it.northleap.backend.repositories;

import it.northleap.backend.entities.DashboardWidget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, UUID> {
    List<DashboardWidget> findByTab_IdOrderBySortOrderAsc(UUID tabId);
    void deleteByTab_Id(UUID tabId);
}
