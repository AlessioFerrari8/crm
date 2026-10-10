package it.northleap.backend.repositories;

import it.northleap.backend.entities.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordRepository extends JpaRepository<Record, UUID> {
    Optional<Record> findByIdAndIsDeletedFalse(UUID id);

    // full-text sui valori del jsonb più il titolo, già filtrata per permessi. La stessa espressione dell'indice
    // idx_record_data_fts (IndexRunner), o l'indice non viene usato
    @Query(value = """
        select r.* from record r
            where r.is_deleted = false
            and (r.object_type_id in (:allTypes) or (r.object_type_id in (:ownTypes) and r.owner_id = :me))
            and (to_tsvector('italian', r.data) @@ plainto_tsquery('italian', :q)
                or r.title ilike concat('%', :q, '%'))
            order by ts_rank(to_tsvector('italian', r.data), plainto_tsquery('italian', :q)) desc, r.updated_at desc
            limit 30
           """, nativeQuery = true)
    List<Record> fullTextSearch(@Param("q") String q, @Param("allTypes") List<UUID> allTypes,
                                      @Param("ownTypes") List<UUID> ownTypes, @Param("me") UUID me);


    List<Record> findByIsDeletedFalseAndTitleContainingOrderByUpdatedAtDesc(String title);
    List<Record> findByIdInAndObjectType_IdAndIsDeletedFalse(List<UUID> ids, UUID objectTypeId);
    List<Record> findByIdInAndObjectType_IdAndOwnerIdAndIsDeletedFalse(List<UUID> ids, UUID objectTypeId, UUID ownerId);
    List<Record> findByObjectType_IdAndIsDeletedFalse(UUID objectTypeId);
    // cestino: ultimi eliminati del tipo (variante con owner per lo scope OWN)
    List<Record> findTop50ByObjectType_IdAndIsDeletedTrueOrderByUpdatedAtDesc(UUID objectTypeId);
    List<Record> findTop50ByObjectType_IdAndOwnerIdAndIsDeletedTrueOrderByUpdatedAtDesc(UUID objectTypeId, UUID ownerId);

    // per invetorySeeder
    boolean existsByObjectTypeIdAndTitleAndIsDeletedFalse(UUID objectTypeId, String title);

    // coppie di record dello stesso tipo, con titoli simili. similarity() è di
    // pg_trgm; 0.6 è una soglia prudente (0.3 troppo, 0.8 quasi niente)
    // r1.id < r2.id evita di restituire la stessa coppia due volte a specchio
    @Query(value = """
            select r1.id as leftId, r2.id as rightId, similarity(r1.title, r2.title) as score
            from record r1
            join record r2
              on r1.object_type_id = r2.object_type_id
             and r1.id < r2.id
            where r1.object_type_id = :objectTypeId
              and r1.is_deleted = false
              and r2.is_deleted = false
              and similarity(r1.title, r2.title) > :threshold
            order by score desc
            limit 200
            """, nativeQuery = true)
    List<DuplicatePair> findDuplicates(@Param("objectTypeId") UUID objectTypeId, @Param("threshold") double threshold);

    // record che citano l'id passato dentro il proprio `data` (campi di tipo reference).
    // Il confronto è sul jsonb serializzato a testo: grezzo, ma un UUID non genera falsi
    // positivi e così prende anche gli id dentro le liste
    @Query(value = """
            select * from record
            where is_deleted = false
              and data::text like concat('%', :id, '%')
            """, nativeQuery = true)
    List<Record> findAllReferencing(@Param("id") String id);

    // projection: Spring Data la implementa da sola
    interface DuplicatePair {
        UUID getLeftId();
        UUID getRightId();
        double getScore();
    }

    // revisioni DIRETTE di un preventivo: la regex chiude su [0-9]+$ apposta, un LIKE
    // 'prefix%' conterebbe anche le revisioni annidate (2025-13_2_2 inizia per "2025-13_")
    // e il progressivo salterebbe
    @Query(value = """
              select count(*) from record
              where object_type_id = :objectTypeId
                and data->>'number' ~ concat('^', :prefix, '[0-9]+$')
                and is_deleted = false
              """, nativeQuery = true)
    long countRevisionsOf(@Param("objectTypeId") UUID objectTypeId,
                          @Param("prefix") String prefix);

    Optional<Record> findFirstByObjectType_KeyAndIsDeletedFalseOrderByCreatedAtDesc(String key);
}
