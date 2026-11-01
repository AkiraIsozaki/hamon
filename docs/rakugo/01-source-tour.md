# hamon 一席、ソース巡り

> 対象: Phase 6 完了＋フロント断面合わせ後（2026-11-01）のソース。パッケージは `io.github.akiraisozaki.hamon` を省略して書く。
> Phase 3 時点の版から書き直した。Data JDBC 版・`@Profile`・`ImpactGraph` / `GraphStore` インターフェースは ADR-0003 で消えている。

## 枕：石を投げれば波紋が立つ

えー、毎度バカバカしいお笑いを一席。昔から申しますな、「石を投げれば波紋が立つ」と。
これがプログラムの世界となりますと、メソッドをひとつ直せば、どこまで響くか分からねえ。
これを調べるのが「影響調査」。で、この調査をやる道具に「hamon」と名をつけた男がおりまして。

まず全体の流れ。hamon のバックエンドには REST の API が 3 本と、MCP の入口が 1 つある。これを追えば全ソースを一周できる。

**POST /api/analysis（解析してグラフを作り直す）**

1. `AnalysisController` が受ける。生成された `AnalysisApi` インターフェースの実装。中身は `Analysis#run` を呼ぶだけ
2. `Analysis` が `SchemaMapper` で接続中の Oracle の表と列を読み、`DatabaseSchema` にする（表と列がノードになる）
3. `JavaSourceAnalysis` が JavaParser で `hamon.analysis.source-roots` の Java を読み、メソッドをノード、呼び出しを辺にする
4. `MyBatisMapperAnalysis` が `hamon.analysis.mapper-roots` の MyBatis XML を読み、SQL 文をノード、SQL が使う表・列への辺にする。3 と 4 のグラフを `merge` する
5. 型が解けねえ呼び出しや、複数の表にある列名は「あいまいな辺」。`DependencyJudge`（本番は `JevDependencyJudge`）が候補ごとに確からしさを付ける
6. `GraphStore` が Oracle の表を全部消して書き直す。MyBatis だけ
7. ノード数と辺の数を `AnalysisResult` で返す

**GET /api/impact?target=…&minProbability=…（影響を調べる）**

1. `ImpactController` が受ける。パラメータ検証は生成インターフェースの注釈に任せる
2. `ImpactSurvey` に頼む。REST と MCP の両方から呼ばれる共通の裏側
3. `ImpactMapper` が再帰 SQL を Oracle に投げ、辺を逆向きに辿りながら確率を掛け合わせる
4. 結果 1 行が `ImpactRow`。DB 都合の形なので `toItem` で API の形 `ImpactItem`（ノードの種類つき）に変換する
5. フロントは openapi-fetch で受け取り、Cytoscape で波紋として描く。種類ごとに形が違う

**GET /api/nodes?q=…&limit=…（変更点の入力補完）**

1. `NodeSearchController` が受ける。`GraphMapper#searchNodes` の部分一致検索を `Node#toSummary` で API の形にする

**POST /mcp（Claude Code からの取次）**

1. Spring AI の MCP サーバーが受け、`HamonTools` の `survey_impact` / `analyze_sources` を呼ぶ。中身は上の `ImpactSurvey` と `Analysis` と同じ

パッケージの役割は、`impact` が読み取り、`graph` がグラフの型と保存と検索、`analysis` が解析、`judgment` が目利き（Jev）、`mcp` が取次。

---

## (a) 自動構成の段

八「ご隠居、ご隠居！ Spring Boot ってのを始めたんですが、こりゃあ何にも書いてねえのに動くんですよ。気味が悪くて」

隠「何を言ってやがる。見せてみろ」

`HamonApplication.java`

```java
@SpringBootApplication
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
@EnableResilientMethods // @Retryable を有効にする（Jev 呼び出しの再試行）
public class HamonApplication {

	public static void main(String[] args) {
		SpringApplication.run(HamonApplication.class, args);
	}

}
```

隠「ほう、たった三行だ。この `@SpringBootApplication` ってのが大家みてえなもんでな。『この長屋の住人は全部あたしが面倒見る』と。
同じパッケージの下に住んでる `@Component` やら `@RestController` やらを、片っ端から見つけて住まわせる」

実体は 3 つの注釈の束。`@ComponentScan` がこのクラスのパッケージ以下を走査して Bean 登録し、
`@EnableAutoConfiguration` がクラスパスを見て設定を組み、`@Configuration` がこのクラス自身を設定クラスにする。
下の 2 つは追加の支度で、`@ConfigurationPropertiesScan` は (d)、`@EnableResilientMethods` は演目 5（`05-jev-and-mcp.md`）で話す。

八「大家ってのは店賃を取るもんでしょう」

隠「店賃は `build.gradle` で先に払ってある」

```groovy
	// Spring AI のバージョンを BOM で揃える（MCP サーバー、ADR-0006）
	implementation platform('org.springframework.ai:spring-ai-bom:2.0.2')
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-jdbc'
	// データアクセスは MyBatis（ADR-0001）。Spring Boot 4.1 系に対応した 4.1.x を使う
	implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0'
	// DB マイグレーション。src/main/resources/db/migration の V__*.sql を起動時に適用する
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	runtimeOnly 'org.flywaydb:flyway-database-oracle'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	// 生成コードの @Valid / @Min などを実際に検証させる
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	runtimeOnly 'com.oracle.database.jdbc:ojdbc17'
	// Claude Code から impact / analysis を MCP ツールとして呼べるようにする（Streamable HTTP、ADR-0006）
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
	// Java ソースの解析（ADR-0002）。symbol-solver は呼び出し先の型解決に使う
	implementation 'com.github.javaparser:javaparser-symbol-solver-core:3.28.2'
```

