package io.github.akiraisozaki.hamon.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Claude Code と同じ経路（Streamable HTTP の MCP クライアント）でツールが見え、呼べることを確かめる。
 * 本物のポートで起動するのでテストのトランザクションは効かない。DB を変えないツール呼び出しだけにする
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HamonToolsTest {

	@LocalServerPort
	private int port;

	private McpSyncClient connect() {
		var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build();
		McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
		client.initialize();
		return client;
	}

	private static String text(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}

	@Test
	void ツール一覧に影響調査と解析がある() {
		try (McpSyncClient client = connect()) {
			assertThat(client.listTools().tools()).extracting(Tool::name).containsExactlyInAnyOrder("survey_impact", "analyze_sources");
		}
	}

	@Test
	void 誰も依存していない変更点の影響候補は空になる() {
		try (McpSyncClient client = connect()) {
			CallToolResult result = client.callTool(new CallToolRequest("survey_impact", Map.of("target", "nobody.DependsOn#this")));

			assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
			assertThat(text(result)).isEqualTo("[]");
		}
	}

	@Test
	void 範囲外の閾値はツールのエラー結果になる() {
		try (McpSyncClient client = connect()) {
			CallToolResult result = client.callTool(new CallToolRequest("survey_impact", Map.of("target", "x", "minProbability", 1.5)));

			assertThat(result.isError()).isTrue();
			assertThat(text(result)).contains("minProbability");
		}
	}
}
