package it.northleap.backend.repositories;

import it.northleap.backend.entities.Dashboard;
import it.northleap.backend.entities.DashboardTab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DashboardTabRepository extends JpaRepository<DashboardTab, UUID> {
    List<DashboardTab> findByDashboard_IdOrderBySortOrderAsc(UUID dashboardId);

    void deleteByDashboard_Id(UUID dashboardId);

    UUID dashboard(Dashboard dashboard);
}