隠「この starter てえのを一行足すと、大家が『ああ、JDBC が来たな、じゃあ DataSource を用意しとこう』と、言わねえでも全部支度する。これを自動構成と言う。
MCP の starter も同じで、一行足せば `/mcp` の戸が開く」

八「`platform(...)` ってのは」

隠「BOM、部品表だ。Spring AI は小さい jar の寄せ集めで、一つずつ版を書くと食い違う。『この表の版で揃えろ』と先に言っておけば、下の行は版を書かねえで済む」

八「じゃあ DB の繋ぎ先はどこに書いてあるんです」

`application.yml`

```yaml
spring:
  application:
    name: hamon
  # DB 接続情報は環境変数 SPRING_DATASOURCE_URL / USERNAME / PASSWORD（compose.yaml）で渡す。ここには書かない
```

隠「書いてねえ。環境変数の `SPRING_DATASOURCE_URL` を、大家が勝手に `spring.datasource.url` と読み替える（緩い束縛、relaxed binding）。
昔のおめえの会社の自社フレームワークじゃ、接続文字列を設定ファイルに書いて、自分で DriverManager を呼んで、と大騒ぎだったろう。
今は依存を一行足す、それだけだ。『一行の starter は千行の XML に勝る』とな」

---

## (b) DI と Bean の段

八「で、こいつがまた気味が悪い。コンストラクタに引数を書いときゃあ、誰かが勝手に渡してくるんです」

`analysis/Analysis.java`

```java
@Component
public class Analysis {

	private final AnalysisProperties properties;
	private final JavaSourceAnalysis javaAnalysis;
	private final MyBatisMapperAnalysis mapperAnalysis;
	private final SchemaMapper schemaMapper;
	private final GraphStore store;

	Analysis(AnalysisProperties properties, JavaSourceAnalysis javaAnalysis, MyBatisMapperAnalysis mapperAnalysis,
			SchemaMapper schemaMapper, GraphStore store) {
		this.properties = properties;
		this.javaAnalysis = javaAnalysis;
		this.mapperAnalysis = mapperAnalysis;
		this.schemaMapper = schemaMapper;
		this.store = store;
	}

	/** 作り直したグラフを返す */
	public DependencyGraph run() {
		DatabaseSchema schema = DatabaseSchema.of(schemaMapper.selectColumns());
		DependencyGraph graph = javaAnalysis.analyze(properties.sourceRoots())
				.merge(mapperAnalysis.analyze(properties.mapperRoots(), schema));
		store.replaceWith(graph);
		return graph;
	}
}
```

隠「それが DI、依存性注入だ。おめえ、朝起きて味噌汁が出てくるだろう。おめえが味噌屋に行って、豆腐屋に行って、火を起こして、とやらねえ。
『味噌汁をくれ』と手を出しときゃあ、女房が出す。Spring の中じゃあ女房がコンテナで、味噌汁が Bean だ」

Bean とはコンテナが作って持っている部品のこと。この 5 つの引数は、それぞれ別の方法で Bean になっている。

| 引数の型 | Bean になる仕組み |
|---|---|
| `AnalysisProperties` | `@ConfigurationProperties` を `@ConfigurationPropertiesScan` が拾う |
| `JavaSourceAnalysis`, `MyBatisMapperAnalysis`, `GraphStore` | クラスに `@Component` が付いている |
| `SchemaMapper` | `@Mapper` インターフェース。MyBatis の starter が実装を作って Bean にする |

コンストラクタがひとつなら `@Autowired` は書かない。

八「`GraphStore` は具象クラスですね。前はインターフェースだったような」

隠「ADR-0003 で外した。実装が一つしかねえインターフェースは、読む人の手数を増やすだけだ。
手を出す型は具象クラスでいい。インターフェースにするのは、**本当に差し替える相手がいるときだけ**。この長屋でそれは `DependencyJudge` の一つだけだ。(c) で話す」

八「じゃあ Spring がねえと何も作れねえんですか」

隠「いや、普通のクラスだ。見ろ、テストじゃ `new` してる」

`analysis/JavaSourceAnalysisTest.java`

```java
	private static DependencyGraph analyzeFixture(FixedDependencyJudge judge) {
		return new JavaSourceAnalysis(judge).analyze(List.of(Path.of("src/test/resources/analysis-fixture")));
	}
```

隠「コンストラクタで受け取る作りにしとくと、女房がいなくても自分で味噌汁が作れる。これがコンストラクタ注入のご利益だ。
目利きの `judge` にはテスト用の `FixedDependencyJudge` を渡してる。本物の Jev を呼ばねえで解析の検査ができる」

---

## (c) 番頭はテストへ引っ越した段

八「ご隠居、前の版では番頭部屋、`@Configuration` と `@Bean` がありましたよね。看板の `@Profile` も。今のソースを探したら、どこにもねえ」

隠「本番コードからは消えた。Data JDBC 版をやめた日に、番頭も看板もいらなくなった（ADR-0003）。
だが番頭は一人だけ、テストに引っ越してる」

`analysis/AnalysisApiTest.java`

```java
@SpringBootTest(properties = {
		"hamon.analysis.source-roots=src/test/resources/analysis-fixture",
		"hamon.analysis.mapper-roots=src/test/resources/analysis-fixture" })
@AutoConfigureMockMvc
@Transactional
class AnalysisApiTest {

	@TestConfiguration
	static class FixedJudgeConfiguration {

		@Bean
		@Primary // 本物の JevDependencyJudge も Bean として居るので、こちらを優先させる
		DependencyJudge fixedJudge() {
			return new FixedDependencyJudge(0.5);
		}
	}
```

