package io.github.akiraisozaki.hamon.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.NodeKind;

/**
 * Java ソースを JavaParser で読み、メソッドをノード、メソッド呼び出しを辺にする。
 * - 型解決できた呼び出し先が解析対象の中にあれば確からしさ 1 の辺（確実な辺）
 * - 解析対象の外（JDK・ライブラリ）への呼び出しは辺にしない
 * - 型解決できない呼び出しは、同名のメソッド全部へのあいまいな辺
 * shortcut: オーバーロードは同じノードにまとまる（名前が クラス#メソッド のため）, 区別が要る時に引数の型を名前に足す
 */
@Component
public class JavaSourceAnalysis {

	public DependencyGraph analyze(List<Path> sourceRoots) {
		List<CompilationUnit> units = parse(sourceRoots);
		List<MethodDeclaration> declarations = units.stream().flatMap(unit -> unit.findAll(MethodDeclaration.class).stream()).toList();

		Map<String, NodeKind> nodes = new LinkedHashMap<>();
		declarations.forEach(declaration -> nodes.put(nodeName(declaration), NodeKind.METHOD));

		Map<String, DependencyEdge> edges = new LinkedHashMap<>();
		for (MethodDeclaration caller : declarations) {
			String dependent = nodeName(caller);
			for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
				addEdges(edges, nodes, dependent, resolve(call), call.getNameAsString());
			}
			for (MethodReferenceExpr reference : caller.findAll(MethodReferenceExpr.class)) {
				addEdges(edges, nodes, dependent, resolve(reference), reference.getIdentifier());
			}
		}
		return new DependencyGraph(nodes, List.copyOf(edges.values()));
	}

	private static void addEdges(Map<String, DependencyEdge> edges, Map<String, NodeKind> nodes, String dependent,
			Optional<String> resolvedDependency, String calledName) {
		if (resolvedDependency.isPresent()) {
			String dependency = resolvedDependency.get();
			if (nodes.containsKey(dependency)) {
				edges.put(dependent + " -> " + dependency, DependencyEdge.certain(dependent, dependency));
			}
			return;
		}
		nodes.keySet().stream()
				.filter(node -> node.endsWith("#" + calledName))
				.forEach(dependency -> edges.putIfAbsent(dependent + " -> " + dependency,
						DependencyEdge.ambiguous(dependent, dependency)));
	}

	private static List<CompilationUnit> parse(List<Path> sourceRoots) {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false)); // false: JDK だけでなくクラスパス全体
		sourceRoots.forEach(root -> typeSolver.add(new JavaParserTypeSolver(root)));
		ParserConfiguration configuration = new ParserConfiguration()
				.setLanguageLevel(LanguageLevel.JAVA_21)
				.setSymbolResolver(new JavaSymbolSolver(typeSolver));
		try {
			return sourceRoots.stream()
					.flatMap(root -> parseRoot(root, configuration).stream())
					.toList();
		} catch (UncheckedIOException e) {
			throw new IllegalStateException("ソースを読めない: " + sourceRoots, e);
		}
	}

	private static List<CompilationUnit> parseRoot(Path root, ParserConfiguration configuration) {
		try {
			return new SourceRoot(root, configuration).tryToParse().stream()
					.flatMap(result -> result.getResult().stream())
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** クラス#メソッド。型解決に頼らず構文木から決めるので、継承元が解決できなくても落ちない */
	private static String nodeName(MethodDeclaration declaration) {
		String type = declaration.findAncestor(TypeDeclaration.class)
				.map(ancestor -> (TypeDeclaration<?>) ancestor) // findAncestor は raw 型を返す
				.flatMap(TypeDeclaration::getFullyQualifiedName)
				.orElseThrow(() -> new IllegalStateException("型の外にメソッドがある: " + declaration.getNameAsString()));
		return type + "#" + declaration.getNameAsString();
	}

	private static Optional<String> resolve(MethodCallExpr call) {
		try {
			return Optional.of(nodeName(call.resolve()));
		} catch (RuntimeException e) { // JavaParser は未解決を UnsolvedSymbolException など複数の実行時例外で表す
			return Optional.empty();
		}
	}

	private static Optional<String> resolve(MethodReferenceExpr reference) {
		try {
			return Optional.of(nodeName(reference.resolve()));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static String nodeName(ResolvedMethodDeclaration resolved) {
		return resolved.declaringType().getQualifiedName() + "#" + resolved.getName();
	}
}
