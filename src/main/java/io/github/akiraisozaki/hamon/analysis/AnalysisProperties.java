package io.github.akiraisozaki.hamon.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml の hamon.analysis.* 。起動時に束ねられ、不正なら起動が失敗する */
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots) {

	public AnalysisProperties {
		if (sourceRoots == null || sourceRoots.isEmpty()) {
			throw new IllegalArgumentException("hamon.analysis.source-roots を 1 つ以上設定してください");
		}
		for (Path root : sourceRoots) {
			if (!Files.isDirectory(root)) {
				throw new IllegalArgumentException("hamon.analysis.source-roots がディレクトリでない: " + root.toAbsolutePath());
			}
		}
		sourceRoots = List.copyOf(sourceRoots);
	}
}
