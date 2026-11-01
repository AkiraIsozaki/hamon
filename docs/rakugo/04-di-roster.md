# DI の名簿（誰が名乗り、誰が手を出し、誰が誰に渡るか）

> hamon の自前コードにある Bean 全員と注入箇所全部を、省略なしで並べる。Phase 3 完了時点（2026-11-01）。
> 起動失敗のメッセージは、プロファイルを `none` にして実際に起動して取ったもの。

## 枕：名簿を作る

八「ご隠居、DI は分かったつもりでしたが、まだぼんやりしてる。『手を出せば来る』は分かった。
でも**誰が名乗ってて、誰が手を出してて、誰が誰に渡ってるのか**、全員分を一度見ねえと腑に落ちねえ」

隠「いい心がけだ。じゃあ名簿を作ろう。hamon の長屋の住人は、おめえが書いた分だけなら十三人しかいねえ。全員出す。省略なしだ」

---

## 一席目：名乗ってる者の名簿（宣言側）

隠「名乗り方は三通りある。まず**自分で看板を背負ってる**連中」

```java
// HamonApplication.java
@SpringBootApplication
public class HamonApplication {
```

隠「大家本人。こいつも Bean だ。`@SpringBootApplication` の中に `@Configuration` が入ってるからな。
この大家の住所が `io.github.akiraisozaki.hamon` で、ここから下を全部見回る」

```java
// InvalidParameterAdvice.java
@RestControllerAdvice
class InvalidParameterAdvice {
```

```java
// impact/ImpactController.java
@RestController
@RequestMapping("/api")
class ImpactController implements ImpactApi {
```

```java
// analysis/AnalysisController.java
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {
```

```java
// analysis/JavaSourceAnalysis.java
@Component
public class JavaSourceAnalysis {
```

```java
// graph/MyBatisGraphStore.java
@Component
@Profile("mybatis")
class MyBatisGraphStore implements GraphStore {
```

```java
// graph/DataJdbcGraphStore.java
@Component
@Profile("datajdbc")
class DataJdbcGraphStore implements GraphStore {
```

隠「`@RestController` も `@RestControllerAdvice` も `@Configuration` も、中を剥けば `@Component` だ。だから大家は全部同じ目で見る。
『あ、看板を背負ってる、住まわせよう』。これで七人」

八「`@Profile` の二人は」

隠「看板に『雨天休業』と書いてあるのと同じだ。大家は見つけるが、今日の看板と合わなきゃ住まわせねえ」

隠「次、**番頭が作る**連中。番頭部屋は二つある」

```java
// impact/ImpactGraphConfiguration.java
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

```java
// graph/DataJdbcConfiguration.java
@Configuration
@Profile("datajdbc")
class DataJdbcConfiguration {

	@Bean
	NamingStrategy upperCaseNamingStrategy() {
		return new DefaultNamingStrategy() { ... };
	}
}
```

隠「番頭部屋そのものが Bean で二人、番頭が作る Bean が最大で二人。`ImpactGraph` は看板で片方だけだから、
実際に増えるのは `ImpactGraph` 一人と、datajdbc の日だけ `NamingStrategy` 一人」

八「`@Bean` の方のメソッド名、`myBatisImpactGraph` ってのは」

隠「それが Bean の名前になる。クラスの方は頭を小文字にした `impactController` が名前だ。
名前で呼び分けることもできるが、hamon は一度もやってねえ。全部**型**で合わせてる」

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
// impact/ImpactRepository.java
interface ImpactRepository extends Repository<Node, Long> {
```

```java
// graph/NodeRepository.java
interface NodeRepository extends CrudRepository<Node, Long> {
```

```java
// graph/EdgeRepository.java
interface EdgeRepository extends CrudRepository<Edge, Long> {
```

隠「`@Mapper` の二人は MyBatis の starter が見つけて、XML の SQL を実行する実装をその場で作って Bean にする。
`Repository` を継いだ三人は Spring Data の starter が見つけて、同じことをする」

八「ちょっと待ってください。`ImpactRepository` は datajdbc の日しか使わねえでしょう。mybatis の日にも作られるんですか」

