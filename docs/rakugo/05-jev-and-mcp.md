# 目利きと取次（Jev と MCP）

> 対象: Phase 6 完了＋フロント断面合わせ後（2026-11-01）のソース。パッケージは `io.github.akiraisozaki.hamon` を省略して書く。

## 枕：Phase 3 のあとに起きたこと

八「ご隠居、しばらく顔を出さねえうちに、hamon の長屋がだいぶ建て替わったんですってね」

隠「建て替わった。順に言うとこうだ」

- **Phase 2c**（ADR-0003）。二人いた職人を MyBatis 一人に決めた。Spring Data JDBC 版、`@Profile` の看板、`ImpactGraph` と `GraphStore` のインターフェースを全部消した。`GraphStore` は今は具象クラス一枚だ
- **Phase 4**（ADR-0004）。Oracle の辞書から表と列をノードにして、MyBatis の XML に書いてある SQL から表・列へ辺を張った。`NODE.NAME` を変更点にすると Controller まで辿れる
- **Phase 5**（ADR-0005）。あいまいな辺の確からしさを、仮の 0.5 じゃなくて **Jev** という目利きに一本ずつ値をつけさせる
- **Phase 6**（ADR-0006）。同じ調査を **MCP** で Claude Code に取り次ぐ。レポートは Claude Code が書く
- **フロントの断面合わせ**。候補にノードの種類が付き、変更点の入力に補完が出る

八「四つも五つも。Spring の新顔は何人出ました」

隠「Phase 4 はほとんど Spring じゃねえ。JavaParser と XML の DOM と、識別子の文字列一致だ。Spring の目で見るところは一つだけ」

`analysis/Analysis.java`

```java
	/** 作り直したグラフを返す */
	public DependencyGraph run() {
		DatabaseSchema schema = DatabaseSchema.of(schemaMapper.selectColumns());
		DependencyGraph graph = javaAnalysis.analyze(properties.sourceRoots())
				.merge(mapperAnalysis.analyze(properties.mapperRoots(), schema));
		store.replaceWith(graph);
		return graph;
	}
```

`src/main/resources/io/github/akiraisozaki/hamon/analysis/SchemaMapper.xml`

```xml
  <select id="selectColumns" resultType="io.github.akiraisozaki.hamon.analysis.TableColumn">
    SELECT table_name, column_name
      FROM user_tab_columns
     ORDER BY table_name, column_id
  </select>
```

隠「`Analysis#run` が三つの解析（Java、XML、辞書）を `merge` して保存する。Oracle の辞書も、ただの表として MyBatis で読むだけだ。
`SchemaMapper` も `@Mapper` のインターフェースで、実装は蔵が作る。前に覚えたとおりで、新しいことはねえ」

八「じゃあ新顔は Phase 5 と 6 で」

隠「そうだ。今日は**目利き**と**取次**の話だ」

---

## 一席目：目利きの口入れ（たった一枚のインターフェース）

八「2c でインターフェースを全部消したって言いましたよね。じゃあ長屋にインターフェースは一枚もねえんで」

隠「一枚だけ残した。いや、新しく一枚だけ立てた」

`judgment/DependencyJudge.java`

```java
/**
 * あいまいな辺の確からしさを判定する。本番は Jev（管理外の外部 API）。
 * テストでは固定値を返す実装に差し替える
 */
@FunctionalInterface
public interface DependencyJudge {

	/** ambiguity の候補それぞれについて「dependent が本当にそれを使っている確率」。キーは候補の dependency ノード名 */
	Map<String, Double> judge(Ambiguity ambiguity);
}
```

八「`GraphStore` のインターフェースは消して、こっちは立てる。何が違うんです」

隠「**差し替える相手が本当にいるかどうか**だ。`GraphStore` の裏は自分の Oracle で、テストでも本物を使える。差し替える理由がねえ。
Jev は他人の店の API だ。テストのたびに他人の店に電話をかけてたら、金もかかるし、向こうが休みの日はテストが落ちる。
だからここだけ『手はインターフェースで出し、名乗りは実装クラスで』の形を残した。実装が二つ**必要**なときにだけインターフェースを立てる。これが 2c の教訓だ」

隠「本番の名乗りはこいつ」

`judgment/JevDependencyJudge.java`

```java
@Component
class JevDependencyJudge implements DependencyJudge {
```