隠「`@TestConfiguration` は『テストのときだけ開く番頭部屋』だ。テストクラスの中に static で入れておくと、**このテストの長屋にだけ**この番頭が立つ。他のテストには見えねえ」

八「で、`@Bean` の返したもんが Bean になる、と」

隠「そうだ、メソッド名が Bean 名。ここで返してるのは `DependencyJudge` の偽物、全候補に 0.5 を返すだけのやつだ」

八「でも本物の `JevDependencyJudge` も `@Component` で名乗ってるでしょう。`DependencyJudge` が二人になりませんか」

隠「なる。だから `@Primary` だ。同じ型の Bean が二人いて手を出されたら、`@Primary` の付いた方を渡す。付いてなきゃ『どっちだか分からねえ』で起動から転ぶ。
看板（`@Profile`）で片方を**消す**んじゃなくて、二人いる中で片方を**優先する**。似て非なる道具だ」

八「なんでテストで本物を使わねえんです」

隠「Jev は他人の店の API だ。テストのたびに呼ぶと、金がかかる、遅い、向こうの都合で答えが揺れる。
こういう**管理外の外部依存**だけはテストで差し替える。それ以外、Oracle も MyBatis も本物を使ってる。差し替えるためにインターフェースにしたのが `DependencyJudge` で、だからこれだけがインターフェースだ」

`judgment/DependencyJudge.java`

```java
@FunctionalInterface
public interface DependencyJudge {

	/** ambiguity の候補それぞれについて「dependent が本当にそれを使っている確率」。キーは候補の dependency ノード名 */
	Map<String, Double> judge(Ambiguity ambiguity);
}
```

---

## (d) 設定値を束ねる段

八「設定ファイルの値を読むのに、昔は `getProperty` を山ほど書きましたよ。あちこちで null かどうか見て」

隠「今はこうだ」

```yaml
hamon:
  analysis:
    # 解析する Java ソースのルート（作業ディレクトリからの相対パス）。まずはこのリポジトリ自身
    source-roots:
      - src/main/java
    # MyBatis マッパー XML を探すルート。SQL が使う表・列への辺になる（ADR-0004）
    mapper-roots:
      - src/main/resources
```

`analysis/AnalysisProperties.java`

```java
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
```

隠「yaml の `source-roots` が record の `sourceRoots` に入る。ハイフンをキャメルに、文字列を `Path` に、一つでも並びでもリストに。この面倒を全部 Spring がやる」

八「真ん中の、引数のねえコンストラクタみてえなのは」

隠「record のコンパクトコンストラクタだ。値が入る直前に検査を挟める。ここで例外が出りゃあ Bean が作れねえから、起動ごと転ぶ」

八「起動で転ぶのは困りますよ」

隠「逆だ。設定を間違えてりゃあ、客が来る前に転んでくれる方がいい。昔は客が来てから『ファイルがありません』で転んだろう。
もう一つ、秘密を扱う方を見せよう」

```yaml
  jev:
    base-url: https://api.typesafe.ai
    # API キーはリポジトリ直下の .env → コンテナの環境変数。ここに値を書かない
    api-key: ${TYPESAFE_API_KEY}
    # モデルは版をピン留めする。エイリアス（jev-latest）は更新で答えが変わる。答えた版は判定ログにも出る
    model: jev-1.14.0
```

`judgment/JevProperties.java`

```java
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
```

隠「`${TYPESAFE_API_KEY}` はプレースホルダだ。yaml に値を書かず、環境変数を差し込む。だからキーは git にも yaml にも出ねえ。
環境変数が空のまま起動すると、こう転ぶ。実物だ」

```
Failed to bind properties under 'hamon.jev' to io.github.akiraisozaki.hamon.judgment.JevProperties:

    Reason: java.lang.IllegalArgumentException: hamon.jev.api-key が空です。リポジトリ直下の .env に TYPESAFE_API_KEY を書いてコンテナをリビルドしてください

Action:

Update your application's configuration
```

八「おお、自分で書いたメッセージがそのまま出てる」

隠「コンパクトコンストラクタの例外を Spring が『束ねるのに失敗した』の中に包んで出す。どの prefix で、なぜ、どうしろ。三つ揃ってる。
使う側は `properties.model()` と呼ぶだけだ。テストじゃあ上書きもできる。(c) の `@SpringBootTest(properties = ...)` がそれだ」

---

## (e) トランザクションの段

八「保存のとこで `@Transactional` ってのが付いてますが、`setAutoCommit(false)` も `commit()` も見当たらねえ」

`graph/GraphStore.java`

```java
@Component
public class GraphStore {

	private final GraphMapper mapper;

	GraphStore(GraphMapper mapper) {
		this.mapper = mapper;
	}

	/** 保存済みのグラフを全部消して graph に置き換える。1 トランザクションで行う */
	@Transactional
	public void replaceWith(DependencyGraph graph) {
		mapper.deleteAllEdges();
		mapper.deleteAllNodes();
		graph.nodes().forEach(mapper::insertNode);
		Map<String, Long> idOf = mapper.selectAllNodes().stream().collect(Collectors.toMap(Node::name, Node::id));
		for (DependencyEdge edge : graph.edges()) {
			mapper.insertEdge(idOf.get(edge.dependent()), idOf.get(edge.dependency()), edge.probability());
		}
	}
}
```

隠「これは影武者の仕掛けでな。Spring は `@Transactional` の付いたクラスをそのまま Bean にしねえ。
そっくりの影武者、プロキシってやつを一枚かぶせて Bean にする。`Analysis` が受け取ってる `GraphStore` は実は影武者だ」

八「影武者が何をするんで」