隠「作られる。Spring Data の starter はクラスパスにある限り、看板を見ずに `Repository` を全部 Bean にする。MyBatis の `@Mapper` も同じで、datajdbc の日も作られる。
**二人の職人は毎日長屋にいる。声をかけられるのが片方だけ**なんだ。看板で切ってるのは職人じゃなくて、職人に声をかける側だ」

八「へえ。じゃあ名簿は」

| # | Bean | 名乗り方 | 看板 |
|---|---|---|---|
| 1 | `HamonApplication` | `@SpringBootApplication` | 常 |
| 2 | `InvalidParameterAdvice` | `@RestControllerAdvice` | 常 |
| 3 | `ImpactController` | `@RestController` | 常 |
| 4 | `AnalysisController` | `@RestController` | 常 |
| 5 | `JavaSourceAnalysis` | `@Component` | 常 |
| 6 | `AnalysisProperties` | `@ConfigurationProperties` + 大家の `@ConfigurationPropertiesScan` | 常 |
| 7 | `ImpactGraphConfiguration` | `@Configuration` | 常 |
| 8 | `ImpactGraph`（ラムダ） | 7 の `@Bean` | mybatis か datajdbc で中身が違う |
| 9 | `MyBatisGraphStore` | `@Component` | mybatis |
| 10 | `DataJdbcGraphStore` | `@Component` | datajdbc |
| 11 | `DataJdbcConfiguration` と `NamingStrategy` | `@Configuration` + `@Bean` | datajdbc |
| 12 | `ImpactMapper`, `GraphMapper` | `@Mapper`（MyBatis starter） | 常 |
| 13 | `ImpactRepository`, `NodeRepository`, `EdgeRepository` | `Repository` 継承（Spring Data starter） | 常 |

隠「六番の `AnalysisProperties` だけ名乗り方が違う。クラスには `@ConfigurationProperties` しかねえ。これは『設定値の塊だ』という印で、それ自体は Bean の印じゃねえ。
大家の `@ConfigurationPropertiesScan` が『その印のやつも住まわせろ』と言ってるから Bean になる」

```java
@ConfigurationPropertiesScan // hamon.* の設定値を record に束ねる（AnalysisProperties など）
public class HamonApplication {
```

```java
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots) {
```

隠「で、名簿に**いねえ**連中も言っとく。`ImpactRow`、`DependencyGraph`、`DependencyEdge`、`Node`、`Edge`、`NodeKind`。印がねえ。だから Bean じゃねえ。物だ。`new` される。
`Node` には `@Table` が付いてるが、あれは Spring Data に『表との対応』を教える印で、Bean の印じゃねえ」

---

## 二席目：手を出してる者の名簿（利用側）

八「名乗りは分かりました。じゃあ手を出してる方を」

隠「手の出し方は hamon では一種類しかねえ。**コンストラクタの引数**だ。全部で五か所。これも省略なしで出す」

```java
// 手その一
ImpactController(ImpactGraph graph) {
	this.graph = graph;
}
```

```java
// 手その二
AnalysisController(AnalysisProperties properties, JavaSourceAnalysis analysis, GraphStore store) {
	this.properties = properties;
	this.analysis = analysis;
	this.store = store;
}
```

```java
// 手その三
MyBatisGraphStore(GraphMapper mapper) {
	this.mapper = mapper;
}
```

```java
// 手その四
DataJdbcGraphStore(NodeRepository nodes, EdgeRepository edges) {
	this.nodes = nodes;
	this.edges = edges;
}
```

```java
// 手その五、番頭部屋の中。@Bean メソッドの引数も「手」だ
ImpactGraph myBatisImpactGraph(ImpactMapper mapper) { ... }
ImpactGraph dataJdbcImpactGraph(ImpactRepository repository) { ... }
```

八「`@Autowired` がどこにもねえ」

隠「コンストラクタが一つしかねえクラスは、Spring が『このコンストラクタで作るしかねえ』と分かるから、書かなくていい。
二つ以上あるときだけ `@Autowired` でどれか教える。hamon は全部一つだ」

八「手を出してねえ Bean もいますね」

隠「`JavaSourceAnalysis` と `InvalidParameterAdvice` は引数なしだ。手を出してねえが、名乗ってはいる。だから Bean。`HamonApplication` もそうだ。
手を出すのと名乗るのは別の話で、hamon の Bean は『名乗って手を出す』『名乗るだけ』の二種類がいて、『手を出すだけ』はいねえ。Bean じゃねえやつには手を出す権利がねえからな」

