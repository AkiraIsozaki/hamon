# DI の名簿（誰が名乗り、誰が手を出し、誰が誰に渡るか）

> hamon の自前コードにある Bean 全員と注入箇所全部を、省略なしで並べる。Phase 6 完了 + フロントの断面合わせ後（2026-11-01、コミット de3ec7e）。
> 起動失敗のメッセージは、Jev の API キーを空にして実際に起動して取ったもの。
> Phase 3 時点の名簿（Spring Data JDBC 版、`@Profile`、`ImpactGraph`）は ADR-0003 で消えた。昔の名簿は git の履歴にある。

## 枕：名簿を作る

八「ご隠居、DI は分かったつもりでしたが、まだぼんやりしてる。『手を出せば来る』は分かった。
でも**誰が名乗ってて、誰が手を出してて、誰が誰に渡ってるのか**、全員分を一度見ねえと腑に落ちねえ」

隠「いい心がけだ。じゃあ名簿を作ろう。hamon の長屋の住人は、おめえが書いた分だけなら十七人だ。全員出す。省略なしだ」

八「前に数えたときは十三人でしたよ」

隠「二人の職人のうち片方に暇を出して（ADR-0003）、代わりに Jev の目利きと MCP の取次と、解析屋が二人増えた。長屋は入れ替わるもんだ」

---

## 一席目：名乗ってる者の名簿（宣言側）

隠「名乗り方は三通りある。まず**自分で看板を背負ってる**連中。十二人いる」

```java
// HamonApplication.java
@SpringBootApplication
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
@EnableResilientMethods // @Retryable を有効にする（Jev 呼び出しの再試行）
public class HamonApplication {
```

隠「大家本人。こいつも Bean だ。`@SpringBootApplication` の中に `@Configuration` が入ってるからな。
この大家の住所が `io.github.akiraisozaki.hamon` で、ここから下を全部見回る。前より看板が一枚増えてる。`@EnableResilientMethods`、あとで影武者の段で話す」

```java
// InvalidParameterAdvice.java
@RestControllerAdvice
class InvalidParameterAdvice {
```

```java
// impact/ImpactController.java
@RestController
@RequestMapping("/api") // openapi.yaml の servers.url。生成コードには付かないのでここで付ける
class ImpactController implements ImpactApi {
```

```java
// analysis/AnalysisController.java
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {
```

```java
// graph/NodeSearchController.java
@RestController
@RequestMapping("/api")
class NodeSearchController implements NodesApi {
```

```java
// impact/ImpactSurvey.java
@Component
public class ImpactSurvey {
```

```java
// analysis/Analysis.java
@Component
public class Analysis {
```

```java
// analysis/JavaSourceAnalysis.java
@Component
public class JavaSourceAnalysis {
```

```java
// analysis/MyBatisMapperAnalysis.java
@Component
public class MyBatisMapperAnalysis {
```

```java
// graph/GraphStore.java
@Component
public class GraphStore {
```

```java
// judgment/JevDependencyJudge.java
@Component
class JevDependencyJudge implements DependencyJudge {
```

```java
// mcp/HamonTools.java
@Component
class HamonTools {
```

隠「`@RestController` も `@RestControllerAdvice` も、中を剥けば `@Component` だ。だから大家は全部同じ目で見る。
『あ、看板を背負ってる、住まわせよう』。これで十二人」

八「`GraphStore` は前はインターフェースで、`MyBatisGraphStore` が看板を背負ってたでしょう」

隠「それが ADR-0003 だ。実装が一つしかねえのにインターフェースを被せるのはやめた。今は `GraphStore` が具象クラスで、自分で看板を背負ってる。
インターフェースが残ってるのは一か所だけだ。`JevDependencyJudge` が名乗ってる `DependencyJudge`。なぜそこだけ残したかは縁結びの段で」

隠「次、**設定値の塊**が二人。こいつらの名乗り方だけ違う」

```java
// analysis/AnalysisProperties.java
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots, List<Path> mapperRoots) {
```

```java
// judgment/JevProperties.java
@ConfigurationProperties("hamon.jev")
public record JevProperties(String baseUrl, String apiKey, String model) {
```