隠「`replaceWith` と呼ばれると、影武者が先に出て『トランザクション開始』と唱え、本物を呼び、無事なら commit、
`RuntimeException` が飛んで出てきたら rollback。だから辺だけ消えてノードが残る、なんて中途半端にはならねえ」

八「便利なもんだ。じゃあ中から `this.replaceWith()` と呼んでも」

隠「それは効かねえ。影武者は外から来た客しか相手にしねえ。同じ屋敷の中で呼び合っても本物同士だから、影武者は知らん顔だ。
ここは `Analysis` から呼んでるから、ちゃんと効いてる。インターフェースがなくなった今は、影武者は `GraphStore` を**継承した**クラスとして作られる。だから `final` にできねえ」

---

## (f) 契約書どおりの Controller の段

八「API の URL やらパラメータやら、Controller にゃあ書いてねえんです。なのに動く」

隠「契約書が先にあるからだ。`openapi.yaml`」

```yaml
  /nodes:
    get:
      tags: [nodes]
      operationId: searchNodes
      summary: 名前の一部でノードを探す（変更点の入力補完用）
      parameters:
        - name: q
          in: query
          required: true
          description: 名前に含まれる文字列（大文字小文字を区別しない）
          schema:
            type: string
            minLength: 1
          example: cancel
        - name: limit
          in: query
          description: 返す件数の上限
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
```

隠「この契約書を `./gradlew` に読ませると、代書屋がインターフェースを書いてくれる。`build/generated/openapi` にできる `NodesApi.java` だ。手で直すなよ、代書屋が書き直すから」

```java
    ResponseEntity<List<NodeSummary>> searchNodes(
        @NotNull @Size(min = 1)  @Valid @RequestParam(value = "q", required = true) String q,
        @Min(value = 1) @Max(value = 100)  @Valid @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    );
```

八「`minLength: 1` が `@Size(min = 1)` に、`default: 20` が `defaultValue` に。契約書の文言がそのまま注釈になってますね」

隠「そうだ。おめえが書くのは実装だけ」

`graph/NodeSearchController.java`

```java
@RestController
@RequestMapping("/api")
class NodeSearchController implements NodesApi {

	private final GraphMapper mapper;

	NodeSearchController(GraphMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public ResponseEntity<List<NodeSummary>> searchNodes(String q, Integer limit) {
		return ResponseEntity.ok(mapper.searchNodes(q, limit).stream().map(Node::toSummary).toList());
	}
}
```

隠「Spring MVC はインターフェースの注釈も読む。だから実装にゃ `@Override` だけ。クラスの `/api` と、インターフェースの `/nodes` が合わさって `/api/nodes` になる。
`@RestController` は『返したもんはそのまま JSON にして出せ』の印だ」

八「影響調査の方も同じ作りで」

`impact/ImpactController.java`

```java
@RestController
@RequestMapping("/api") // openapi.yaml の servers.url。生成コードには付かないのでここで付ける
class ImpactController implements ImpactApi {

	private final ImpactSurvey survey;

	ImpactController(ImpactSurvey survey) {
		this.survey = survey;
	}

	@Override
	public ResponseEntity<List<ImpactItem>> surveyImpact(String target, Double minProbability) {
		return ResponseEntity.ok(survey.surveyImpact(target, minProbability));
	}
}
```

八「こっちは Mapper じゃなくて `ImpactSurvey` てえのに頼んでますね」

`impact/ImpactSurvey.java`

```java
@Component
public class ImpactSurvey {

	private final ImpactMapper mapper;

	ImpactSurvey(ImpactMapper mapper) {
		this.mapper = mapper;
	}

	public List<ImpactItem> surveyImpact(String target, double minProbability) {
		return mapper.traceUpstream(target, minProbability).stream().map(ImpactRow::toItem).toList();
	}
}
```

隠「影響調査は REST からも MCP からも呼ばれる。両方に同じ三行を書くより、一か所に置いて二人に使わせる。
Controller は HTTP の受け答えだけ、`ImpactSurvey` が調査の本体。ノード検索は呼び手が一人だから、Controller が直に Mapper を使ってる。要るときだけ層を足す」

代書屋の支度は `build.gradle`。

```groovy
openApiGenerate {
	generatorName = 'spring'
	inputSpec = "$rootDir/openapi.yaml"
	configOptions = [
		interfaceOnly        : 'true',   // インターフェースだけ生成し、実装は自分で書く
		skipDefaultInterface : 'true',   // 未実装メソッドの既定実装（501 を返す）を作らない
		useTags              : 'true',   // tag 名からインターフェース名を決める（impact → ImpactApi）
	]
}
sourceSets.main.java.srcDir "$buildDir/generated/openapi/src/main/java"
tasks.named('compileJava') { dependsOn 'openApiGenerate' }
```

隠「`skipDefaultInterface` が利いててな。契約書に API を足して実装を忘れると、コンパイルで止まる。
`ImpactItem` に必須の `kind` を足したときも、生成コンストラクタの引数が増えて `ImpactRow#toItem` がコンパイルで落ちた。直す場所を番頭に教えられる方が、客に見つけられるよりいい」

---

## (g) 四百番を一か所で出す段

八「`minProbability` に 1.5 を入れたら、最初は五百番が返ってきたんです。『サーバーの不手際』って。あたしの入力が悪いのに」

隠「それがこいつで直ってる」

`InvalidParameterAdvice.java`

```java
@RestControllerAdvice
class InvalidParameterAdvice {

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail invalidParameter(ConstraintViolationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
```

隠「順を言うとな。契約書のインターフェースにゃクラス全体に `@Validated` が付いてる。すると Spring はまた影武者を立てて、呼ばれるたびに引数を検査する。
1.5 は `@DecimalMax("1")` に逆らうから、影武者が `ConstraintViolationException` を投げる」

