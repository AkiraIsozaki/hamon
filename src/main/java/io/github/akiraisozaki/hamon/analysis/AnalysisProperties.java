package io.github.akiraisozaki.hamon.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml の hamon.analysis.* 。起動時に束ねられ、不正なら起動が失敗する。
 * sourceRoots は Java ソース、mapperRoots は MyBatis マッパー XML を探すディレクトリ
 */
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots, List<Path> mapperRoots) {

	public AnalysisProperties {
		sourceRoots = directories("hamon.analysis.source-roots", sourceRoots);
		mapperRoots = directories("hamon.analysis.mapper-roots", mapperRoots);
	}

	private static List<Path> directories(String property, List<Path> roots) {
		if (roots == null || roots.isEmpty()) {
			throw new IllegalArgumentException(property + " を 1 つ以上設定してください");
		}
		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				throw new IllegalArgumentException(property + " がディレクトリでない: " + root.toAbsolutePath());
			}
		}
		return List.copyOf(roots);
	}
}