隠「クラスには `@ConfigurationProperties` しかねえ。これは『設定値の塊だ』という印で、それ自体は Bean の印じゃねえ。
大家の `@ConfigurationPropertiesScan` が『その印のやつも住まわせろ』と言ってるから Bean になる」

```yaml
hamon:
  analysis:
    source-roots:
      - src/main/java
    mapper-roots:
      - src/main/resources
  jev:
    base-url: https://api.typesafe.ai
    api-key: ${TYPESAFE_API_KEY}
    model: jev-1.14.0
```

八「`${TYPESAFE_API_KEY}` てえのは」

隠「環境変数の中身をここに差し込め、という印だ。キーの値は yaml にも git にも出てこねえ。`.env` からコンテナの環境変数に渡って、起動のときに初めて `apiKey` に入る。
値がねえと `JevProperties` のコンストラクタで転ぶ。それが起動の段で見せる実物だ」

隠「最後、**蔵（starter）が代わりに名乗らせる**連中。こいつらはインターフェースで、実装クラスはどこにもねえ」

```java
// impact/ImpactMapper.java
@Mapper
interface ImpactMapper {
```

```java
// graph/GraphMapper.java
@Mapper
interface GraphMapper {
```

```java
// analysis/SchemaMapper.java
@Mapper
interface SchemaMapper {
```

隠「`@Mapper` の三人は MyBatis の starter が見つけて、XML の SQL を実行する実装をその場で作って Bean にする。
`SchemaMapper` は Phase 4 で増えた。Oracle の辞書ビュー `user_tab_columns` から表と列を読む」

八「前にいた `Repository` の三人は」

隠「暇を出した。Spring Data の starter ごと `build.gradle` から消えてる。『毎日長屋にいるが声をかけられねえ職人』は、もういねえ」

八「じゃあ名簿は」

| # | Bean | 名乗り方 | 増えた時期 |
|---|---|---|---|
| 1 | `HamonApplication` | `@SpringBootApplication` | Phase 0 |
| 2 | `InvalidParameterAdvice` | `@RestControllerAdvice` | Phase 1 |
| 3 | `ImpactController` | `@RestController` | Phase 1 |
| 4 | `AnalysisController` | `@RestController` | Phase 3 |
| 5 | `NodeSearchController` | `@RestController` | フロント断面合わせ |
| 6 | `ImpactSurvey` | `@Component` | Phase 6（REST と MCP で共有するため） |
| 7 | `Analysis` | `@Component` | Phase 6（同上） |
| 8 | `JavaSourceAnalysis` | `@Component` | Phase 3 |
| 9 | `MyBatisMapperAnalysis` | `@Component` | Phase 4 |
| 10 | `GraphStore` | `@Component`（具象クラス） | Phase 2c で `MyBatisGraphStore` を改名 |
| 11 | `JevDependencyJudge` | `@Component`、`DependencyJudge` を実装 | Phase 5 |
| 12 | `HamonTools` | `@Component`、中に `@McpTool` | Phase 6 |
| 13 | `AnalysisProperties` | `@ConfigurationProperties` + 大家の `@ConfigurationPropertiesScan` | Phase 3 |
| 14 | `JevProperties` | 同上 | Phase 5 |
| 15 | `ImpactMapper` | `@Mapper`（MyBatis starter） | Phase 2 |
| 16 | `GraphMapper` | `@Mapper`（MyBatis starter） | Phase 2b |
| 17 | `SchemaMapper` | `@Mapper`（MyBatis starter） | Phase 4 |

隠「`@Configuration` と `@Bean` と `@Profile` は、プロダクションコードには**一つもねえ**。番頭部屋は空き家だ」

八「番頭がいねえ長屋なんてあるんですか」

隠「自分で書いたクラスばかりなら、クラスに看板を付ければ済む。番頭が要るのは、ラムダや他人のクラスを Bean にするときと、**同じ型の Bean を差し替えるとき**だ。
後の方は一か所だけある。テストの中だ。六席目でやる」

隠「で、名簿に**いねえ**連中も言っとく。`ImpactRow`、`DependencyGraph`、`DependencyEdge`、`Node`、`NodeKind`、`TableColumn`、`DatabaseSchema`、`Ambiguity`、`JevRequest`。
印がねえ。だから Bean じゃねえ。物だ。`new` される。`Node` に付いてた `@Table` も Spring Data と一緒に消えた」