隠「で、テストの名乗りはこいつ。`src/test` の側にいる」

`judgment/FixedDependencyJudge.java`（テスト）

```java
/** テスト用。全候補に同じ確からしさを返し、何を聞かれたかを覚えておく（Jev は管理外の外部 API なので本物は使わない） */
public final class FixedDependencyJudge implements DependencyJudge {

	private final double probability;
	private final List<Ambiguity> asked = new ArrayList<>();

	public FixedDependencyJudge(double probability) {
		this.probability = probability;
	}

	@Override
	public Map<String, Double> judge(Ambiguity ambiguity) {
		asked.add(ambiguity);
		return ambiguity.candidates().stream().collect(Collectors.toMap(Ambiguity.Candidate::dependency, candidate -> probability, (a, b) -> a, LinkedHashMap::new));
	}
```

八「こっちには `@Component` がねえ」

隠「ねえ。だから大家は見つけねえ。`JavaSourceAnalysisTest` みてえに Spring なしで `new JavaSourceAnalysis(judge)` する分には、それでいい。
だが `AnalysisApiTest` は長屋を丸ごと建てる。そっちで本物の `JevDependencyJudge` の代わりに立てるには、口入れがいる」

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

隠「三つ注釈がある。一つずつ」

- `@TestConfiguration` は**テストの中にだけ建つ番頭部屋**。昔の `ImpactGraphConfiguration` と同じ `@Configuration` の仲間だが、本番の大家には見えねえ。テストクラスの内側に書くと、そのテストの長屋にだけ足される
- `@Bean` は前に覚えたとおり。返したもんが Bean になる。これで `DependencyJudge` 型の Bean が**二人**になる。本物の `JevDependencyJudge` と、この `fixedJudge`
- `@Primary` が**口入れ**だ。二人いて喧嘩になるところを、『手が出たらこっちを渡せ』と一人を立てる

八「`@Profile` の看板と何が違うんです。あれも一人に絞る道具でしたよね」

隠「絞り方が違う。看板は『今日は片方しか長屋にいねえ』。口入れは『二人いるが、こっちが先』。
本物の `JevDependencyJudge` はテストの長屋にもちゃんといる。`JevProperties` も束ねられる。だから設定が壊れてりゃテストでも転ぶ。
いるけど呼ばれねえ。看板で消すより、本物の支度までは毎回通してやる方が、壊れたときに早く気づく」

八「じゃあ hamon の番頭部屋は今」

隠「本番コードには**ゼロ**だ。番頭はテストに引っ越した。`@Configuration` + `@Bean` の出番は『他人のクラスを Bean にする』か『テストで差し替える』か。自分のクラスなら注釈を直接付けりゃいい、というのは変わってねえ」

---

## 二席目：設定の束と、環境変数の差し込み

八「Jev は他人の店でしょう。鍵はどこに書いてあるんです。コードに書いたら丸見えだ」

隠「書いてねえ。前に見た `AnalysisProperties` と同じ作りがもう一つある」

`judgment/JevProperties.java`

```java
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
```

`application.yml`

```yaml
hamon:
  jev:
    base-url: https://api.typesafe.ai
    # API キーはリポジトリ直下の .env → コンテナの環境変数。ここに値を書かない
    api-key: ${TYPESAFE_API_KEY}
    # モデルは版をピン留めする。エイリアス（jev-latest）は更新で答えが変わる。答えた版は判定ログにも出る
    model: jev-1.14.0
```

隠「`${TYPESAFE_API_KEY}` が**プレースホルダ**だ。Spring は yaml を読むときにこの形を見つけると、環境変数やシステムプロパティから同じ名前の値を探して差し込む。
値は `.env` にあって、Dev Container が環境変数にして渡してる。yaml にも Java にも、鍵の値は一文字も出てこねえ。ログにも出さねえ」

八「昔は `System.getenv` をあちこちで呼んで、null なら既定値で、とやってましたよ」

隠「それが yaml の一行と、record のコンパクトコンストラクタに集まった。で、鍵を忘れるとどうなるか。今日、環境変数を空にして本当に起動してみた」

```
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'hamon.jev' to io.github.akiraisozaki.hamon.judgment.JevProperties:

    Reason: java.lang.IllegalArgumentException: hamon.jev.api-key が空です。リポジトリ直下の .env に TYPESAFE_API_KEY を書いてコンテナをリビルドしてください

Action:

Update your application's configuration
```