八「で、それが五百番に」

隠「この例外は Bean Validation の生まれで、Spring MVC の身内じゃねえ。だから MVC は『知らねえ例外だ、サーバーの不手際にしとこう』で五百番だ。
そこで `@RestControllerAdvice`。こいつは全部の Controller に横から口を出せる例外係だ。『その例外はあたしが引き受ける、四百番に書き換えておく』と」

補足: Spring 6.1 以降、MVC 自身にもパラメータ検証が組み込まれていて、そちらは最初から 400 になる。
ただしクラスに `@Validated` が付いていると、MVC は自分の検証をやめて影武者（AOP のメソッド検証）に任せる。
生成コードが `@Validated` を付けてくるので、この Advice が要る。

八「`ProblemDetail` ってのは」

隠「エラーの形の決まりごと、RFC 9457 だ。Spring が JSON にしてくれる。Controller ごとに try/catch を書かねえで、店全体でエラーの形が揃う。
ノード検索の `q` を空にしても同じ係が四百番にする。Controller 三人、検証コードは零行だ。テストもある」

```java
	@Test
	void 確率の範囲外を指定すると400になる() {
		assertThat(survey("x", 1.5)).hasStatus(HttpStatus.BAD_REQUEST);
	}
```

```java
	@Test
	void 検索語が空なら400になる() {
		assertThat(mvc.get().uri("/api/nodes").param("q", "").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
	}
```

---

## (h) 職人は一人になった段

八「前は DB の読み書きが MyBatis と Spring Data JDBC の二通りあって、比べてるって話でしたね」

隠「比べて、決めた。ADR-0003 だ。残ったのは MyBatis の職人一人」

`impact/ImpactMapper.java`

```java
@Mapper
interface ImpactMapper {

	/** 変更点から影響が広がる先を、確からしさの高い順に返す。同じノードに複数の経路で届く場合は最も確からしい経路だけ。 */
	List<ImpactRow> traceUpstream(@Param("target") String target, @Param("minProbability") double minProbability);
}
```

`src/main/resources/io/github/akiraisozaki/hamon/impact/ImpactMapper.xml`（SQL 本体は大詰めで）

```xml
<mapper namespace="io.github.akiraisozaki.hamon.impact.ImpactMapper">
  <select id="traceUpstream" resultType="io.github.akiraisozaki.hamon.impact.ImpactRow">
    WITH ripple (node_id, probability, node_path) AS (
        ...
         WHERE changed.name = #{target}
           AND e.probability &gt;= #{minProbability}
    ...
  </select>
</mapper>
```

隠「XML の `namespace` がインターフェースの名前、`<select id>` がメソッド名、`#{target}` が `@Param("target")`。この三つで結び付く。
`@Mapper` を見つけて実装を作るのは starter の仕事で、おめえは `SqlSession` を触らねえ」

八「結果を record に詰めるのは」

```yaml
mybatis:
  configuration:
    # SELECT の列名 node_path を Java の nodePath に対応させる
    map-underscore-to-camel-case: true
    # 結果行を record のコンストラクタ引数名で組み立てる（引数名は Boot の Gradle プラグインが -parameters で残す）
    arg-name-based-constructor-auto-mapping: true
```

`impact/ImpactRow.java`

```java
record ImpactRow(String node, io.github.akiraisozaki.hamon.graph.NodeKind kind, double probability, String nodePath) {

	/** ImpactMapper.xml の CHR(10) と揃える */
	private static final String PATH_SEPARATOR = "\n";

	ImpactItem toItem() {
		return new ImpactItem(node, NodeKind.fromValue(kind.name()), probability, List.of(nodePath.split(PATH_SEPARATOR)));
	}
}
```

八「`NodeKind` が二つ出てきますぜ。長い名前の方と短い方」

隠「長い `graph.NodeKind` は DB の `kind` 列を受ける自前の enum。短い方は代書屋が契約書から作った `api.model.NodeKind`。
同じ六つの値だが、生まれが違う。DB の都合と契約の都合を一つの型に混ぜねえで、境目の `toItem` で `fromValue(kind.name())` と名前で渡す。
`Node#toSummary` も同じことをやってる」

八「書き込みと検索の方は」

`graph/GraphMapper.xml`

```xml
  <delete id="deleteAllEdges">DELETE FROM edge</delete>
  <delete id="deleteAllNodes">DELETE FROM node</delete>

  <insert id="insertNode">
    INSERT INTO node (name, kind) VALUES (#{name}, #{kind})
  </insert>

  <!-- 短い名前ほど入力に近いはずなので先に出す。LIKE のワイルドカード（% _）はそのまま使えてよい（検索が広がるだけ） -->
  <select id="searchNodes" resultType="io.github.akiraisozaki.hamon.graph.Node">
    SELECT id, name, kind
      FROM node
     WHERE UPPER(name) LIKE '%' || UPPER(#{q}) || '%'
     ORDER BY LENGTH(name), name
     FETCH FIRST #{limit} ROWS ONLY
  </select>
```

隠「`FETCH FIRST #{limit} ROWS ONLY` が契約書の `limit` だ。並び順も SQL に書いてある。
MyBatis の良さはここでな、**SQL が SQL のまま見える**。hamon は SQL 文から表・列への辺を張る道具だから、自分の SQL が XML に素のまま置いてあるのが一番都合がいい。
Phase 4 で解析対象にしたのは、まさにこの `GraphMapper.xml` と `ImpactMapper.xml` だ」

八「Spring Data JDBC が負けた理由は」

