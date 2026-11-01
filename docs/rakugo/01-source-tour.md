# hamon 一席、ソース巡り

> 対象: Phase 3 完了時点（2026-11-01）のソース。パッケージは `io.github.akiraisozaki.hamon` を省略して書く。

## 枕：石を投げれば波紋が立つ

えー、毎度バカバカしいお笑いを一席。昔から申しますな、「石を投げれば波紋が立つ」と。
これがプログラムの世界となりますと、メソッドをひとつ直せば、どこまで響くか分からねえ。
これを調べるのが「影響調査」。で、この調査をやる道具に「hamon」と名をつけた男がおりまして。

まず全体の流れ。hamon のバックエンドには API が 2 本しかない。この 2 本を追えば全ソースを一周できる。

**POST /api/analysis（解析してグラフを作り直す）**

1. `AnalysisController` が受ける。生成された `AnalysisApi` インターフェースの実装
2. `AnalysisProperties` から解析対象ディレクトリ一覧を取る（application.yml の `hamon.analysis.source-roots`）
3. `JavaSourceAnalysis` が JavaParser でソースを読み、メソッドをノード、呼び出しを辺にした `DependencyGraph` を返す。ここは Spring に依存しない純粋な計算
4. `GraphStore` が Oracle の表を全部消して書き直す。実装は MyBatis 版と Spring Data JDBC 版があり、プロファイルで片方だけが選ばれる
5. ノード数と辺の数を `AnalysisResult` で返す

**GET /api/impact?target=…&minProbability=…（影響を調べる）**

1. `ImpactController` が受ける。パラメータ検証は生成インターフェースの注釈に任せる
2. `ImpactGraph` に問い合わせる。これも MyBatis 版か Spring Data JDBC 版
3. どちらの版も同じ再帰 SQL を Oracle に投げ、辺を逆向きに辿りながら確率を掛け合わせる
4. 結果 1 行が `ImpactRow`。DB 都合の形なので `toItem` で API の形 `ImpactItem` に変換する
5. フロントは openapi-fetch で受け取り、Cytoscape で波紋として描く

パッケージの役割は、`impact` が読み取り、`graph` がグラフの型と保存、`analysis` が解析。

---

## (a) 自動構成の段

八「ご隠居、ご隠居！ Spring Boot ってのを始めたんですが、こりゃあ何にも書いてねえのに動くんですよ。気味が悪くて」

隠「何を言ってやがる。見せてみろ」

`HamonApplication.java`