八「あたしが書いた例外の文が、そのまま `Reason` に出てる」

隠「そうだ。束ねるときにコンパクトコンストラクタが投げた例外を、Boot が『設定の束に失敗した』の形に包んで見せる。
客が来る前に、しかも直し方まで書いて転ぶ。前に『設定を間違えてりゃあ客が来る前に転んでくれる方がいい』と言ったのは、この実物のことだ」

---

## 三席目：外へ電話を掛ける（RestClient）

八「で、目利きに聞くのはどうやるんで。昔は `HttpURLConnection` を開いて、JSON を文字列で手で組んで、返ってきた文字列をまた手でほぐして」

隠「今はこうだ」

`judgment/JevDependencyJudge.java`

```java
	private final RestClient client;
	private final String model;

	JevDependencyJudge(JevProperties properties) {
		this.client = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
				.build();
		this.model = properties.model();
	}
```

```java
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
```

隠「`RestClient` は Spring の電話機だ。`builder()` で店の住所（`baseUrl`）と毎回付ける名乗り（`Authorization` ヘッダ）を仕込んで、一台組む。
掛けるときは `post().uri().body().retrieve().body(型)`。`body(request)` に record を渡せば Jackson が JSON にして送り、`body(Response.class)` と言えば返ってきた JSON を record に詰めて返す」

八「JSON の手書きがねえ」

隠「ねえ。手紙の形は record で決めてある」

`judgment/JevRequest.java`

```java
record JevRequest(String model, Map<String, String> state, Map<String, Question> questions) {

	/** Noul 質問。instructions は質問文と候補の説明を 1 つのオブジェクトにしたもの（構造化 instructions） */
	record Question(String type, Map<String, String> instructions) {
	}

	/** 応答。answers のキーは質問 ID。usage など他のフィールドは読まない */
	record Response(String model, Map<String, Answer> answers) {
	}

	record Answer(double noul) {
	}
```

隠「`of` が `Ambiguity` をこの形に組み、`probabilities` が答えを候補の名前に戻す。往復の変換がこの一枚に閉じてる。
返ってくる JSON に `usage` やら知らねえ項目があっても、record にねえ項目は Jackson が読み飛ばす設定になってる」

八「この `RestClient` は Bean なんですか。手を出せば来るんで」

隠「hamon では Bean じゃねえ。`JevDependencyJudge` がコンストラクタの中で自分で組んでる。役の中で使う**物**だ。
使うのがこの一か所で、鍵もここでしか要らねえからな。Bean にして回す理由がまだねえ」

隠「それと、質問の文言は全部 `Ambiguity` に置いてある。英語だ」

`judgment/Ambiguity.java`

```java
		return new Ambiguity(caller,
				Map.of("caller", caller, "caller_source", callerSource, "call", call),
				"Is this the method that `call` in `caller_source` invokes?",
				candidates);
```

隠「Jev は英語が主で、漢字仮名は精度が落ちる。それと、質問の文言は人が一番いじりたくなるところだから、一か所に集めとく。あちこちの文字列を直し回るのは昔の `getProperty` と同じ病気だ」

---

## 四席目：転んでも三度まで（@Retryable）

八「他人の店だと、話し中もあるでしょう。429 とか 503 とか」

隠「ある。だから `judge` にはこれが付いてる」

```java
	@Override
	@Retryable(includes = { HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
			maxRetries = 3, delay = 1000, multiplier = 2)
	public Map<String, Double> judge(Ambiguity ambiguity) {
```

`HamonApplication.java`

```java
@SpringBootApplication
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
@EnableResilientMethods // @Retryable を有効にする（Jev 呼び出しの再試行）
public class HamonApplication {
```

隠「`@Retryable` は『この例外で転んだら、一秒待って掛け直せ。次は二秒、その次は四秒。三度までだ』。
サーバーの不手際（5xx）、話し中（429）、線が繋がらねえ（`ResourceAccessException`）の三つだけ掛け直す。400 みてえな**こっちの不手際**は掛け直しても無駄だから含めてねえ」

八「`@EnableResilientMethods` ってのは」

