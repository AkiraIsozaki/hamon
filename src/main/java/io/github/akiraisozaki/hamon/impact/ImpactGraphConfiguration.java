package io.github.akiraisozaki.hamon.impact;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** ADR-0001: データアクセスの実装をプロファイルで切り替える（既定は application.yml の spring.profiles.default） */
@Configuration
class ImpactGraphConfiguration {

	@Bean
	@Profile("mybatis")
	ImpactGraph myBatisImpactGraph(ImpactMapper mapper) {
		return mapper::traceUpstream;
	}

	@Bean
	@Profile("datajdbc")
	ImpactGraph dataJdbcImpactGraph(ImpactRepository repository) {
		return repository::traceUpstream;
	}
}
