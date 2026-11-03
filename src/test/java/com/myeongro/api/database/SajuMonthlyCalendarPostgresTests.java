package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myeongro.api.domain.saju.calculation.*;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.SajuStoredCalculationMapper;

class SajuMonthlyCalendarPostgresTests {
	@Test void preservesV5AndStrictlyStoresV6WithoutPrivateOrMalformedMonthlyFields() throws Exception {
		String url = System.getProperty("freshPostgresJdbcUrl");
		String user = System.getProperty("freshPostgresUsername");
		String password = System.getProperty("freshPostgresPassword");
		assumeTrue(url != null && user != null && password != null, "Requires isolated fresh Postgres");
		Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
		var mapper = new ObjectMapper();
		var corrector = new TrueSolarTimeCorrector();
		var service = new SajuCalculationService(new SajuBirthPlaceCatalog(mapper,
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")), corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		var snapshot = service.calculate(Map.of("birthDate", "1992-08-17", "birthTime", "12:00",
			"birthTimePrecision", "exact", "provinceCode", "11", "luckDirectionBasis", "unspecified"), 2027);
		var stored = new LinkedHashMap<>(new SajuStoredCalculationMapper().map(snapshot));
		var v5 = mapper.valueToTree(Map.of("targetYear", 2027, "calculationSnapshot", stored));
		stored.put("monthlyCalendar", mapper.convertValue(new SajuMonthlyCalendarService().calculate(2027, snapshot.dayMaster()), new TypeReference<Map<String,Object>>() { }));
		ObjectNode v6 = mapper.valueToTree(Map.of("targetYear", 2027, "calculationSnapshot", stored));
		try (var connection = DriverManager.getConnection(url, user, password)) {
			try (var query = connection.prepareStatement("select public.is_valid_saju_v6_input(?::jsonb)")) {
				query.setString(1, v6.toString());
				try (var result = query.executeQuery()) { result.next(); assertThat(result.getBoolean(1)).isTrue(); }
				for (String field : new String[]{"birthDate", "birthTime", "provinceCode", "unknown"}) {
					var invalid = v6.deepCopy();
					((ObjectNode)invalid.at("/calculationSnapshot/monthlyCalendar/months/0/periods/0")).put(field, "forbidden");
					query.setString(1, invalid.toString());
					try (var result = query.executeQuery()) { result.next(); assertThat(result.getBoolean(1)).as(field).isFalse(); }
				}
				var gap = v6.deepCopy();
				((ObjectNode)gap.at("/calculationSnapshot/monthlyCalendar/months/0/periods/1")).put("startInclusive", "2027-01-08T00:00:00+09:00");
				query.setString(1, gap.toString());
				try (var result = query.executeQuery()) { result.next(); assertThat(result.getBoolean(1)).isFalse(); }
			}
			UUID owner = UUID.randomUUID();
			try (var insert = connection.prepareStatement("insert into public.profiles(id) values (?)")) { insert.setObject(1, owner); insert.executeUpdate(); }
			try (var insert = connection.prepareStatement("insert into public.readings(user_id, kind, title, input_payload, input_hash, schema_version) values (?, 'saju', 'synthetic', ?::jsonb, 'test-hash', ?)")) {
				insert.setObject(1, owner); insert.setString(2, v5.toString()); insert.setInt(3, 5); assertThat(insert.executeUpdate()).isOne();
				insert.setString(2, v6.toString()); insert.setInt(3, 6); assertThat(insert.executeUpdate()).isOne();
				var invalid = v6.deepCopy(); invalid.put("birthProfile", "forbidden"); insert.setString(2, invalid.toString());
				assertThatThrownBy(insert::executeUpdate).isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
			}
		}
	}
}