---

## 二席目：手を出してる者の名簿（利用側）

八「名乗りは分かりました。じゃあ手を出してる方を」

隠「手の出し方は hamon では一種類しかねえ。**コンストラクタの引数**だ。全部で十か所。これも省略なしで出す」

```java
// 手その一
ImpactController(ImpactSurvey survey) {
	this.survey = survey;
}
```

```java
// 手その二
AnalysisController(Analysis analysis) {
	this.analysis = analysis;
}
```

```java
// 手その三
NodeSearchController(GraphMapper mapper) {
	this.mapper = mapper;
}
```

```java
// 手その四
ImpactSurvey(ImpactMapper mapper) {
	this.mapper = mapper;
}
```

```java
// 手その五。長屋で一番手の多い男
Analysis(AnalysisProperties properties, JavaSourceAnalysis javaAnalysis, MyBatisMapperAnalysis mapperAnalysis,
		SchemaMapper schemaMapper, GraphStore store) {
	this.properties = properties;
	this.javaAnalysis = javaAnalysis;
	this.mapperAnalysis = mapperAnalysis;
	this.schemaMapper = schemaMapper;
	this.store = store;
}
```

```java
// 手その六
public JavaSourceAnalysis(DependencyJudge judge) {
	this.judge = judge;
}
```

```java
// 手その七
public MyBatisMapperAnalysis(DependencyJudge judge) {
	this.judge = judge;
}
```

```java
// 手その八
GraphStore(GraphMapper mapper) {
	this.mapper = mapper;
}
```

```java
// 手その九。受け取った設定値で RestClient を自分で組む
JevDependencyJudge(JevProperties properties) {
	this.client = RestClient.builder()
			.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
			.build();
	this.model = properties.model();
}
```

```java
// 手その十
HamonTools(ImpactSurvey survey, Analysis analysis) {
	this.survey = survey;
	this.analysis = analysis;
}
```

八「`@Autowired` がどこにもねえ」

隠「コンストラクタが一つしかねえクラスは、Spring が『このコンストラクタで作るしかねえ』と分かるから、書かなくていい。
二つ以上あるときだけ `@Autowired` でどれか教える。hamon は全部一つだ」

八「六番と七番だけ `public` が付いてますね」

隠「テストが `new JavaSourceAnalysis(judge)` と自分で作るからだ。パッケージが違えば `public` が要る。Spring のためじゃねえ、テストのためだ。
Bean は `new` できなくなるわけじゃねえ。女房がいなくても自分で味噌汁は作れる」

八「九番の `RestClient` は Bean じゃねえんですか」

隠「Bean じゃねえ。手で受け取ってるのは `JevProperties` までで、`RestClient` はその場で組んでる。使うのがこの一人だけなら、長屋に住まわせる理由がねえ。
Boot には `RestClient.Builder` を Bean で配る仕掛けもあるが、hamon では使ってねえ」

八「手を出してねえ Bean もいますね」

隠「`InvalidParameterAdvice` と `HamonApplication` は引数なしだ。手を出してねえが、名乗ってはいる。だから Bean。
hamon の Bean は『名乗って手を出す』『名乗るだけ』の二種類がいて、『手を出すだけ』はいねえ。Bean じゃねえやつには手を出す権利がねえからな」

八「テストにもありましたね、`@Autowired`」

```java
@Autowired
private MockMvcTester mvc;
```

```java
@LocalServerPort
private int port;
```

隠「あれがフィールド注入だ。テストクラスは JUnit が `new` するから、コンストラクタで受け取るには一手間いる。
だからフィールドに印を付けて、Spring が後から差し込む。`@LocalServerPort` は MCP のテストで、本物のポートで起こしたサーバーの番号をもらう手だ。プロダクションコードにはフィールド注入は一つもねえ」

---

## 三席目：縁結び、誰が誰に渡るか

八「名乗る側と手を出す側が揃った。で、誰が誰に渡るんです」

隠「Spring の縁結びの規則はひとつ。**手を出してる型に、代入できる Bean を探す**。ちょうど一人いれば渡す。零人なら転ぶ。
二人以上なら『名前が合うやつ』『`@Primary` のやつ』を探して、それでも決まらなきゃ転ぶ」

