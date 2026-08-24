package it.northleap.backend.repositories;

import it.northleap.backend.entities.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {
    // condivise + personali dell'utente, già ordinate: il filtro sui ruoli (roleIds, jsonb) non si
    // fa in SQL, sono pochisime righe e il confronto fra liste dentro un jsonb in query costa più
    // codice di quanto valga. Lo fa DashboardService in memoria
    List<Dashboard> findByOwnerIdIsNullOrOwnerIdOrderBySortOrderAscNameAsc(UUID ownerId);

    boolean existsByName(String name);
}
