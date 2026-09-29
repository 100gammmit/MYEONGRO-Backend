package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AccountWithdrawalMigrationContractTests {

	private static final Path BASELINE = Path.of(
		"src", "main", "resources", "db", "migration", "V1__initialize_schema.sql"
	);

	@Test
	void baselineUsesImmediateAccountDeletionSchema() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create table public.profiles")
			.contains("foreign key (profile_id) references public.profiles(id) on delete cascade")
			.contains("foreign key (user_id) references public.profiles(id) on delete cascade")
			.doesNotContain("deleted_at")
			.doesNotContain("purge_after")
			.doesNotContain("purged_at")
			.doesNotContain("profiles_active_idx")
			.doesNotContain("profiles_purge_due_idx");
	}

	@Test
	void baselineStoresVersionedAdultEligibilityWithoutBirthData() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create table public.adult_eligibility_assertions")
			.contains("user_id uuid not null")
			.contains("policy_version text not null")
			.contains("confirmed_at timestamp with time zone not null")
			.contains("signup_generation_id character varying(64)")
			.contains("adult_eligibility_assertions_user_id_policy_version_key")
			.contains("adult_eligibility_assertions_signup_generation_unique")
			.contains("where (signup_generation_id is not null)")
			.doesNotContain("birth_date")
			.doesNotContain("date_of_birth");
	}

	private String baselineSql() throws IOException {
		return Files.readString(BASELINE).toLowerCase(Locale.ROOT);
	}
}