隠「十本の手を、一本ずつ結ぶ」

```
手: ImpactController(ImpactSurvey)         → 六番。一人
手: AnalysisController(Analysis)           → 七番。一人
手: NodeSearchController(GraphMapper)      → 十六番。MyBatis starter が作った実装。一人
手: ImpactSurvey(ImpactMapper)             → 十五番。一人
手: Analysis(AnalysisProperties, JavaSourceAnalysis, MyBatisMapperAnalysis, SchemaMapper, GraphStore)
                                           → 十三、八、九、十七、十番。全部一人ずつ
手: JavaSourceAnalysis(DependencyJudge)    → 型 DependencyJudge に代入できる Bean は? 十一番 JevDependencyJudge。一人
手: MyBatisMapperAnalysis(DependencyJudge) → 同じく十一番。一人
手: GraphStore(GraphMapper)                → 十六番。NodeSearchController と同じ一人を分け合う
手: JevDependencyJudge(JevProperties)      → 十四番。yaml と環境変数から束ねた record。一人
手: HamonTools(ImpactSurvey, Analysis)     → 六番と七番。Controller と同じ一人を分け合う
```

隠「十本とも、本番では迷いなく一人に決まる。だから名前も `@Primary` も、プロダクションコードには出てこねえ」

八「`GraphMapper` が二人に渡ってますが、二つ作るんですか」

隠「一つだ。Bean は既定で長屋に一人（シングルトン）。`NodeSearchController` も `GraphStore` も同じ一人を使う。
`ImpactSurvey` と `Analysis` も、REST の Controller と MCP の `HamonTools` が同じ一人を分け合う。**REST と MCP が同じ答えを返す**のは、裏が同じ一人だからだ」

八「で、`DependencyJudge` だけインターフェースを残したわけは」

隠「Jev は**管理外の外部 API**だ。テストで本物を呼ぶわけにゃいかねえ。だから手は `DependencyJudge` で出して、本番は `JevDependencyJudge` が名乗り、テストでは別のやつを名乗らせる。
**手はインターフェースで出し、名乗りは実装クラスでやる**。差し替えが本当に要るところだけ、この形にする。
`GraphStore` は差し替える相手がいねえからインターフェースをやめた。それが ADR-0003 の言い分だ」

隠「もう一人、おめえの手にも名簿にも出てこねえが、長屋にいるやつがいる。MCP サーバーだ」

```java
@McpTool(name = "survey_impact", description = "変更点（クラス#メソッド、TABLE、TABLE.COLUMN）を変えたときに影響を受ける候補を、確からしさの高い順に返す。"
		+ "probability は経路上の辺の確からしさの積、path は変更点から候補までのノード列")
List<ImpactItem> surveyImpact(
```

隠「Spring AI の starter が MCP サーバーの Bean 一式を名簿に足して、そいつが名簿の中から `@McpTool` の付いたメソッドを探して、ツールとして Claude Code に見せる。
`HamonTools` は手を出してるだけで、誰かに渡ってるわけじゃねえ。**名乗ると呼ばれる**。`@RestControllerAdvice` と同じ型のハリウッドだ」

隠「全部つなぐとこういう絵になる。矢印は『手を出した側 ◀ 渡された側』」

```
HTTP GET  /api/impact ──▶ ImpactController ──┐
MCP  survey_impact ─────▶ HamonTools ─────────┤── survey: ImpactSurvey ◀── mapper: ImpactMapper ◀── MyBatis starter（ImpactMapper.xml）
                                              │
HTTP POST /api/analysis ─▶ AnalysisController ┤
MCP  analyze_sources ───▶ HamonTools ─────────┴── analysis: Analysis
                                                    ├─ properties   ◀── AnalysisProperties ◀── application.yml の hamon.analysis.*
                                                    ├─ javaAnalysis ◀── JavaSourceAnalysis ──┐
                                                    ├─ mapperAnalysis ◀── MyBatisMapperAnalysis ┴── judge: DependencyJudge ◀── JevDependencyJudge（@Retryable の影武者つき）
                                                    │                                                        └─ properties: JevProperties ◀── hamon.jev.* と環境変数 TYPESAFE_API_KEY
                                                    ├─ schemaMapper ◀── SchemaMapper ◀── MyBatis starter（SchemaMapper.xml）
                                                    └─ store        ◀── GraphStore（@Transactional の影武者つき）
                                                                          └─ mapper: GraphMapper ◀── MyBatis starter（GraphMapper.xml）
HTTP GET  /api/nodes ────▶ NodeSearchController ── mapper: GraphMapper（上と同じ一人）
                                                                                   └─ SqlSessionFactory ◀── DataSource ◀── 環境変数 SPRING_DATASOURCE_*
例外 ────────────────────▶ InvalidParameterAdvice  （手なし）
```

