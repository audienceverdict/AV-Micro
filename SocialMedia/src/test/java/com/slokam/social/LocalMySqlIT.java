package com.slokam.social;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Requires a dedicated empty test database; verification removes its worker rows. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class LocalMySqlIT extends AbstractMySqlVerification {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
    r.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
    r.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
  }
}
