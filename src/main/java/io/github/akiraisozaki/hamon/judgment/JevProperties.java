package io.github.akiraisozaki.hamon.judgment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml の hamon.jev.* 。apiKey は環境変数 TYPESAFE_API_KEY から入る */
@ConfigurationProperties("hamon.jev")
public record JevProperties(String baseUrl, String apiKey, String model) {

	public JevProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("hamon.jev.base-url を設定してください");
		}
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("hamon.jev.api-key が空です。リポジトリ直下の .env に TYPESAFE_API_KEY を書いてコンテナをリビルドしてください");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("hamon.jev.model を設定してください（例: jev-1.14.0。エイリアスでなく版をピン留めする）");
		}
	}
}