---

## 四席目：起動のとき、何が何番に起きるか

八「で、これが起動のときにどういう順で起きるんです。`new` の順番が見えねえのが気持ち悪い」

隠「順番はこうだ。おめえの書いたものに限って言う」

1. **大家が名簿を作る。** `io.github.akiraisozaki.hamon` 以下を走査して、看板を背負ってる十二人を見つける。この時点では**まだ誰も `new` してねえ**。名簿だけだ
2. **蔵が名簿に足す。** starter の自動構成が DataSource、SqlSessionFactory、TransactionManager、Flyway、Spring MVC、Jackson、MCP サーバーなどを名簿に足す。
   MyBatis starter が `@Mapper` 三人を、`@ConfigurationPropertiesScan` が設定値の塊二人を足す
3. **名簿を見て、依存の順に `new` する。** ここで初めてインスタンスができる。`Analysis` を作ろうとして、手を見て『`JavaSourceAnalysis` が要る』、
   `JavaSourceAnalysis` を作ろうとして『`DependencyJudge` が要る』、`JevDependencyJudge` を作ろうとして『`JevProperties` が要る』、
   と**手を辿って底まで降りて、底から順に作って上がってくる**。`new` の順番を考えなくていいのは、この探索を Spring がやるからだ
4. **影武者をかぶせる。** `@Transactional` の `GraphStore`、`@Retryable` の `JevDependencyJudge`、`@Validated` のインターフェースを実装してる Controller 三人に、プロキシを一枚かぶせる。
   名簿に登録されるのは影武者の方だ。だから `Analysis` が受け取る `store` も `judge` も影武者
5. **MVC が道案内を作る。** `@RequestMapping` を読んで『`GET /api/impact` が来たら `ImpactController#surveyImpact`』の表を作る。MCP サーバーも `@McpTool` を読んでツールの表を作る
6. **Flyway が V ファイルを流す。** DataSource ができた直後にやる
7. **Tomcat が戸を開ける。** ここまで全部無事なら 8080 で客を待つ

隠「三番で一人でも欠けると、戸を開ける前に転ぶ。わざと Jev のキーを空にして起動してみた。本物の転び方だ」

```
TYPESAFE_API_KEY= ./gradlew bootRun
```

隠「まず WARN が一行出る。長えから途中を刈ってあるが、Bean の名前の順はそのままだ」

```
Error creating bean with name 'analysis' ...: Unsatisfied dependency expressed through constructor parameter 1:
  Error creating bean with name 'javaSourceAnalysis' ...: Unsatisfied dependency expressed through constructor parameter 0:
    Error creating bean with name 'jevDependencyJudge' ...: Unsatisfied dependency expressed through constructor parameter 0:
      Error creating bean with name 'hamon.jev-io.github.akiraisozaki.hamon.judgment.JevProperties':
        Could not bind properties to 'JevProperties' : prefix=hamon.jev, ignoreInvalidFields=false, ignoreUnknownFields=true
```

八「おお、三番の『手を辿って底まで降りる』が、そのまま字になってる。`analysis` の二番目の手、`javaSourceAnalysis` の零番目の手、`jevDependencyJudge` の零番目の手、で `JevProperties` が底」

隠「そうだ。手の番号は零から数える。で、その下に人間向けの報告が出る」

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'hamon.jev' to io.github.akiraisozaki.hamon.judgment.JevProperties:

    Reason: java.lang.IllegalArgumentException: hamon.jev.api-key が空です。リポジトリ直下の .env に TYPESAFE_API_KEY を書いてコンテナをリビルドしてください

