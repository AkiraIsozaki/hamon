package io.github.akiraisozaki.hamon.graph;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

/**
 * Spring Data JDBC は識別子を "dependent_id" のように引用符付きで出す。Oracle は引用符付きだと
 * 大文字小文字を区別するため、通常の（大文字で作られた）表・列に合わせて大文字にする。
 */
@Configuration
@Profile("datajdbc")
class DataJdbcConfiguration {

	@Bean
	NamingStrategy upperCaseNamingStrategy() {
		return new DefaultNamingStrategy() {
			@Override
			public String getTableName(Class<?> type) {
				return super.getTableName(type).toUpperCase();
			}

			@Override
			public String getColumnName(RelationalPersistentProperty property) {
				return super.getColumnName(property).toUpperCase();
			}
		};
	}
}