```java
@SpringBootApplication
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
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

八「大家ってのは店賃を取るもんでしょう」

隠「店賃は `build.gradle` で先に払ってある」

```groovy
implementation 'org.springframework.boot:spring-boot-starter-jdbc'          // DataSource, JdbcTemplate, TransactionManager
implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0'  // SqlSessionFactory, @Mapper スキャン
implementation 'org.springframework.boot:spring-boot-starter-flyway'        // 起動時に db/migration を適用
implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'     // Repository インターフェースの実装生成
implementation 'org.springframework.boot:spring-boot-starter-webmvc'        // 組み込み Tomcat, DispatcherServlet, JSON 変換
implementation 'org.springframework.boot:spring-boot-starter-validation'    // @Valid / @Min を実際に検証する
```

隠「この starter てえのを一行足すと、大家が『ああ、JDBC が来たな、じゃあ DataSource を用意しとこう』と、言わねえでも全部支度する。これを自動構成と言う」

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

`analysis/AnalysisController.java`

```java
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {

	private final AnalysisProperties properties;
	private final JavaSourceAnalysis analysis;
	private final GraphStore store;

	AnalysisController(AnalysisProperties properties, JavaSourceAnalysis analysis, GraphStore store) {
		this.properties = properties;
		this.analysis = analysis;
		this.store = store;
	}
```

隠「それが DI、依存性注入だ。おめえ、朝起きて味噌汁が出てくるだろう。おめえが味噌屋に行って、豆腐屋に行って、火を起こして、とやらねえ。
『味噌汁をくれ』と手を出しときゃあ、女房が出す。Spring の中じゃあ女房がコンテナで、味噌汁が Bean だ」

Bean とはコンテナが作って持っている部品のこと。この 3 つの引数は、それぞれ別の方法で Bean になっている。

| 引数の型 | Bean になる仕組み |
|---|---|
| `AnalysisProperties` | `@ConfigurationProperties` を `@ConfigurationPropertiesScan` が拾う |
| `JavaSourceAnalysis` | クラスに `@Component` が付いている |
| `GraphStore` | インターフェース。実装クラス `MyBatisGraphStore` か `DataJdbcGraphStore` のうち、プロファイルで生きている方が渡る |

コンストラクタがひとつなら `@Autowired` は書かない。

八「`GraphStore` ってインターフェースでしょう。インターフェースが来るんですか」

隠「そこが味噌汁の良いところだ。おめえは『味噌汁』と言うだけで、今日が赤味噌か白味噌か知らねえで飲んでる。
Controller も、裏が MyBatis か Spring Data JDBC か知らねえで使ってる」

八「じゃあ Spring がねえと何も作れねえんですか」

隠「いや、普通のクラスだ。見ろ、テストじゃ `new` してる」

`analysis/JavaSourceAnalysisTest.java`

```java
class JavaSourceAnalysisTest {
	private static DependencyGraph analyzeFixture() {
		return new JavaSourceAnalysis().analyze(List.of(Path.of("src/test/resources/analysis-fixture")));
	}
```

隠「コンストラクタで受け取る作りにしとくと、女房がいなくても自分で味噌汁が作れる。これがコンストラクタ注入のご利益だ」

---

## (c) プロファイルで実装を替える段

八「ご隠居、裏が二つあるって言いましたね。どっちが来るか、誰が決めてるんです」

隠「番頭がいる。`ImpactGraphConfiguration` だ」

`impact/ImpactGraph.java`

```java
@FunctionalInterface
interface ImpactGraph {
	List<ImpactRow> traceUpstream(String target, double minProbability);
}
```

`impact/ImpactGraphConfiguration.java`

```java
@Configuration
class ImpactGraphConfiguration {

	@Bean
	@Profile("mybatis")
	ImpactGraph myBatisImpactGraph(ImpactMapper mapper) {
		return mapper::traceUpstream;
	}

	@Bean
	@Profile("datajdbc")
	ImpactGraph dataJdbcImpactGraph(ImpactRepository repository) {
		return repository::traceUpstream;
	}
}
```

隠「`@Configuration` は『ここは番頭部屋だ』の印。中の `@Bean` メソッドは、返したもんが Bean になる（メソッド名が Bean 名）。
引数の `ImpactMapper` はそこでも女房が渡してくる」

八「同じ `ImpactGraph` を二つ返してますぜ。喧嘩になりませんか」

隠「なる。二つとも生きてりゃあ『どっちだか分からねえ』で起動から転ぶ。だから `@Profile` だ。
`mybatis` の看板が出てる日は上だけ、`datajdbc` の看板の日は下だけ。看板の既定はここに書いてある」

```yaml
spring:
  profiles:
    default: mybatis
```

隠「替えたきゃ起動のときに `--spring.profiles.active=datajdbc` と言えばいい。店の看板を昼と夜で掛け替えるようなもんだ」

八「`mapper::traceUpstream` てえのは何です。クラスがねえ」

隠「`ImpactGraph` が `@FunctionalInterface` で、メソッドが一つしかねえ。`ImpactMapper` にも同じ形のメソッドがある。
だからメソッド参照一本で済む。間に取り持ちのクラスを書かねえ。これぞ怠け者の美学だ」

八「書き込みの方も番頭がやってるんで」

隠「いや、そっちは自分で看板を背負ってる」

```java
@Component
@Profile("mybatis")
class MyBatisGraphStore implements GraphStore {
```

```java
@Component
@Profile("datajdbc")
class DataJdbcGraphStore implements GraphStore {
```

隠「自分で書いたクラスなら、クラスに直接 `@Component` と `@Profile` を付けりゃあいい。
番頭を通すのは、ラムダで済むときや、他人の作ったクラスを Bean にするときだ。この hamon にゃあ両方あるから見比べられる」

---

## (d) 設定値を束ねる段

八「設定ファイルの値を読むのに、昔は `getProperty` を山ほど書きましたよ。あちこちで null かどうか見て」

隠「今はこうだ」

```yaml
hamon:
  analysis:
    source-roots:
      - src/main/java
```

`analysis/AnalysisProperties.java`

```java
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
```

隠「yaml の `source-roots` が record の `sourceRoots` に入る。ハイフンをキャメルに、文字列を `Path` に、一つでも並びでもリストに。この面倒を全部 Spring がやる」

八「真ん中の、引数のねえコンストラクタみてえなのは」

隠「record のコンパクトコンストラクタだ。値が入る直前に検査を挟める。ここで例外が出りゃあ Bean が作れねえから、起動ごと転ぶ」

八「起動で転ぶのは困りますよ」

隠「逆だ。設定を間違えてりゃあ、客が来る前に転んでくれる方がいい。昔は客が来てから『ファイルがありません』で転んだろう。
使う側は `properties.sourceRoots()` と呼ぶだけだ。テストじゃあ上書きもできる」

```java
@SpringBootTest(properties = "hamon.analysis.source-roots=src/test/resources/analysis-fixture")
```

---

## (e) トランザクションの段

八「保存のとこで `@Transactional` ってのが付いてますが、`setAutoCommit(false)` も `commit()` も見当たらねえ」

`graph/MyBatisGraphStore.java`

```java
	@Override
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
```

隠「これは影武者の仕掛けでな。Spring は `@Transactional` の付いたクラスをそのまま Bean にしねえ。
そっくりの影武者、プロキシってやつを一枚かぶせて Bean にする。Controller が受け取ってる `GraphStore` は実は影武者だ」

八「影武者が何をするんで」

隠「`replaceWith` と呼ばれると、影武者が先に出て『トランザクション開始』と唱え、本物を呼び、無事なら commit、
`RuntimeException` が飛んで出てきたら rollback。だから辺だけ消えてノードが残る、なんて中途半端にはならねえ」

八「便利なもんだ。じゃあ中から `this.replaceWith()` と呼んでも」

隠「それは効かねえ。影武者は外から来た客しか相手にしねえ。同じ屋敷の中で呼び合っても本物同士だから、影武者は知らん顔だ。
ここは Controller から呼んでるから、ちゃんと効いてる」

---

## (f) 契約書どおりの Controller の段

八「API の URL やらパラメータやら、Controller にゃあ書いてねえんです。なのに動く」

隠「契約書が先にあるからだ。`openapi.yaml`」

```yaml
servers:
  - url: /api
paths:
  /impact:
    get:
      tags: [impact]
      operationId: surveyImpact
      parameters:
        - name: target
          in: query
          required: true
          schema:
            type: string
            minLength: 1
        - name: minProbability
          in: query
          schema:
            type: number
            format: double
            minimum: 0
            maximum: 1
            default: 0.05
```

隠「この契約書を `./gradlew` に読ませると、代書屋がインターフェースを書いてくれる。`build/generated/openapi` にできる `ImpactApi.java` だ。手で直すなよ、代書屋が書き直すから」

```java
@Validated
public interface ImpactApi {

    @RequestMapping(
        method = RequestMethod.GET,
        value = ImpactApi.PATH_SURVEY_IMPACT,   // "/impact"
        produces = { "application/json" }
    )
    ResponseEntity<List<ImpactItem>> surveyImpact(
        @NotNull @Size(min = 1)  @Valid @RequestParam(value = "target", required = true) String target,
        @DecimalMin(value = "0") @DecimalMax(value = "1")  @Valid @RequestParam(value = "minProbability", required = false, defaultValue = "0.05") Double minProbability
    );
}
```

八「`minimum: 0` が `@DecimalMin("0")` に、`default: 0.05` が `defaultValue` に。契約書の文言がそのまま注釈になってますね」

隠「そうだ。おめえが書くのは実装だけ」

`impact/ImpactController.java`

```java
@RestController
@RequestMapping("/api") // openapi.yaml の servers.url。生成コードには付かないのでここで付ける
class ImpactController implements ImpactApi {

	private final ImpactGraph graph;

	ImpactController(ImpactGraph graph) {
		this.graph = graph;
	}

	@Override
	public ResponseEntity<List<ImpactItem>> surveyImpact(String target, Double minProbability) {
		return ResponseEntity.ok(graph.traceUpstream(target, minProbability).stream().map(ImpactRow::toItem).toList());
	}
}
```

隠「Spring MVC はインターフェースの注釈も読む。だから実装にゃ `@Override` だけ。クラスの `/api` と、インターフェースの `/impact` が合わさって `/api/impact` になる。
`@RestController` は『返したもんはそのまま JSON にして出せ』の印だ」

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

隠「`skipDefaultInterface` が利いててな。契約書に API を足して実装を忘れると、コンパイルで止まる。忘れ物を客に見つけられるより、番頭に見つけられる方がいい」

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

隠「エラーの形の決まりごと、RFC 9457 だ。Spring が JSON にしてくれる。Controller ごとに try/catch を書かねえで、店全体でエラーの形が揃う。テストもある」

```java
	@Test
	void 確率の範囲外を指定すると400になる() {
		assertThat(survey("x", 1.5)).hasStatus(HttpStatus.BAD_REQUEST);
	}
```

---

## (h) 二人の職人、MyBatis と Spring Data JDBC の段

八「DB の読み書きが二通りあるのは、どっちが良いか比べてるんですってね」

隠「ADR-0001 だ。まず MyBatis の職人」

`impact/ImpactMapper.java`

```java
@Mapper
interface ImpactMapper {
	List<ImpactRow> traceUpstream(@Param("target") String target, @Param("minProbability") double minProbability);
}
```

`src/main/resources/io/github/akiraisozaki/hamon/impact/ImpactMapper.xml`（SQL 本体は後述）

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
    map-underscore-to-camel-case: true           # node_path → nodePath
    arg-name-based-constructor-auto-mapping: true # record のコンストラクタ引数名で組み立てる
```

`impact/ImpactRow.java`

```java
record ImpactRow(String node, double probability, String nodePath) {
	private static final String PATH_SEPARATOR = "\n";

	ImpactItem toItem() {
		return new ImpactItem(node, probability, List.of(nodePath.split(PATH_SEPARATOR)));
	}
}
```

隠「列の `node_path` が引数の `nodePath` に名前で入る。で、もう一人の職人、Spring Data JDBC」

`impact/ImpactRepository.java`

```java
interface ImpactRepository extends Repository<Node, Long> {

	@Query("""
			WITH ripple (node_id, probability, node_path) AS (
			    ...
			     WHERE changed.name = :target
			       AND e.probability >= :minProbability
			...
			""")
	List<ImpactRow> traceUpstream(String target, double minProbability);
}
```

隠「`Repository` を継ぐだけで実装は Spring Data が実行時に作る。XML はなし、SQL は `@Query` に直に、変数は `:target`」

八「書き込みの方は」

`graph/NodeRepository.java`

```java
interface NodeRepository extends CrudRepository<Node, Long> {
}
```

八「空っぽだ!」

隠「空っぽで `save` も `saveAll` も `deleteAll` も付いてくる。表との対応は record の方に書く」

`graph/Node.java`

```java
@Table("NODE") // Oracle は引用符付き識別子を区別するので大文字（DataJdbcConfiguration も参照）
public record Node(@Id Long id, String name, NodeKind kind) {
}
```

`graph/DataJdbcGraphStore.java`

```java
	@Override
	@Transactional
	public void replaceWith(DependencyGraph graph) {
		edges.deleteAll();
		nodes.deleteAll();
		List<Node> unsaved = graph.nodes().entrySet().stream().map(e -> new Node(null, e.getKey(), e.getValue())).toList();
		Map<String, Long> idOf = new HashMap<>();
		nodes.saveAll(unsaved).forEach(saved -> idOf.put(saved.name(), saved.id())); // save は id の入った新しい Node を返す
		edges.saveAll(graph.edges().stream()
				.map(edge -> new Edge(null, idOf.get(edge.dependent()), idOf.get(edge.dependency()), edge.probability()))
				.toList());
	}
```

隠「MyBatis の職人は insert したあと `selectAllNodes` で id を引き直す。こっちは `saveAll` の戻りから id を取る。record は不変だから、save は id の入った新しいやつを返すわけだ。
SQL の本数も、MyBatis は `GraphMapper.xml` に四本、こっちは零本」

八「じゃあ Spring Data JDBC の方が楽じゃねえですか」

隠「楽には楽の罠がある」

`graph/DataJdbcConfiguration.java`

```java
@Configuration
@Profile("datajdbc")
class DataJdbcConfiguration {

	@Bean
	NamingStrategy upperCaseNamingStrategy() {
		return new DefaultNamingStrategy() {
			@Override
			public String getTableName(Class<?> type) {
				return super.getTableName(type).toUpperCase();
			}

			@Override
			public String getColumnName(RelationalPersistentProperty property) {
				return super.getColumnName(property).toUpperCase();
			}
		};
	}
}
```

隠「Spring Data JDBC は列名を `"dependent_id"` と引用符つきで出す。Oracle は引用符つきだと大文字小文字をやかましく区別する。
表は大文字で作ってあるから、小文字で引っ張っても『そんな列はねえ』と」

八「で、どう直したんで」

隠「`NamingStrategy` という型の Bean を自分で一つ置いた。すると Spring Data は既定のやつをやめて、こいつを使う。
『同じ型の Bean を置いて既定を差し替える』。これは Spring の常套手段だから覚えとけ」

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

隠「`CHECK (kind IN ...)` と Java の `NodeKind` が同じ六つ。片方だけ増やすとどっちかが怒る。コメントに『揃える』と書いてあるのはそのためだ」

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

八「で、これが一番気味が悪い。中身が空なんです」

`impact/ImpactApiDataJdbcTest.java`

```java
@ActiveProfiles("datajdbc")
class ImpactApiDataJdbcTest extends ImpactApiTest {
}
```

隠「三行で看板を `datajdbc` に掛け替える。すると (c) の番頭が Data JDBC の方の Bean を出す。親のテストが全部、もう一人の職人で走る。
『同じ統合テストが両方で通る』の実体はこの三行だ」

八「三行で二倍のテスト。これぞ怠け者の美学」

---

## 大詰め：再帰 SQL と JavaParser

隠「最後に hamon の肝だ。石を投げて波紋が広がる、その SQL」

`ImpactMapper.xml`

```sql
WITH ripple (node_id, probability, node_path) AS (
    -- 1 段目: target に直接依存している辺
    SELECT e.dependent_id,
           e.probability,
           changed.name || CHR(10) || dependent.name
      FROM node changed
      JOIN edge e         ON e.dependency_id = changed.id
      JOIN node dependent ON dependent.id = e.dependent_id
     WHERE changed.name = #{target}
       AND e.probability >= #{minProbability}
    UNION ALL
    -- 2 段目以降: 前の段の結果からさらに 1 段。確率を掛け、閾値未満は打ち切る
    SELECT e.dependent_id,
           r.probability * e.probability,
           r.node_path || CHR(10) || dependent.name
      FROM ripple r
      JOIN edge e         ON e.dependency_id = r.node_id
      JOIN node dependent ON dependent.id = e.dependent_id
     WHERE r.probability * e.probability >= #{minProbability}
)
CYCLE node_id SET is_cycle TO 'Y' DEFAULT 'N'
SELECT n.name AS node, best.probability, best.node_path
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
経路は `CHR(10)` で改行つなぎにして、Java 側の `PATH_SEPARATOR = "\n"` で切る。二か所で揃える値だから両方にコメントがある」

八「Java の方の、ソースを読む側は」

`analysis/JavaSourceAnalysis.java`

```java
	private static void addEdges(Map<String, DependencyEdge> edges, Map<String, NodeKind> nodes, String dependent,
			Optional<String> resolvedDependency, String calledName) {
		if (resolvedDependency.isPresent()) {
			String dependency = resolvedDependency.get();
			if (nodes.containsKey(dependency)) {
				edges.put(dependent + " -> " + dependency, new DependencyEdge(dependent, dependency, 1.0));
			}
			return;
		}
		nodes.keySet().stream()
				.filter(node -> node.endsWith("#" + calledName))
				.forEach(dependency -> edges.putIfAbsent(dependent + " -> " + dependency,
						new DependencyEdge(dependent, dependency, AMBIGUOUS_PROBABILITY)));
	}
```

隠「呼び先の型が分かって、しかも解析の中にいるなら確からしさ 1 の確実な辺。JDK や他人の蔵へ行く呼び出しは辺にしねえ。
型が分からねえなら、同じ名前のメソッド全部へ 0.5 のあいまいな辺を張る。確実な辺は `put`、あいまいな辺は `putIfAbsent` だから、ぶつかったら確実な方が勝つ」

八「0.5 ってのは適当じゃねえですか」

隠「適当だ。だから `shortcut:` と書いてある。Phase 5 で Jev という目利きに一本ずつ値をつけさせる。それまでの仮の値だ」

テスト用フィクスチャ `src/test/resources/analysis-fixture/fixture/Invoker.java`

```java
class Invoker {
	void run() {
		Unknown.cancel(); // Unknown はどこにも無いので型解決できない → 同名メソッド全部へのあいまいな辺
	}
}
```

隠「この `Invoker` は、どこにもねえ `Unknown` を呼ぶ。だから `cancel` という名のメソッド全部へ 0.5 の辺が張られる。テストで固めてある」

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
}
```

八「フロントの方は」

`frontend/src/api/client.ts`

```ts
export type ImpactItem = components['schemas']['ImpactItem']
export const api = createClient<paths>({ baseUrl: '/api' })
```

`frontend/src/App.tsx`

```ts
const { data, error, response } = await api.GET('/impact', { params: { query: { target, minProbability } } })
```

隠「同じ `openapi.yaml` から `npm run api` で型を作る。だからパラメータの名前を打ち間違えると TypeScript が先に止める。
サーバーの `ImpactApi` とフロントの `paths` が同じ契約書から出てる。契約書は一枚、これがスキーマファーストの実体だ」

---

## サゲ

八「ご隠居、よく分かりました。石を投げりゃあ波紋が広がる、その波紋を確率で追いかける。それが hamon ってわけですね」

隠「そうだ」

八「で、Spring ってのは、要するに女房と大家と番頭と影武者と代書屋を、一人で勤めてるんですね」

隠「まあ、そういうことだ」

八「そんな働き者がいるのに、あたしは何を書けばいいんです」

隠「おめえの書く仕事はひとつだけだ。『このメソッドを変えたら、どこに響くか』」

八「へえ、それを調べる道具がこの hamon」

隠「違う。おめえがこの hamon を直したら、どこに響くか。それを hamon に聞くんだ」

八「自分で自分を調べるんですか」

隠「だから Phase 3 は『このリポジトリ自身を解析する』になってる」

八「……石を投げた池に、自分が立ってるようなもんですね」

隠「波紋が、一番よく見えるだろう」

お後がよろしいようで。

---

## 登場人物の対応表（復習用）

| 落語の役 | Spring の実体 | hamon での例 |
|---|---|---|
| 大家 | `@SpringBootApplication`（コンポーネントスキャン + 自動構成） | `HamonApplication` |
| 女房・味噌汁 | コンテナ・Bean（DI） | コンストラクタ引数で受け取る全部 |
| 番頭 | `@Configuration` + `@Bean` | `ImpactGraphConfiguration`、`DataJdbcConfiguration` |
| 看板 | `@Profile` | `mybatis` / `datajdbc` |
| 影武者 | AOP プロキシ | `@Transactional`、`@Validated` |
| 代書屋 | openapi-generator | `ImpactApi`、`AnalysisApi` |
| 例外係 | `@RestControllerAdvice` | `InvalidParameterAdvice` |
| 二人の職人 | MyBatis / Spring Data JDBC | `ImpactMapper` / `ImpactRepository` |
| 指紋を取る役人 | Flyway | `V1`〜`V3` |
| 風呂敷 | テストの `@Transactional` | `ImpactApiTest` |
