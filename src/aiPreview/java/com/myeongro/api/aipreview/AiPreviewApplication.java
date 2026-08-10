package com.myeongro.api.aipreview;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.service.OpenAiReadingGenerator;
import com.myeongro.api.domain.reading.service.ReadingGeneratorRouter;
import com.myeongro.api.domain.reading.service.SajuPromptCatalog;
import com.myeongro.api.domain.reading.service.TarotPromptCatalog;
import com.myeongro.api.domain.reading.service.TarotReadingResultValidator;
import com.myeongro.api.domain.saju.service.OpenAiSajuReadingGenerator;
import com.myeongro.api.domain.saju.service.SajuReadingResultValidator;

@SpringBootConfiguration
@ComponentScan(basePackages = "com.myeongro.api.aipreview")
@Profile("ai-preview")
@EnableAutoConfiguration(exclude = {
	DataSourceAutoConfiguration.class,
	FlywayAutoConfiguration.class,
	HibernateJpaAutoConfiguration.class,
	RedisAutoConfiguration.class,
	RedisRepositoriesAutoConfiguration.class,
	SessionAutoConfiguration.class,
	SecurityAutoConfiguration.class
})
@Import({
	OpenAiReadingGenerator.class,
	OpenAiSajuReadingGenerator.class,
	ReadingGeneratorRouter.class,
	TarotPromptCatalog.class,
	SajuPromptCatalog.class,
	TarotReadingResultValidator.class,
	SajuReadingResultValidator.class
})
public class AiPreviewApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(AiPreviewApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args).close();
	}

	@Bean
	AiPreviewCaseLoader aiPreviewCaseLoader(ObjectMapper objectMapper) {
		return new AiPreviewCaseLoader(objectMapper);
	}

	@Bean
	TarotPreviewInputValidator tarotPreviewInputValidator() {
		return new TarotPreviewInputValidator();
	}

	@Bean
	SajuPreviewInputValidator sajuPreviewInputValidator() {
		return new SajuPreviewInputValidator();
	}

	@Bean
	AiPreviewMarkdownRenderer aiPreviewMarkdownRenderer() {
		return new AiPreviewMarkdownRenderer(ZoneId.systemDefault());
	}

	@Bean
	AiPreviewReportWriter aiPreviewReportWriter(
		ObjectMapper objectMapper,
		AiPreviewMarkdownRenderer markdownRenderer
	) {
		return new AiPreviewReportWriter(
			objectMapper,
			markdownRenderer,
			Path.of("build", "ai-preview")
		);
	}

	@Bean
	AiPreviewLabelFactory aiPreviewLabelFactory() {
		return new AiPreviewLabelFactory(Clock.systemDefaultZone());
	}

	@Bean
	CommandLineRunner aiPreviewCommandLineRunner(AiPreviewRunner runner) {
		return args -> runner.run();
	}
}