隠「大家に『再試行の影武者も用意しろ』と言う印だ。これがねえと `@Retryable` は飾りで終わる。
`org.springframework.resilience` は Spring Framework 7 で本体に入った新しい部屋でな。昔は Spring Retry という別の蔵を借りてたのが、Boot 4 からは持ち込みなしで済む」

八「影武者ってことは」

隠「そうだ。`@Transactional` と同じ作りだ。だから三つの『だから』もそのまま効く。`final` にしねえ、中から `this.judge()` と呼ばねえ、自分で `new JevDependencyJudge(...)` しねえ。
掛け直すのは影武者で、本物の `judge` は一度掛けることしか知らねえ。業務の中に `for (int i = 0; i < 3; i++)` が混ざらねえ」

八「三度掛けても出なかったら」

隠「例外で上に上がる。**答えにはしねえ**。ここが肝でな。『呼べなかった』を『0 だった』と読ませたら、使ってる辺が消えて影響調査が漏れる。
失敗は失敗の顔で出す。これは ADR-0005 で決めた」

隠「で、実際に目利きが何と言ったか。このリポジトリ自身を解析すると、あいまいな辺は一件だけ出る。今日のログだ」

```
Jev jev-1.14.0 が io.github.akiraisozaki.hamon.impact.ImpactMapper#traceUpstream を判定: {NODE.ID=0.92, EDGE.ID=0.13}
```

八「`id` ですか」

隠「`traceUpstream` の SQL に `id` という列が出てくる。`node` にも `edge` にも `id` がある。識別子の一致だけじゃ、どっちの `id` か決められねえ。
目利きに SQL ごと見せて『この SQL はこの列を読み書きしてるか』と候補ごとに聞いたら、`NODE.ID` が 0.92、`EDGE.ID` が 0.13。SQL を読めば `n.id` と `changed.id` ばかりで、`e.id` は使ってねえ。目利きの言うとおりだ」

八「前に聞いたときは 0.93 と 0.12 じゃありませんでしたか」

隠「僅かに揺れる。同じ版でもそのくらいは揺れるし、版が上がれば答え方そのものが変わる。だから `model: jev-1.14.0` と版をピン留めして、ログにも『どの版が答えたか』を残す。`jev-latest` と書いときゃ楽だが、ある朝から辺の太さが変わってて理由が分からねえ、になる」

---

## 五席目：取次（MCP サーバー）

八「六で Claude Code から使えるようにしたって話ですが、また HTTP の API を一本足したんで」

隠「API はもう足してねえ。**取次**を一人置いただけだ。まず店賃から」

`build.gradle`

```groovy
	// Spring AI のバージョンを BOM で揃える（MCP サーバー、ADR-0006）
	implementation platform('org.springframework.ai:spring-ai-bom:2.0.2')
	...
	// Claude Code から impact / analysis を MCP ツールとして呼べるようにする（Streamable HTTP、ADR-0006）
	implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
```

隠「`platform(...)` が **BOM**、蔵の帳簿だ。Spring AI は部品が何十もあって、版がばらけると動かねえ。帳簿を一冊渡しとけば『この帳簿に載ってる部品はこの版で』と Gradle が揃える。
だから下の starter には版が書いてねえ。Boot の starter に版を書かねえのと同じ仕組みで、Boot のプラグインが Boot の帳簿を勝手に渡してるからだ」

`application.yml`

```yaml
spring:
  ai:
    mcp:
      server:
        # Claude Code 向けの MCP サーバー（ADR-0006）。@McpTool を付けた Bean のメソッドがツールになる。
        # 入口は POST /mcp（spring.ai.mcp.server.streamable-http.mcp-endpoint の既定）。認証は無いので localhost 限定
        protocol: STREAMABLE
        name: hamon
        version: 0.1.0
        instructions: Java + Oracle のシステムの確率つき影響調査。survey_impact で変更点から影響候補を確からしさ順に得る
```

隠「で、取次本人」

`mcp/HamonTools.java`

