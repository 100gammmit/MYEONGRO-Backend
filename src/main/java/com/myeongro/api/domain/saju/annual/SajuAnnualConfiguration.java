package com.myeongro.api.domain.saju.annual;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!ai-preview")
@Import({SajuAnnualPromptCatalog.class, OpenAiSajuAnnualGenerator.class})
public class SajuAnnualConfiguration { }