八「テストにもありましたね、`@Autowired`」

```java
@Autowired
private MockMvcTester mvc;
```

隠「あれは唯一のフィールド注入だ。テストクラスは JUnit が `new` するから、コンストラクタで受け取るには一手間いる。
だからフィールドに `@Autowired` を付けて、Spring が後から差し込む。プロダクションコードには一つもねえ」

---

## 三席目：縁結び、誰が誰に渡るか

八「名乗る側と手を出す側が揃った。で、誰が誰に渡るんです」

隠「Spring の縁結びの規則はひとつ。**手を出してる型に、代入できる Bean を探す**。ちょうど一人いれば渡す。零人なら転ぶ。
二人以上なら『名前が合うやつ』『`@Primary` のやつ』を探して、それでも決まらなきゃ転ぶ。hamon は看板で必ず一人にしてるから、名前も `@Primary` も使ってねえ」

隠「五本の手を、一本ずつ結ぶ」

```
手: ImpactController(ImpactGraph graph)
  型 ImpactGraph に代入できる Bean は?
    mybatis の日  → ImpactGraphConfiguration#myBatisImpactGraph が返したラムダ（中身 ImpactMapper）
    datajdbc の日 → ImpactGraphConfiguration#dataJdbcImpactGraph が返したラムダ（中身 ImpactRepository）
  どちらの日も一人 → 渡す

手: AnalysisController(AnalysisProperties, JavaSourceAnalysis, GraphStore)
  AnalysisProperties → 六番。yaml から束ねた record。一人
  JavaSourceAnalysis → 五番。一人
  GraphStore         → mybatis の日は MyBatisGraphStore、datajdbc の日は DataJdbcGraphStore。一人

手: MyBatisGraphStore(GraphMapper)
  GraphMapper → MyBatis starter が作った実装。一人

手: DataJdbcGraphStore(NodeRepository, EdgeRepository)
  それぞれ Spring Data が作った実装。一人ずつ

手: myBatisImpactGraph(ImpactMapper) / dataJdbcImpactGraph(ImpactRepository)
  starter が作った実装。一人ずつ
```

隠「`GraphStore` のところをよく見ろ。手を出してるのはインターフェース型だ。Bean の方は `MyBatisGraphStore` クラスで名乗ってる。
『`MyBatisGraphStore` は `GraphStore` に代入できるか』、できる、だから渡す。
**手はインターフェースで出し、名乗りは実装クラスでやる**。これが DI で実装を差し替えられる理由の全部だ」

八「じゃあ `ImpactGraph` のラムダは、何のクラスなんです」

隠「名前のねえクラスだ。`mapper::traceUpstream` を Java がその場で作る。Spring は『`ImpactGraph` に代入できるか』しか見ねえから、名前がなくても構わねえ」

隠「全部つなぐとこういう絵になる。矢印は『手を出した側 ◀ 渡された側』。mybatis の日だ」

```
                 ┌──────────────────────────────┐
HTTP ───────────▶│ ImpactController             │
                 │   graph: ImpactGraph ◀───────┼─── ImpactGraphConfiguration#myBatisImpactGraph
                 └──────────────────────────────┘        └─ mapper: ImpactMapper ◀── MyBatis starter（ImpactMapper.xml）
                                                                                           └─ SqlSessionFactory ◀── DataSource ◀── 環境変数

                 ┌──────────────────────────────┐
HTTP ───────────▶│ AnalysisController           │
                 │   properties ◀───────────────┼─── AnalysisProperties ◀── application.yml の hamon.analysis.*
                 │   analysis   ◀───────────────┼─── JavaSourceAnalysis
                 │   store: GraphStore ◀────────┼─── MyBatisGraphStore（@Transactional の影武者つき）
                 └──────────────────────────────┘        └─ mapper: GraphMapper ◀── MyBatis starter（GraphMapper.xml）

                 ┌──────────────────────────────┐
例外 ───────────▶│ InvalidParameterAdvice       │  （手なし）
                 └──────────────────────────────┘

（長屋にはいるが声をかけられない） ImpactRepository, NodeRepository, EdgeRepository
（看板が合わず名簿から消える）   DataJdbcGraphStore, DataJdbcConfiguration, dataJdbcImpactGraph
```