```java
@Component
class HamonTools {

	/** openapi.yaml の minProbability の既定値と揃える */
	private static final double DEFAULT_MIN_PROBABILITY = 0.05;

	private final ImpactSurvey survey;
	private final Analysis analysis;

	HamonTools(ImpactSurvey survey, Analysis analysis) {
		this.survey = survey;
		this.analysis = analysis;
	}

	@McpTool(name = "survey_impact", description = "変更点（クラス#メソッド、TABLE、TABLE.COLUMN）を変えたときに影響を受ける候補を、確からしさの高い順に返す。"
			+ "probability は経路上の辺の確からしさの積、path は変更点から候補までのノード列")
	List<ImpactItem> surveyImpact(
			@McpToolParam(description = "変更点のノード名。例: io.github.akiraisozaki.hamon.impact.ImpactRow#toItem, EDGE.PROBABILITY", required = true) String target,
			@McpToolParam(description = "この確からしさ未満の候補は返さない（漏れの許容度、0〜1）。省略時 0.05", required = false) Double minProbability) {
		if (target == null || target.isBlank()) {
			throw new IllegalArgumentException("target を指定してください");
		}
		double threshold = minProbability == null ? DEFAULT_MIN_PROBABILITY : minProbability; // 省略可の引数は MCP から null で来る
		if (threshold < 0 || threshold > 1) {
			throw new IllegalArgumentException("minProbability は 0〜1: " + threshold);
		}
		return survey.surveyImpact(target, threshold);
	}
```

八「`@RestController` とそっくりだ」

隠「発想が同じだからな。`@Component` の Bean のメソッドに `@McpTool` を付けると、starter がそれを拾って『このサーバーにはこういうツールがある』と名簿にする。
Controller の客はブラウザや curl、こっちの客は Claude Code。`description` は人に読ませる文じゃなくて、モデルが『いつこのツールを使うか』を決めるための文だ。だから長い」

八「中で呼んでるのは `ImpactSurvey` と `Analysis`」

隠「そこが取次の値打ちだ。REST の `ImpactController` も、同じ `ImpactSurvey#surveyImpact` を呼んでる。裏側が一つだから、REST で聞いても MCP で聞いても同じ答えが返る。
Phase 6 で Controller から `ImpactSurvey` と `Analysis` を切り出したのは、客が二人になったからだ。客が一人のうちは切らねえ」

八「検証は。REST の方は契約書の注釈に任せてましたよね」

隠「MCP には契約書の代書屋がいねえ。だから `HamonTools` の中で自分で見る。外から来た値を受け取ったその場で検証する、は昔から変わらねえ作法だ。
投げた `RuntimeException` は、starter が**ツールのエラー結果**に包んでモデルに返す。モデルは『閾値が変だ』と読んで、自分で直して掛け直す。テストもある」

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

```java
	@Test
	void 範囲外の閾値はツールのエラー結果になる() {
		try (McpSyncClient client = connect()) {
			CallToolResult result = client.callTool(new CallToolRequest("survey_impact", Map.of("target", "x", "minProbability", 1.5)));

			assertThat(result.isError()).isTrue();
			assertThat(text(result)).contains("minProbability");
		}
	}
```

八「`MockMvcTester` じゃねえ」

隠「MCP は HTTP の上で `initialize` から始まる手順を踏む。裏口の `MockMvcTester` じゃその手順が通らねえ。
だから `webEnvironment = RANDOM_PORT` で**本当に Tomcat の戸を開けて**、空いてるポートを `@LocalServerPort` で受け取り、Claude Code と同じ MCP のクライアントで叩く。
代わりに、本当の戸を開けると**テストの `@Transactional` は効かねえ**。別のスレッドで処理されるからな。だからこのテストは DB を変えねえツール呼び出しだけにしてある」

八「Claude Code の側は」

`.mcp.json`

```json
{
  "mcpServers": {
    "hamon": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

隠「これだけだ。hamon を起こしておけば、Claude Code が `survey_impact` を自分で呼んで、結果を読んでレポートを書く。
サーバーから Claude の API を呼ぶ案もあったが、やめた。目利き（Jev）は hamon が呼び、物書き（Claude）は hamon を呼ぶ。方向を一つにしておく」

---

## 六席目：フロントの断面合わせ（契約書は一枚、の実感）

八「最後にフロントですが、これは Spring の話ですか」

隠「半分な。やったのは契約書 `openapi.yaml` に二つ足しただけだ。`ImpactItem` に `kind`、それと `GET /nodes`」

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
```

隠「`ImpactItem` に必須の `kind` を足した瞬間、生成された `ImpactItem` のコンストラクタの引数が一つ増えて、`ImpactRow#toItem` が**コンパイルで止まった**。
『yaml を変えたら、直すべき場所をコンパイラが全部教える』。スキーマファーストの値打ちは、これを一度味わうと分かる」

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

