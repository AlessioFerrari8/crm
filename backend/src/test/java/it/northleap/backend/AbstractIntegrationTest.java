package it.northleap.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base per i test che caricano il contesto Spring completo.
 *
 * <p>Il container e' {@code static}: ne parte uno solo per JVM, riusato da tutte le
 * sottoclassi grazie alla cache del contesto di Spring Test. {@code @ServiceConnection}
 * sovrascrive url/username/password del datasource con quelli del container, quindi le
 * credenziali di {@code application.properties} (pensate per un Postgres locale) e quelle
 * del compose ({@code user/user}) non c'entrano nulla con i test.
 *
 * <p>Lo schema parte vuoto ad ogni run, quindi qui gira per davvero tutta la DDL di
 * Hibernate e i quattro seeder ({@code RoleSeeder}, {@code ObjectTypeSeeder},
 * {@code PermissionSeeder}, {@code WorkflowSeeder}) — che e' il motivo principale per cui
 * conviene un container usa e getta invece del DB gia' popolato del compose.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

}
