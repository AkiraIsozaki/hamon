package io.github.akiraisozaki.hamon.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.NodeKind;

/**
 * MyBatis のマッパー XML を読み、SQL 文を「namespace#id」の METHOD ノード（Mapper メソッドと同じ名前）にして、
 * その SQL が使う表・列への辺を張る。表と列のノードは DatabaseSchema から出す。
 * shortcut: &lt;include refid&gt; で差し込まれる SQL 片は展開しない, 共通の WHERE 句を include で持つ対象が出たら解決する
 */
@Component
public class MyBatisMapperAnalysis {

	private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");

	public DependencyGraph analyze(List<Path> mapperRoots, DatabaseSchema schema) {
		Map<String, NodeKind> nodes = new LinkedHashMap<>(schema.nodes());
		List<DependencyEdge> edges = new ArrayList<>();
		for (Element mapper : mapperElements(mapperRoots)) {
			String namespace = mapper.getAttribute("namespace");
			for (Element statement : childElements(mapper)) {
				if (!STATEMENT_TAGS.contains(statement.getTagName())) {
					continue;
				}
				String node = namespace + "#" + statement.getAttribute("id");
				nodes.put(node, NodeKind.METHOD);
				edges.addAll(schema.dependenciesOf(node, statement.getTextContent()));
			}
		}
		return new DependencyGraph(nodes, edges);
	}

	/** mapperRoots 配下の XML のうち、ルート要素が mapper のものだけ */
	private static List<Element> mapperElements(List<Path> mapperRoots) {
		DocumentBuilder builder = documentBuilder();
		try {
			return mapperRoots.stream()
					.flatMap(MyBatisMapperAnalysis::xmlFiles)
					.map(file -> parse(builder, file))
					.filter(root -> root.getTagName().equals("mapper"))
					.toList();
		} catch (UncheckedIOException e) {
			throw new IllegalStateException("マッパー XML を読めない: " + mapperRoots, e);
		}
	}

	private static Stream<Path> xmlFiles(Path root) {
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(file -> file.toString().endsWith(".xml")).toList().stream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Element parse(DocumentBuilder builder, Path file) {
		try {
			return builder.parse(file.toFile()).getDocumentElement();
		} catch (SAXException e) {
			throw new IllegalStateException("XML として読めない: " + file, e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** マッパー XML は DOCTYPE で mybatis.org の DTD を指すので、外部 DTD と外部実体を取りに行かない設定にする */
	private static DocumentBuilder documentBuilder() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("XML パーサを設定できない", e);
		}
	}

	private static List<Element> childElements(Element parent) {
		NodeList children = parent.getChildNodes();
		List<Element> elements = new ArrayList<>();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element element) {
				elements.add(element);
			}
		}
		return elements;
	}
}