八「`NodeKind` が二つありますね。長い方と短い方」

隠「`graph.NodeKind` は hamon の中の種類、`api.model.NodeKind` は代書屋が契約書から作った種類だ。同じ六つの値だが別の型。
境界で `fromValue(kind.name())` と一度だけ変換する。中の型を契約書の型で置き換えちまうと、yaml を直すたびにドメインが揺れる。`Node#toSummary` も同じ作りだ」

隠「で、`/nodes` の方。契約書の `minLength: 1` はこうなる」

生成された `api/NodesApi.java`

```java
    ResponseEntity<List<NodeSummary>> searchNodes(
        @NotNull @Size(min = 1)  @Valid @RequestParam(value = "q", required = true) String q,
        @Min(value = 1) @Max(value = 100)  @Valid @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    );
```

`graph/NodeSearchController.java`

```java
	@Override
	public ResponseEntity<List<NodeSummary>> searchNodes(String q, Integer limit) {
		return ResponseEntity.ok(mapper.searchNodes(q, limit).stream().map(Node::toSummary).toList());
	}
```

隠「Controller は一行。空文字で呼ばれたら、前に作った `InvalidParameterAdvice` が 400 にする。検証のコードは一行も足してねえ。
Phase 1 で例外係を一人置いといたから、API が三本になっても、エラーの形は店全体で揃ったままだ」

---

## サゲ

八「ご隠居、整理します。インターフェースは差し替える相手が本当にいるときだけ。口入れは `@Primary`。鍵は環境変数を yaml に差し込む。電話は `RestClient`、転んだら影武者が三度まで掛け直す。取次は `@McpTool`、裏側は REST と同じ。契約書を変えればコンパイラが直す場所を教える」

隠「よく覚えた」

八「で、結局 Spring の新顔は、電話機と、再試行の影武者と、取次の名簿と。どれも『注釈を付けると大家が面倒を見る』の繰り返しですね」

隠「そういうことだ。覚える形は増えてねえ。名乗って、手を出して、注釈で影武者を頼む。相手が Oracle でも、他人の店でも、Claude Code でも、同じだ」

八「じゃあ目利きの Jev と、物書きの Claude Code、どっちが偉いんです」

隠「どっちも hamon の客か、hamon の下請けだ。偉いのは契約書だ」

八「契約書……あの yaml が」

隠「あれ一枚で、Java も TypeScript も、代書屋が同じことを書く。目利きも物書きも、あれに書いてある形でしか物を言えねえ」

八「じゃあ、あたしが一番大事にするのは」

隠「yaml を一行変えたときに、どこが赤くなるか、だ」

八「……それこそ影響調査じゃねえですか」

隠「だから hamon なんだ」

お後がよろしいようで。

---

## 登場人物の対応表（今回の追加分）

| 落語の役 | Spring の実体 | hamon での例 |
|---|---|---|
| 目利き | 管理外の外部 API を一枚のインターフェースで受ける | Jev / `DependencyJudge`（実装は `JevDependencyJudge` と、テストの `FixedDependencyJudge`） |
| 口入れ | `@Primary`（同じ型が二人いても優先を決める） | `AnalysisApiTest` の `@TestConfiguration` + `@Bean` の `fixedJudge` |
| 鍵の差し込み | yaml のプレースホルダ `${…}` + `@ConfigurationProperties` | `hamon.jev.api-key: ${TYPESAFE_API_KEY}` → `JevProperties` |
| 電話 | `RestClient` + Jackson（record ↔ JSON） | `JevDependencyJudge`、`JevRequest` |
| 三度まで | `@Retryable` + `@EnableResilientMethods`（Framework 7 本体の影武者） | `JevDependencyJudge#judge` |
| 取次 | MCP サーバー（Spring AI、`@McpTool` / `@McpToolParam`） | `HamonTools`、`.mcp.json` |
| 蔵の帳簿 | Gradle の `platform(...)`（BOM） | `spring-ai-bom:2.0.2` |
| 本当の戸 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` | `HamonToolsTest`（裏口 `MockMvcTester` では MCP の手順が通らない） |
| 共通の裏側 | 客が二人になったら Controller から切り出す `@Component` | `ImpactSurvey`、`Analysis`（REST と MCP が同じ答えを返す） |