Action:

Update your application's configuration
```

隠「`Reason` の日本語は `JevProperties` のコンパクトコンストラクタがおめえに向けて投げた文言だ。Spring はそれを捕まえて、どの設定の束ねで転んだかを添えて出す。
設定を間違えてりゃあ、客が来る前に転んでくれる。昔は客が来て Jev を呼んだところで『401』だったろう」

八「`ImpactController` の方は転ばなかったんで」

隠「転ぶ前に `Analysis` の系統で転んだだけだ。先に見つかった方で止まる。キーを入れりゃあ全部生き返る」

---

## 五席目：影武者の実物

八「四番の影武者、あれが一番ぴんと来ねえ。受け取ってるのは本物じゃねえと」

隠「`Analysis` の `store` の中身をデバッガで見れば、クラス名が `GraphStore$$SpringCGLIB$$0` のような名前になってる。
Spring が `GraphStore` を**継承したクラスを実行時に作って**、`replaceWith` を上書きしてる。上書きした中身が『トランザクション開始、`super.replaceWith()`、commit か rollback』だ」

```java
// Spring が実行時に作る影武者のイメージ（実物は CGLIB が生成する。デバッガで確認したものではない）
class GraphStore$$SpringCGLIB$$0 extends GraphStore {
	@Override
	public void replaceWith(DependencyGraph graph) {
		TransactionStatus tx = transactionManager.getTransaction(...);
		try {
			super.replaceWith(graph);       // ここで本物
			transactionManager.commit(tx);
		} catch (RuntimeException e) {
			transactionManager.rollback(tx);
			throw e;
		}
	}
}
```

隠「だから `GraphStore` を `final` にすると影武者が作れなくて転ぶ。だから中から `this.replaceWith()` と呼ぶと `super` 側の本物に直接行って影武者を通らねえ。
だから `new GraphStore(mapper)` と自分で作ると影武者がいねえ。三つの『だから』は全部、影武者が『継承した別クラス』だからだ」

八「`JevDependencyJudge` の影武者ってのは」

```java
@Override
@Retryable(includes = { HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
		maxRetries = 3, delay = 1000, multiplier = 2)
public Map<String, Double> judge(Ambiguity ambiguity) {
```

隠「同じ作りで、上書きの中身が『`super.judge()` を呼ぶ、5xx か 429 か接続失敗なら一秒待ってもう一度、次は二秒、次は四秒、三回で諦めて例外を投げる』になってる。
これは Spring Framework 7 で本体に入った仕掛けで、大家の `@EnableResilientMethods` が『この印を見たら影武者を立てろ』の合図だ。
`@Transactional` と同じ三つの『だから』がそのまま効く。再試行を書かずに済んでるのは、影武者が間に立ってるからだ」

八「Controller の影武者は」

隠「同じ作りで、上書きの中身が『引数を検査して、違反なら `ConstraintViolationException`』になってる。それを `InvalidParameterAdvice` が受ける。
あの Advice が手を出してねえのに動くのは、MVC が『例外が出たら `@RestControllerAdvice` の名簿を見る』と決めてるからだ。
手を出す代わりに、**名乗ると呼ばれる**。これもハリウッドの原則の一種だ」

---

## 六席目：テストの長屋

八「テストのときも同じ長屋が建つんですか」

隠「`@SpringBootTest` は同じ大家を呼んで、同じ手順で同じ長屋を建てる。違うのは四つだけだ」

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

	@Autowired
	private MockMvcTester mvc;
```

- `properties =` で yaml を二か所上書きする。`AnalysisProperties` の中身だけが変わり、`Analysis` は何も知らずにそれを受け取る
- `@AutoConfigureMockMvc` が `MockMvcTester` を名簿に足す。テストがそれに手を出す
- `@Transactional` でテスト一本ごとに風呂敷を広げ、終わったら巻き戻す。解析で書いた node / edge も消える
- **`@TestConfiguration` の番頭部屋**。プロダクションには空き家だった番頭が、ここだけ住んでる

八「`@Primary` てえのは」

隠「このテストの長屋では、`DependencyJudge` に代入できる Bean が**二人**いる。本物の `JevDependencyJudge` と、番頭が返した `FixedDependencyJudge`。
三席目の規則で、二人いたら転ぶはずだ。そこで `@Primary`。『迷ったらこっち』の印だ。`JavaSourceAnalysis` と `MyBatisMapperAnalysis` の手には、黙って偽物が渡る」

```java
/** テスト用。全候補に同じ確からしさを返し、何を聞かれたかを覚えておく（Jev は管理外の外部 API なので本物は使わない） */
public final class FixedDependencyJudge implements DependencyJudge {
```

隠「こいつ自身には看板がねえ。`final` でもいい。番頭が `new` して返すから Bean になる。
**手を出す側を一文字も変えずに、名乗る側を替えて同じ検査を通す**。前は `@Profile` の掛け替えでこれを見せたが、今はこの番頭一人で見せてる。DI の値打ちはこれに尽きる」

隠「あと三つの長屋も言っとく」

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/sample-graph.sql")
class ImpactApiTest {
```

隠「`ImpactApiTest` と `NodeSearchApiTest` は `@Sql` でサンプルのグラフを風呂敷の中に入れて、API から Oracle まで通す。番頭はいらねえ。Jev を呼ぶ道がねえからだ」

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HamonToolsTest {

	@LocalServerPort
	private int port;
```

隠「`HamonToolsTest` だけ作りが違う。`MockMvcTester` の裏口じゃなく、空いてるポートで Tomcat を本当に起こして、Claude Code と同じ MCP クライアントで `POST /mcp` を叩く。
本物のポートで起こすと、テストのスレッドとサーバーのスレッドが別だから、風呂敷が効かねえ。だから DB を書き換えるツールは呼ばず、読むだけのツールで確かめてる」

---

## サゲ

八「ご隠居、名簿を作り直したら腑に落ちました。名乗ってるのが十七人、手を出してるのが十か所、縁結びは型で一人に決まる、二人いるのはテストの目利きだけで `@Primary`、順番は手を辿って底から、影武者は継承で三枚、テストも同じ長屋」

隠「よく覚えた」

八「で、あたしが『`new` がねえ』と騒いでたのは」

隠「三番だ。手を辿って底から作る、あの一か所を Spring がやってる。おめえの `new` は全部そこに集まってる」

八「じゃあ `new` は消えたんじゃなくて、引っ越しただけで」

隠「引っ越し先が、おめえの書かねえ場所なだけだ」

八「……それ、うちの女房が味噌汁の味噌をどこで買ってるか、あたしが知らねえのと同じですね」

隠「知らなくても味噌汁は出る。知りたきゃ、転んだときの一行を読め。今日のは『味噌が切れてる』と女房が自分で書いた一行だった」

お後がよろしいようで。

---

## 付録：新しいクラスを足すときの判定表

| 問い | はい | いいえ |
|---|---|---|
| 長屋に一人いれば足りるか（状態を持たず、依存を持つ働き手か） | **役**。`@Component` を付け、依存はコンストラクタ引数で受け取る | **物**。record にして `new` する |
| 実装を差し替えたいか | 差し替えが本当に要るのは管理外の外部依存だけ。インターフェースで手を出し、テストでは `@TestConfiguration` + `@Primary` で替える | 実装が 1 つなら具象クラスのまま手に出す（ADR-0003） |
| 自分で書いたクラスか | クラスに注釈を直接付ける | `@Configuration` の `@Bean` メソッドで返す（ラムダ、外部ライブラリのクラス。hamon ではテストにだけある） |
| 設定値の塊か | `@ConfigurationProperties` の record。コンパクトコンストラクタで検証。秘密は `${環境変数}` で差し込む | |
| 外部 API を呼ぶか | `RestClient` をコンストラクタで組む。`@Retryable` で再試行。失敗は答えにせず例外で上げる | |
| 前後に横断的な処理を挟みたいか（トランザクション、再試行、検証） | 注釈で影武者に任せる。`final` にしない、`this` 呼びしない、自分で `new` しない | |
| REST と MCP の両方から呼ぶか | 中身を `@Component` に出して（`ImpactSurvey`、`Analysis`）、Controller と `@McpTool` の両方がそれに手を出す | |
