package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SpringOAuthMigrationContractTests {

	private static final Path MIGRATION_DIRECTORY =
		Path.of("src", "main", "resources", "db", "migration");

	@Test
	void v3RemovesSupabaseAuthProfileForeignKeyAndAddsOAuthAccounts() throws IOException {
		var migration = Files.list(MIGRATION_DIRECTORY)
			.filter(path -> path.getFileName().toString()
				.startsWith("V3__spring_oauth_profiles_and_accounts"))
			.findFirst()
			.orElseThrow();

		String sql = Files.readString(migration);

		assertThat(sql)
			.contains("drop constraint if exists profiles_id_fkey")
			.contains("drop trigger if exists auth_user_created on auth.users")
			.contains("drop function if exists public.create_profile_for_new_user()")
			.contains("create table if not exists public.oauth_accounts")
			.contains("profile_id uuid not null references public.profiles(id)")
			.contains("unique (provider, provider_user_id)");
	}
}
