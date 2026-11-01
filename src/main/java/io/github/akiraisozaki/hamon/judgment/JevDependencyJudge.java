package io.github.akiraisozaki.hamon.judgment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Jev（TypeSafe System One API）であいまいな辺を判定する。
 * 失敗は答えにせず例外で上げる（「呼べなかった」を「使っていない」と読ませない）。
 * 429 / 5xx / 接続失敗は間隔を倍にしながら 3 回まで再試行する（@Retryable、HamonApplication の @EnableResilientMethods で有効）
 */
@Component
class JevDependencyJudge implements DependencyJudge {

	private static final Logger log = LoggerFactory.getLogger(JevDependencyJudge.class);

	private final RestClient client;
	private final String model;

	JevDependencyJudge(JevProperties properties) {
		this.client = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
				.build();
		this.model = properties.model();
	}

	@Override
	@Retryable(includes = { HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
			maxRetries = 3, delay = 1000, multiplier = 2)
	public Map<String, Double> judge(Ambiguity ambiguity) {
		JevRequest request = JevRequest.of(model, ambiguity);
		JevRequest.Response response = client.post().uri("/v1/systemone")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(JevRequest.Response.class);
		if (response == null) {
			throw new IllegalStateException("Jev の応答が空");
		}
		Map<String, Double> probabilities = JevRequest.probabilities(ambiguity, response);
		// 判定ログ。モデルは更新されるので、どの版が答えたかを残す
		log.info("Jev {} が {} を判定: {}", response.model(), ambiguity.dependent(), probabilities);
		return probabilities;
	}
}