隠「三つだ。肝心の再帰 SQL はどっちも生 SQL で書くしかなく、Repository の自動実装が効いたのは insert と delete の四本だけ。
Oracle の引用符つき識別子のせいで `NamingStrategy` の回避コードが要った。
で、両方を維持すると DB の変更が全部二回になる。学びたいこと（Repository の自動実装、`@Profile`、`@Configuration` + `@Bean`）は Phase 2b で一度体験したから、十分だ。詳しくは ADR-0003」

八「表と列を読む方も Mapper で」

`analysis/SchemaMapper.xml`

```xml
  <!-- 接続ユーザーが持つ表と列。解析対象システムと同じ DB に繋いでいる前提（ADR-0004） -->
  <select id="selectColumns" resultType="io.github.akiraisozaki.hamon.analysis.TableColumn">
    SELECT table_name, column_name
      FROM user_tab_columns
     ORDER BY table_name, column_id
  </select>
```

```java
/** SchemaMapper.selectColumns の 1 行（Oracle の USER_TAB_COLUMNS）。DB との境界の形 */
record TableColumn(String tableName, String columnName) {
}
```

隠「Oracle の辞書を読むのも、同じ職人の同じ手口だ。`user_tab_columns` を読めば、繋いでるユーザーの表と列が全部出る。それが TABLE と COLUMN のノードになる」

---

## (i) Flyway の段

八「表を作る SQL は `V1__graph_tables.sql`、`V2__sample_graph.sql`、`V3__remove_sample_graph.sql` と三つありますが、V3 の中身がこれで」

```sql
-- V2 のサンプルグラフは解析結果（POST /api/analysis）で置き換わるので消す。
DELETE FROM edge;
DELETE FROM node;
```

八「V2 で入れて V3 で消すなんて、V2 を消しちまえばいいじゃねえですか」

隠「それをやると Flyway が怒る。あいつは適用した SQL の指紋（チェックサム）を `flyway_schema_history` に取ってある。
V2 を書き換えると『指紋が違う、誰だおまえは』で起動を止める。だから一度流した V ファイルは触らねえ、直したきゃ次の番号を足す」

```sql
CREATE TABLE node (
    id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(500) NOT NULL,
    kind VARCHAR2(20)  NOT NULL CHECK (kind IN ('METHOD', 'CLASS', 'FILE', 'TABLE', 'COLUMN', 'CONFIG')),
    CONSTRAINT node_name_unique UNIQUE (name)
);

CREATE TABLE edge (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dependent_id  NUMBER NOT NULL REFERENCES node (id),
    dependency_id NUMBER NOT NULL REFERENCES node (id),
    probability   NUMBER(5, 4) NOT NULL CHECK (probability BETWEEN 0 AND 1),
    CONSTRAINT edge_unique UNIQUE (dependent_id, dependency_id)
);
```

```java
public enum NodeKind {
	METHOD, CLASS, FILE, TABLE, COLUMN, CONFIG
}
```

隠「`CHECK (kind IN ...)` と Java の `NodeKind` と契約書の `NodeKind` が同じ六つ。どれか一つだけ増やすと残りが怒る。揃える場所が三つになったのは、フロントに種類を見せるようにしたからだ」

---

## (j) テストの段

八「テストがまた、注釈だらけで」

`impact/ImpactApiTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/sample-graph.sql")
class ImpactApiTest {

	private static final String ORDER_SERVICE_CANCEL = "com.example.order.OrderService#cancel";

	@Autowired
	private MockMvcTester mvc;
```

隠「四つを一つずつ言おう」

| 注釈 | 役割 |
|---|---|
| `@SpringBootTest` | 店ごと丸ごと開ける。Oracle にも本当に繋ぐ |
| `@AutoConfigureMockMvc` | 表の戸は開けずに、裏口から客の振りをして MVC を通す道具 `MockMvcTester` を出す |
| `@Transactional` | テスト一本ごとに風呂敷で包んで、終わったら全部巻き戻す |
| `@Sql` | テストの前にこの SQL を流す。風呂敷ごと巻き戻るから DB は汚れねえ |

八「`@Autowired` がフィールドに付いてますぜ。コンストラクタ注入が良いって言ったじゃねえですか」

隠「テストクラスは JUnit が作るもんで Spring が作るもんじゃねえ。だからフィールド注入が昔からの書き方だ。
JUnit 5 ならテストのコンストラクタで受け取ることもできる。どっちでもいいが、このリポジトリはフィールドで揃えてる」

```java
	@Test
	void 確からしさは経路上の辺の確率の積になる() {
		List<ImpactItem> items = surveyItems(ORDER_SERVICE_CANCEL, 0.5);

		ImpactItem endpoint = items.stream().filter(i -> i.getNode().equals("com.example.api.OrderCancelEndpoint#post")).findFirst().orElseThrow();
		assertThat(endpoint.getProbability()).isCloseTo(0.95 * 0.90, within(1e-9));
		assertThat(endpoint.getPath()).containsExactly(ORDER_SERVICE_CANCEL, "com.example.order.OrderFacade#cancelWithRefund", "com.example.api.OrderCancelEndpoint#post");
	}
```

隠「確率を掛け合わせるのも、一番いい経路を選ぶのも、みんな SQL の中に書いてある。だから本物の Oracle で守る。ADR-0001 の言うとおりだ」

八「解析の方のテストは、(c) で見た番頭つきのやつで」

`analysis/AnalysisApiTest.java`

```java
	@Test
	void 列を変更点にするとSQL文からコントローラまで辿れる() {
		mvc.post().uri("/api/analysis").exchange();

		impactOf("NODE.NAME").containsExactly(
				"fixture.OrderController#cancel",
				"fixture.OrderRepository#updateStatus",
				"fixture.OrderService#cancel",
				"fixture.Invoker#run");
	}
```

