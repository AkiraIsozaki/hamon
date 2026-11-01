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
import io.github.akiraisozaki.hamon.judgment.Ambiguity;
import io.github.akiraisozaki.hamon.judgment.DependencyJudge;

/**
 * Java ソースを JavaParser で読み、メソッドをノード、メソッド呼び出しを辺にする。
 * - 型解決できた呼び出し先が解析対象の中にあれば確からしさ 1 の辺（確実な辺）
 * - 解析対象の外（JDK・ライブラリ）への呼び出しは辺にしない
 * - 型解決できない呼び出しは、同名のメソッド全部へのあいまいな辺。確からしさは呼び出し元のコードと候補の宣言を添えて DependencyJudge（Jev）に聞く
 * shortcut: オーバーロードは同じノードにまとまる（名前が クラス#メソッド のため）, 区別が要る時に引数の型を名前に足す
 */
@Component
public class JavaSourceAnalysis {

	private final DependencyJudge judge;

	public JavaSourceAnalysis(DependencyJudge judge) {
		this.judge = judge;
	}

	/** 呼び出し元のメソッドから見た 1 つの呼び出し。text は呼び出し式そのもの、name は呼んでいるメソッド名 */
	private record Call(String dependent, String callerSource, String text, String name) {
	}

	public DependencyGraph analyze(List<Path> sourceRoots) {
		List<CompilationUnit> units = parse(sourceRoots);
		List<MethodDeclaration> declarations = units.stream().flatMap(unit -> unit.findAll(MethodDeclaration.class).stream()).toList();

		Map<String, NodeKind> nodes = new LinkedHashMap<>();
		Map<String, String> declarationOf = new LinkedHashMap<>(); // ノード名 → 宣言。Jev に候補の説明として渡す
		for (MethodDeclaration declaration : declarations) {
			nodes.put(nodeName(declaration), NodeKind.METHOD);
			declarationOf.merge(nodeName(declaration), declaration.getDeclarationAsString(false, false, false), (a, b) -> a + "; " + b);
		}

		Map<String, DependencyEdge> edges = new LinkedHashMap<>();
		for (MethodDeclaration caller : declarations) {
			String dependent = nodeName(caller);
			for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
				addEdges(edges, declarationOf, new Call(dependent, caller.toString(), call.toString(), call.getNameAsString()), resolve(call));
			}
			for (MethodReferenceExpr reference : caller.findAll(MethodReferenceExpr.class)) {
				addEdges(edges, declarationOf, new Call(dependent, caller.toString(), reference.toString(), reference.getIdentifier()), resolve(reference));
			}
		}
		return new DependencyGraph(nodes, List.copyOf(edges.values()));
	}

	private void addEdges(Map<String, DependencyEdge> edges, Map<String, String> declarationOf, Call call, Optional<String> resolvedDependency) {
		if (resolvedDependency.isPresent()) {
			String dependency = resolvedDependency.get();
			if (declarationOf.containsKey(dependency)) {
				edges.put(call.dependent() + " -> " + dependency, DependencyEdge.certain(call.dependent(), dependency));
			}
			return;
		}
		Map<String, String> candidates = new LinkedHashMap<>();
		declarationOf.forEach((node, declaration) -> {
			if (node.endsWith("#" + call.name())) {
				candidates.put(node, declaration);
			}
		});
		if (candidates.isEmpty()) {
			return;
		}
		judge.judge(Ambiguity.methodCall(call.dependent(), call.callerSource(), call.text(), candidates))
				.forEach((dependency, probability) -> edges.putIfAbsent(call.dependent() + " -> " + dependency,
						new DependencyEdge(call.dependent(), dependency, probability)));
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
