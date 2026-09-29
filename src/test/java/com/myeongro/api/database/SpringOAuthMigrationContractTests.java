package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SpringOAuthMigrationContractTests {

	private static final Path BASELINE = Path.of(
		"src", "main", "resources", "db", "migration", "V1__initialize_schema.sql"
	);

	@Test
	void baselineUsesSpringOwnedProfilesAndOAuthAccounts() throws IOException {
		String sql = baselineSql();

		assertThat(sql)
			.contains("create table public.profiles")
			.contains("create table public.oauth_accounts")
			.contains("id bigint not null")
			.contains("profile_id uuid not null")
			.contains("provider text not null")
			.contains("provider_user_id text not null")
			.contains("oauth_accounts_provider_provider_user_id_key")
			.contains("foreign key (profile_id) references public.profiles(id) on delete cascade")
			.doesNotContain("auth.users")
			.doesNotContain("auth.uid")
			.doesNotContain("enable row level security")
			.doesNotContain("create policy");
	}

	@Test
	void baselinePersistsOnlyMinimalOAuthIdentifiers() throws IOException {
		String sql = baselineSql();

		String oauthTable = sql.substring(
			sql.indexOf("create table public.oauth_accounts"),
			sql.indexOf("create table public.profiles")
		);

		assertThat(oauthTable)
			.doesNotContain("email")
			.doesNotContain("display_name")
			.doesNotContain("updated_at");
	}

	private String baselineSql() throws IOException {
		return Files.readString(BASELINE).toLowerCase(Locale.ROOT);
	}
}