隠「`properties =` で `source-roots` も `mapper-roots` もフィクスチャの小さな長屋に向け、番頭が目利きを偽物に替え、その上で解析から影響調査まで一本で通す。
フィクスチャの `OrderMapper.xml` が `UPDATE node SET kind = ... WHERE name = ...` と書いてあるから、列 `NODE.NAME` を変更点にすると、SQL 文 → Repository → Service → Controller と辿れる。
表と列は**本物の Oracle の辞書**から来てる。だから `node` 表がある hamon 自身の DB でこのテストは動く」

八「もう一本、気味の悪いのがありました。`MockMvcTester` を使ってねえやつ」

`mcp/HamonToolsTest.java`

```java
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
```

隠「MCP は裏口から通せねえ。本物の MCP クライアントが HTTP で繋いでくるのを確かめたいからだ。
だから `RANDOM_PORT` で**表の戸を本当に開ける**。空いてるポートで Tomcat が立ち、その番号が `@LocalServerPort` で入ってくる」

八「風呂敷は」

隠「効かねえ。客は別のスレッドから来るから、テストのトランザクションの中にいねえ。だからこのテストは DB を変えねえツール呼び出しだけにしてある。
`analyze_sources` を呼んだら本物のグラフが書き換わる上に、Jev まで呼びに行く。やらねえ」

---

## 大詰め：再帰 SQL と目利きと取次

隠「最後に hamon の肝だ。石を投げて波紋が広がる、その SQL」

`ImpactMapper.xml`

```sql
WITH ripple (node_id, probability, node_path) AS (
    SELECT e.dependent_id,
           e.probability,
           changed.name || CHR(10) || dependent.name
      FROM node changed
      JOIN edge e         ON e.dependency_id = changed.id
      JOIN node dependent ON dependent.id = e.dependent_id
     WHERE changed.name = #{target}
       AND e.probability >= #{minProbability}
    UNION ALL
    SELECT e.dependent_id,
           r.probability * e.probability,
           r.node_path || CHR(10) || dependent.name
      FROM ripple r
      JOIN edge e         ON e.dependency_id = r.node_id
      JOIN node dependent ON dependent.id = e.dependent_id
     WHERE r.probability * e.probability >= #{minProbability}
)
CYCLE node_id SET is_cycle TO 'Y' DEFAULT 'N'
SELECT n.name AS node, n.kind, best.probability, best.node_path
  FROM (SELECT r.*,
               ROW_NUMBER() OVER (PARTITION BY r.node_id ORDER BY r.probability DESC) AS path_rank
          FROM ripple r
         WHERE r.is_cycle = 'N') best
  JOIN node n ON n.id = best.node_id
 WHERE best.path_rank = 1
 ORDER BY best.probability DESC, n.name
```

隠「`UNION ALL` の上が一段目の波、下が二段目から先の波。段を越えるたびに確率を掛ける。弱くなった波は閾値で消える。
`CYCLE` で同じ石の上を二度通らねえ。`ROW_NUMBER` で、同じ場所に二つの波が届いたら強い方だけ残す。
経路は `CHR(10)` で改行つなぎにして、Java 側の `PATH_SEPARATOR = "\n"` で切る。二か所で揃える値だから両方にコメントがある。
最後の `n.kind` が、フロントに形を変えさせてる種類だ」

八「Java の方の、ソースを読む側は」

`analysis/JavaSourceAnalysis.java`

```java
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
```

隠「呼び先の型が分かって、しかも解析の中にいるなら `DependencyEdge.certain`、確からしさ 1 の確実な辺。JDK や他人の蔵へ行く呼び出しは辺にしねえ。
型が分からねえなら、同じ名前のメソッド全部を候補にして、呼び出し元のコードと候補の宣言を `Ambiguity` に詰めて目利きに聞く。
返ってきた確からしさがそのまま辺の値だ。確実な辺は `put`、あいまいな辺は `putIfAbsent` だから、ぶつかったら確実な方が勝つ」

八「前は 0.5 を適当に入れてましたよね」

隠「Phase 5 で目利きがついた。Jev という、問いに確率で答える System One モデルだ。候補ごとに『この呼び出しが呼んでるのはこいつか』と聞いて、0 から 1 の数で返ってくる。
このリポジトリ自身を解析したときの実物がこれだ」

```
Jev jev-1.14.0 が io.github.akiraisozaki.hamon.impact.ImpactMapper#traceUpstream を判定: {NODE.ID=0.92, EDGE.ID=0.13}
```

隠「`traceUpstream` の SQL に `id` という列名が出てくるが、`node` 表にも `edge` 表にも `id` がある。どっちだ、と聞いたら『node が 0.92、edge が 0.13』。
SQL を読めば `n.id = best.node_id` だから node の方が本命で、答えは合ってる。
解析全部でこの一件しか聞いてねえ。あとは全部確実な辺だ。67 ノード、69 辺（2026-11-01 実測）」

目利きの Spring 側の仕掛け（`RestClient`、`@Retryable`、`@EnableResilientMethods`）と MCP の取次は長くなるので演目 5 `05-jev-and-mcp.md` に回す。ここでは入口の形だけ。

`mcp/HamonTools.java`

```java
@Component
class HamonTools {

	@McpTool(name = "survey_impact", description = "変更点（クラス#メソッド、TABLE、TABLE.COLUMN）を変えたときに影響を受ける候補を、確からしさの高い順に返す。"
			+ "probability は経路上の辺の確からしさの積、path は変更点から候補までのノード列")
	List<ImpactItem> surveyImpact(
			@McpToolParam(description = "変更点のノード名。例: io.github.akiraisozaki.hamon.impact.ImpactRow#toItem, EDGE.PROBABILITY", required = true) String target,
			@McpToolParam(description = "この確からしさ未満の候補は返さない（漏れの許容度、0〜1）。省略時 0.05", required = false) Double minProbability) {
```

