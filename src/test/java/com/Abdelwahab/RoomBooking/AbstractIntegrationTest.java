package com.Abdelwahab.RoomBooking;

import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.redis.testcontainers.RedisContainer;

/**
 * Shared Testcontainers base class for all integration tests that need a real
 * database and/or Redis.
 *
 * <p><strong>Container lifecycle</strong><br>
 * Both containers are started in a {@code static} initialiser block, which means
 * they start exactly <em>once per JVM</em> and are never stopped by the
 * Testcontainers JUnit extension between {@code @DirtiesContext} reloads.
 * Using {@code @Container} on static fields causes the extension to stop the
 * container when the context is refreshed (because the JUnit store is cleared),
 * leading to "Connection refused" errors in subsequent contexts.
 *
 * <p><strong>Spring Boot wiring</strong><br>
 * {@link #overrideProperties} is called by Spring before it creates the
 * {@code ApplicationContext}, injecting the random ports assigned by Docker into
 * the datasource URL and Redis host/port properties.
 *
 * <p><strong>How to use</strong><br>
 * Extend this class from any test that needs a real DB or Redis. No further
 * annotation is needed — {@code @SpringBootTest} and {@code @AutoConfigureTestEntityManager}
 * are already present here.
 *
 * <pre>{@code
 * class MyRepositoryTest extends AbstractIntegrationTest {
 *     @Autowired MyRepository repo;
 *     // ...
 * }
 * }</pre>
 */
@SpringBootTest
@AutoConfigureTestEntityManager
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final RedisContainer REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("hotelbooking_test")
                .withUsername("rb_user")
                .withPassword("rb_secret");

        REDIS = new RedisContainer("redis:7-alpine");

        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Injects the container-assigned host/port into the Spring datasource and
     * Redis properties before any {@code ApplicationContext} is created.
     *
     * <p>Spring Boot's {@code DynamicPropertySource} mechanism ensures these values
     * are visible to every auto-configuration class (JPA, Flyway, Lettuce, etc.)
     * regardless of the order in which beans are initialised.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // ── PostgreSQL ────────────────────────────────────────────────
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName",
                () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect");

        // ── Redis ─────────────────────────────────────────────────────
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}