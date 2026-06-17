package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlywayBaselineContractTests {

    private static final Path MIGRATION_DIRECTORY =
        Path.of("src", "main", "resources", "db", "migration");

    @Test
    void v1RecreatesTheCurrentSupabaseApplicationSchema() throws IOException {
        var migrations = Files.list(MIGRATION_DIRECTORY)
            .filter(path -> path.getFileName().toString().startsWith("V1__"))
            .toList();

        assertThat(migrations).hasSize(1);

        var sql = Files.readString(migrations.getFirst());

        assertThat(sql)
            .contains("create table public.profiles")
            .contains("create table public.readings")
            .contains("create table public.consents")
            .contains("create table public.purchases")
            .contains("create table public.followups")
            .contains("create table public.generation_records")
            .contains("create table public.guest_ownership_transfers")
            .contains("create table public.payment_webhook_events")
            .contains("create table public.free_reading_quota_events")
            .contains("create or replace function public.start_failed_reading_retry")
            .contains("alter table public.readings enable row level security")
            .doesNotContain("create table consent (");
    }

    @Test
    void productionConfigurationNeverAutomaticallyBaselinesAnUnknownDatabase()
        throws IOException {
        var yaml = Files.readString(
            Path.of("src", "main", "resources", "application.yaml")
        );

        assertThat(yaml)
            .contains("ddl-auto: validate")
            .contains("baseline-on-migrate: false")
            .contains("validate-on-migrate: true");
    }

    @Test
    void localApplicationDefaultsToThePostgresDevProfile() throws IOException {
        var yaml = Files.readString(
            Path.of("src", "main", "resources", "application.yaml")
        );
        var build = Files.readString(Path.of("build.gradle"));

        assertThat(yaml)
            .contains("active: dev")
            .contains("optional:classpath:applicaiton-secret.yaml");
        assertThat(build)
            .contains("testRuntimeOnly 'com.h2database:h2'")
            .doesNotContain("runtimeOnly 'com.h2database:h2'");
    }
}