八「datajdbc の日は」

隠「`ImpactGraph` の中身が `ImpactRepository` に、`GraphStore` が `DataJdbcGraphStore` に替わって、その下に `NodeRepository` と `EdgeRepository` がぶら下がる。
`NamingStrategy` が一人増えて Spring Data に拾われる。Controller 二人は**一文字も変わらねえ**。これが差し替えの実物だ」

---

## 四席目：起動のとき、何が何番に起きるか

八「で、これが起動のときにどういう順で起きるんです。`new` の順番が見えねえのが気持ち悪い」

隠「順番はこうだ。おめえの書いたものに限って言う」

1. **大家が名簿を作る。** `io.github.akiraisozaki.hamon` 以下を走査して、看板を背負ってる七人と番頭部屋二つを見つける。この時点では**まだ誰も `new` してねえ**。名簿だけだ
2. **看板で名簿を削る。** `@Profile` が今日の看板と合わねえやつを名簿から消す。mybatis の日なら `DataJdbcGraphStore`、`DataJdbcConfiguration`、`dataJdbcImpactGraph` が消える
3. **蔵が名簿に足す。** starter の自動構成が DataSource、SqlSessionFactory、TransactionManager、Flyway、Spring MVC、Jackson などを名簿に足す。
   MyBatis starter が `@Mapper` 二人を、Spring Data starter が `Repository` 三人を足す。`@ConfigurationPropertiesScan` が `AnalysisProperties` を足す
4. **名簿を見て、依存の順に `new` する。** ここで初めてインスタンスができる。`AnalysisController` を作ろうとして、手を見て『`GraphStore` が要る』、
   `MyBatisGraphStore` を作ろうとして『`GraphMapper` が要る』、`GraphMapper` を作ろうとして『`SqlSessionFactory` が要る』、
   と**手を辿って底まで降りて、底から順に作って上がってくる**。`new` の順番を考えなくていいのは、この探索を Spring がやるからだ
5. **影武者をかぶせる。** `@Transactional` の `MyBatisGraphStore` と、`@Validated` のインターフェースを実装してる `ImpactController` に、プロキシを一枚かぶせる。
   名簿に登録されるのは影武者の方だ。だから `AnalysisController` が受け取る `store` は影武者
6. **MVC が道案内を作る。** `@RequestMapping` を読んで『`GET /api/impact` が来たら `ImpactController#surveyImpact`』の表を作る
7. **Flyway が V ファイルを流す。** DataSource ができた直後にやる
8. **Tomcat が戸を開ける。** ここまで全部無事なら 8080 で客を待つ

隠「四番で一人でも欠けると、戸を開ける前に転ぶ。わざと看板を `none` にして起動してみた。本物の転び方だ」

```
./gradlew bootRun --args='--spring.profiles.active=none'
```

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 2 of constructor in io.github.akiraisozaki.hamon.analysis.AnalysisController required a bean of type 'io.github.akiraisozaki.hamon.graph.GraphStore' that could not be found.

Action:

Consider defining a bean of type 'io.github.akiraisozaki.hamon.graph.GraphStore' in your configuration.
```

八「おお、分かりやすい。『`AnalysisController` のコンストラクタの三番目の引数が `GraphStore` を欲しがってるが、名乗ってる者がいねえ』」

隠「そうだ。手を出した場所と、欲しかった型と、いなかったこと。三つとも一行で言ってる。`new` を探す代わりに、この一行を読めばいい。
で、なぜいねえかと言えば、`MyBatisGraphStore` も `DataJdbcGraphStore` も看板が合わなくて二番で名簿から消えたからだ」

八「`ImpactController` の方は転ばなかったんで」

隠「転ぶ前に `AnalysisController` で転んだだけだ。先に見つかった方で止まる。看板を直せば両方生き返る」

---

## 五席目：影武者の実物

八「五番の影武者、あれが一番ぴんと来ねえ。受け取ってるのは本物じゃねえと」

隠「`AnalysisController` の `store` の中身をデバッガで見れば、クラス名が `MyBatisGraphStore$$SpringCGLIB$$0` のような名前になってる。
Spring が `MyBatisGraphStore` を**継承したクラスを実行時に作って**、`replaceWith` を上書きしてる。上書きした中身が『トランザクション開始、`super.replaceWith()`、commit か rollback』だ」

```java
// Spring が実行時に作る影武者のイメージ（実物は CGLIB が生成する。デバッガで確認したものではない）
class MyBatisGraphStore$$SpringCGLIB$$0 extends MyBatisGraphStore {
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

隠「だから `MyBatisGraphStore` を `final` にすると影武者が作れなくて転ぶ。だから中から `this.replaceWith()` と呼ぶと `super` 側の本物に直接行って影武者を通らねえ。
だから `new MyBatisGraphStore(mapper)` と自分で作ると影武者がいねえ。三つの『だから』は全部、影武者が『継承した別クラス』だからだ」

八「`ImpactController` の影武者は」

隠「同じ作りで、上書きの中身が『引数を検査して、違反なら `ConstraintViolationException`』になってる。それを `InvalidParameterAdvice` が受ける。
あの Advice が手を出してねえのに動くのは、MVC が『例外が出たら `@RestControllerAdvice` の名簿を見る』と決めてるからだ。
手を出す代わりに、**名乗ると呼ばれる**。これもハリウッドの原則の一種だ」

---

## 六席目：テストの長屋

八「テストのときも同じ長屋が建つんですか」

隠「`@SpringBootTest` は同じ大家を呼んで、同じ手順で同じ長屋を建てる。違いは三つだけだ」

```java
@SpringBootTest(properties = "hamon.analysis.source-roots=src/test/resources/analysis-fixture")
@AutoConfigureMockMvc
@Transactional
class AnalysisApiTest {
	@Autowired
	private MockMvcTester mvc;
```

- `properties =` で yaml を一か所上書きする。`AnalysisProperties` の中身だけが変わり、`AnalysisController` は何も知らずにそれを受け取る
- `@AutoConfigureMockMvc` が `MockMvcTester` を名簿に足す。テストがそれに手を出す
- `@ActiveProfiles("datajdbc")` のサブクラスでは、二番の『看板で削る』の結果が逆になる。残りの手順は同じ

```java
@ActiveProfiles("datajdbc")
class AnalysisApiDataJdbcTest extends AnalysisApiTest {
}
```

隠「だからこの三行で `GraphStore` の中身が `DataJdbcGraphStore` に、`ImpactGraph` の中身が `ImpactRepository` に替わる。テスト本体は親のまま。
**手を出す側を一文字も変えずに、名乗る側を替えて同じ検査を通す**。DI の値打ちはこれに尽きる」

---

## サゲ

八「ご隠居、名簿を作ったら腑に落ちました。名乗ってるのが十三人、手を出してるのが五か所、縁結びは型で一人に決まる、順番は手を辿って底から、影武者は継承、テストも同じ長屋」

隠「よく覚えた」

八「で、あたしが『`new` がねえ』と騒いでたのは」

隠「四番だ。手を辿って底から作る、あの一か所を Spring がやってる。おめえの `new` は全部そこに集まってる」

八「じゃあ `new` は消えたんじゃなくて、引っ越しただけで」

隠「引っ越し先が、おめえの書かねえ場所なだけだ」

八「……それ、うちの女房が味噌汁の味噌をどこで買ってるか、あたしが知らねえのと同じですね」

隠「知らなくても味噌汁は出る。知りたきゃ、転んだときの一行を読め」

お後がよろしいようで。

---

## 付録：新しいクラスを足すときの判定表

| 問い | はい | いいえ |
|---|---|---|
| 長屋に一人いれば足りるか（状態を持たず、依存を持つ働き手か） | **役**。`@Component` を付け、依存はコンストラクタ引数で受け取る | **物**。record にして `new` する |
| 実装を差し替えたいか | インターフェースで手を出し、実装クラスで名乗る。複数あるなら `@Profile` か `@Primary` で一人にする | クラスをそのまま手に出してよい |
| 自分で書いたクラスか | クラスに注釈を直接付ける | `@Configuration` の `@Bean` メソッドで返す（ラムダ、外部ライブラリのクラス） |
| 設定値の塊か | `@ConfigurationProperties` の record。コンパクトコンストラクタで検証 | |
| 前後に横断的な処理を挟みたいか（トランザクション、検証） | 注釈で影武者に任せる。`final` にしない、`this` 呼びしない、自分で `new` しない | |
