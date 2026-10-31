package io.github.akiraisozaki.hamon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
public class HamonApplication {

	public static void main(String[] args) {
		SpringApplication.run(HamonApplication.class, args);
	}

}
