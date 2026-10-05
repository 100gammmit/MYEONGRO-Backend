package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class FlywayBaselineContractTests {

	private static final Path MIGRATION_DIRECTORY =
		Path.of("src", "main", "resources", "db", "migration");
	private static final Path BASELINE =
		MIGRATION_DIRECTORY.resolve("V1__initialize_schema.sql");

	@Test
	void keepsExactlyOnePreProductionBaseline() throws IOException {
		try (var migrations = Files.list(MIGRATION_DIRECTORY)) {
			assertThat(migrations
				.filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
				.map(path -> path.getFileName().toString()))
				.containsExactly("V1__initialize_schema.sql");
		}
	}

	@Test
	void v1CreatesTheCurrentApplicationSchema() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create table public.profiles")
			.contains("create table public.oauth_accounts")
			.contains("create table public.consent_events")
			.contains("create table public.adult_eligibility_assertions")
			.contains("create table public.readings")
			.contains("create table public.generation_records")
			.contains("create type public.reading_kind")
			.contains("create type public.reading_status")
			.contains("create type public.generation_status");
	}

	@Test
	void v1ContainsTheCurrentFunctionAndConstraintContracts() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create function public.create_pending_reading")
			.contains("create function public.complete_reading_generation")
			.contains("create function public.fail_reading_generation")
			.contains("create function public.fail_stale_reading_generations")
			.contains("create function public.get_reading_credit_status")
			.contains("create function public.is_valid_saju_v5_input")
			.contains("readings_one_generating_per_user_uq")
			.contains("readings_no_persisted_prompt_text_check")
			.contains("readings_saju_v5_input_check")
			.contains("consent_events_document_type_check")
			.contains("oauth_accounts_provider_provider_user_id_key")
			.contains("foreign key (profile_id) references public.profiles(id) on delete cascade")
			.contains("foreign key (user_id) references public.profiles(id) on delete cascade")
			.contains("foreign key (reading_id) references public.readings(id) on delete cascade");
	}

	@Test
	void baselineStoresVersionedAdultEligibilityWithoutBirthData() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create table public.adult_eligibility_assertions")
			.contains("policy_version text not null")
			.contains("adult_eligibility_assertions_user_id_policy_version_key")
			.contains("adult_eligibility_assertions_signup_generation_unique")
			.doesNotContain("birth_date")
			.doesNotContain("date_of_birth");
	}

	@Test
	void baselinePersistsOnlyMinimalOAuthIdentifiers() throws IOException {
		String sql = baselineSql();
		String oauthTable = sql.substring(
			sql.indexOf("create table public.oauth_accounts"),
			sql.indexOf("create table public.profiles")
		);

		assertThat(oauthTable)
			.contains("provider_user_id text not null")
			.doesNotContain("email")
			.doesNotContain("display_name");
	}

	@Test
	void securityDefinerFunctionsRevokePublicExecution() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("revoke all on function public.apply_daily_reading_credit_reset(uuid, integer) from public")
			.contains("revoke all on function public.complete_reading_generation(uuid, bigint, text, jsonb, integer) from public")
			.contains("revoke all on function public.create_pending_reading(uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text, integer, integer) from public")
			.contains("revoke all on function public.fail_reading_generation(uuid, bigint, text) from public")
			.contains("revoke all on function public.fail_stale_reading_generations(interval) from public")
			.contains("revoke all on function public.get_reading_credit_status(uuid, integer) from public");
	}

	@Test
	void productionConfigurationRejectsUnknownOrDriftingSchemas() throws IOException {
		var yaml = Files.readString(
			Path.of("src", "main", "resources", "application.yaml")
		);

		assertThat(yaml)
			.contains("ddl-auto: validate")
			.contains("baseline-on-migrate: false")
			.contains("validate-on-migrate: true")
			.contains("clean-disabled: true");
	}

	private String baselineSql() throws IOException {
		return Files.readString(BASELINE).toLowerCase(Locale.ROOT);
	}
}