隠「`@McpTool` を付けた Bean のメソッドが、そのまま Claude Code から見えるツールになる。`@RestController` の MVC 版と思えばいい。
説明文は人間じゃなくて Claude が読む。だから引数の例まで書いてある」

テスト用フィクスチャ `src/test/resources/analysis-fixture/fixture/Invoker.java`

```java
class Invoker {
	void run() {
		Unknown.cancel(); // Unknown はどこにも無いので型解決できない → 同名メソッド全部へのあいまいな辺
	}
}
```

隠「この `Invoker` は、どこにもねえ `Unknown` を呼ぶ。だから `cancel` という名のメソッド全部が候補になって目利きに回る。テストでは偽の目利きが 0.5 を返す」

DB に届く前に不正を止める値オブジェクト。`graph/DependencyGraph.java`

```java
public record DependencyGraph(Map<String, NodeKind> nodes, List<DependencyEdge> edges) {

	public DependencyGraph {
		nodes = Map.copyOf(nodes);
		edges = List.copyOf(edges);
		for (DependencyEdge edge : edges) {
			if (!nodes.containsKey(edge.dependent()) || !nodes.containsKey(edge.dependency())) {
				throw new IllegalArgumentException("辺の端がノードにない: " + edge);
			}
		}
	}

	/** other のノードと辺を足した新しいグラフ。同名のノードは other の種類が勝つ */
	public DependencyGraph merge(DependencyGraph other) {
		Map<String, NodeKind> mergedNodes = new LinkedHashMap<>(nodes);
		mergedNodes.putAll(other.nodes);
		List<DependencyEdge> mergedEdges = new ArrayList<>(edges);
		mergedEdges.addAll(other.edges);
		return new DependencyGraph(mergedNodes, mergedEdges);
	}
}
```

隠「`merge` は自分を書き換えねえ。Java のグラフと XML のグラフを足した**新しい**グラフを返す。record は不変だから、足し算も新品で返す」

八「フロントの方は」

`frontend/src/api/client.ts`

```ts
export type ImpactItem = components['schemas']['ImpactItem']
export type NodeKind = components['schemas']['NodeKind']
export type NodeSummary = components['schemas']['NodeSummary']

export const api = createClient<paths>({ baseUrl: '/api' })
```

`frontend/src/App.tsx`

```ts
    const { data } = await api.GET('/nodes', { params: { query: { q } } })
```

```ts
    const { data, error, response } = await api.GET('/impact', { params: { query: { target, minProbability } } })
```

隠「同じ `openapi.yaml` から `npm run api` で型を作る。だからパラメータの名前を打ち間違えると TypeScript が先に止める。
サーバーの `ImpactApi` / `NodesApi` とフロントの `paths` が同じ契約書から出てる。契約書は一枚、これがスキーマファーストの実体だ。
`kind` を足したときも、フロントは型を作り直すだけで `item.kind` が使えるようになった」

---

## サゲ

八「ご隠居、よく分かりました。石を投げりゃあ波紋が広がる、その波紋を確率で追いかける。それが hamon ってわけですね」

隠「そうだ」

八「で、Spring ってのは、要するに女房と大家と番頭と影武者と代書屋を、一人で勤めてるんですね」

隠「まあ、そういうことだ。今は目利きと取次も抱えてる」

八「そんな働き者がいるのに、あたしは何を書けばいいんです」

隠「おめえの書く仕事はひとつだけだ。『このメソッドを変えたら、どこに響くか』」

八「へえ、それを調べる道具がこの hamon」

隠「違う。おめえがこの hamon を直したら、どこに響くか。それを hamon に聞くんだ」

八「自分で自分を調べるんですか」

隠「だから Phase 3 から『このリポジトリ自身を解析する』になってる。Phase 4 では自分の XML を、Phase 5 では自分の SQL の `id` が node か edge かを目利きに聞いた」

八「……石を投げた池に、自分が立ってるようなもんですね」

隠「波紋が、一番よく見えるだろう」

お後がよろしいようで。

---

## 登場人物の対応表（復習用）

| 落語の役 | Spring の実体 | hamon での例 |
|---|---|---|
| 大家 | `@SpringBootApplication`（コンポーネントスキャン + 自動構成） | `HamonApplication` |
| 女房・味噌汁 | コンテナ・Bean（DI） | コンストラクタ引数で受け取る全部 |
| 番頭 | `@TestConfiguration` + `@Bean` + `@Primary`（本番コードには無い） | `AnalysisApiTest.FixedJudgeConfiguration` |
| 看板 | `@Profile` | 削除済み（ADR-0003）。歴史は `02`〜`04` に残る |
| 影武者 | AOP プロキシ | `@Transactional`、`@Validated`、`@Retryable` |
| 代書屋 | openapi-generator | `ImpactApi`、`AnalysisApi`、`NodesApi` |
| 例外係 | `@RestControllerAdvice` | `InvalidParameterAdvice` |
| 職人 | MyBatis（一人） | `ImpactMapper`、`GraphMapper`、`SchemaMapper` |
| 目利き | Jev（`RestClient` + `@Retryable`） | `JevDependencyJudge`、偽物は `FixedDependencyJudge` |
| 取次 | MCP サーバー（Spring AI、`@McpTool`） | `HamonTools` |
| 指紋を取る役人 | Flyway | `V1`〜`V3` |
| 風呂敷 | テストの `@Transactional` | `ImpactApiTest`、`AnalysisApiTest` |
