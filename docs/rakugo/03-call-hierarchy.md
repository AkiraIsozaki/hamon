# 右クリックの神様（IDE の呼び出し階層は Spring でも使えるか）

> 結論: 使える。ただし 5 か所で木が切れる。その 5 か所は hamon の「あいまいな辺」そのものである。
> 今の hamon（Phase 6 完了後、2026-11-01 に実測）は、インターフェースの壁のうち `GraphStore` の分が消え（具象クラスになった、ADR-0003）、
> 残るのは `DependencyJudge` の一か所。文字列の川は MyBatis XML と表・列の分に橋が架かり（Phase 4、ADR-0004）、あいまいな辺は Jev が判定する（Phase 5、ADR-0005）。
> パッケージ `io.github.akiraisozaki.hamon` は省略して書く。

## 枕：右クリックの神様

八「ご隠居、あたしゃあね、影響調査には自信があったんです。IDE でメソッドの上で右クリック、『呼び出し階層を開く』。
これで呼び出し元が木になって出てくる。上へ上へと辿れば、画面まで届く。二十年これでやってきた」

隠「いい道具だ」

八「それが Spring になったら使えねえんじゃねえかと。`new` がねえ長屋ですから、呼び出し階層もどこかで切れちまうんじゃねえかと」

隠「答えを先に言おう。**使える。ただし、木が途中で切れる場所が五つある。** その五つを知ってりゃあ、道具は今までどおり使える。知らねえと『ここで終わりか』と思って漏らす」

八「五つですか」

隠「五つだ。しかもな、その五つは**おめえが作ってる hamon が埋めようとしてる穴そのもの**だ。だから今日はいい復習になる」

---

## 一席目：切れない場所

隠「まず安心させとこう。普通の呼び出しは切れねえ。`ImpactRow#toItem` の上で呼び出し階層を開けば、`ImpactSurvey#surveyImpact` が出る。
`JavaSourceAnalysis#analyze` を開けば `Analysis#run` が出る。Spring は普通の Java を普通に呼ぶ。ここは昔と同じだ」

八「それは良かった」

隠「メソッド参照も大抵は追える。`GraphStore#replaceWith` の中の `mapper::insertNode` は `GraphMapper#insertNode` の呼び出し元として、
`ImpactSurvey` の `ImpactRow::toItem` は `ImpactRow#toItem` の呼び出し元として IDE が拾う。ラムダの中の呼び出しも拾う。だから木の幹は生きてる」

```java
// graph/GraphStore.java
		graph.nodes().forEach(mapper::insertNode);
```

```java
// impact/ImpactSurvey.java
		return mapper.traceUpstream(target, minProbability).stream().map(ImpactRow::toItem).toList();
```

---

## 二席目：切れる場所、その一、インターフェースの壁

八「じゃあどこで切れるんです」

隠「`JevDependencyJudge#judge` の上で呼び出し階層を開いてみろ」

`analysis/JavaSourceAnalysis.java`

```java
		judge.judge(Ambiguity.methodCall(call.dependent(), call.callerSource(), call.text(), candidates))
```

`analysis/DatabaseSchema.java`

```java
				judge.judge(Ambiguity.columnReference(dependent, sql, identifier, owners))
```

隠「二か所とも呼んでるのは `DependencyJudge#judge`、インターフェースの方だ。実装クラス `JevDependencyJudge` のメソッドは誰も名指ししてねえ。
IDE によっては『この実装の呼び出し元』として空になるし、親メソッドの呼び出し元まで出してくれる IDE でも、
**実装が二つあるときにどっちが本当に呼ばれてるか**は IDE には分からねえ」

八「じゃあどうすれば」

隠「インターフェースの方、`DependencyJudge#judge` で開く。すると解析屋が二人出る。
実装側から辿るときは、まず『オーバーライド元へ』でインターフェースに上がって、そこから階層を開く」

八「Phase 3 のころは `GraphStore` もこの壁だったと聞きましたが」

隠「あのころは `GraphStore` がインターフェースで、MyBatis 版と Data JDBC 版の二つの実装があった。
ADR-0003 で MyBatis に決めて、インターフェースをやめて具象クラス一枚にした。今は `Analysis#run` が `store.replaceWith(graph)` と直に呼んでる。壁がひとつ消えた。
**実装が一つしかねえ抽象は、呼び出し階層を切るだけで何の得もねえ**。`code-design` の規律どおりだ」

隠「もう一つ、生成された `ImpactApi`、`AnalysisApi`、`NodesApi` もインターフェースだ。こっちは IDE の『実装を探す』で Controller に飛べるから困らねえ。
だが hamon の方は、生成コードの置き場 `build/generated` が解析対象に入ってねえから、この三つのインターフェースをノードとしてさえ知らねえ。
`GET /api/nodes?q=Api%23` と聞くと `[]` が返る。IDE が見えてて hamon が見えてねえ、逆の場所もあるってことだ」

---

## 三席目：その二、コンストラクタに呼び出し元がない

八「もう一つ気味の悪いのが、`ImpactController` のコンストラクタで呼び出し階層を開いても、誰も出てこねえ」

隠「それは本当に誰も呼んでねえ。呼んでるのは Spring がリフレクションでだ。おめえのコードにはいねえ」

八「じゃあ『この Controller は誰が使ってるんだ』はどう調べる」

隠「問いが違う。Controller を**使う**のは Spring MVC で、HTTP で叩かれる。だから調べるのは『どの URL が割り当たってるか』だ。
`@RequestMapping` を読む。hamon なら契約書 `openapi.yaml` を読めば一覧になってる」

隠「で、ここからが Spring らしいところだ。『この Bean を誰が受け取ってるか』は、呼び出し階層じゃなくて**型の利用箇所**で探す。
`Analysis` の型を右クリックして『参照を検索』。すると `AnalysisController` と `HamonTools` のコンストラクタ引数が出る。それが『受け取り手』だ。
Spring では**呼び出し元を探すときは、メソッドじゃなくて型で探す場面が増える**。これが一番大きな癖の違いだ」

```java
// analysis/AnalysisController.java
	AnalysisController(Analysis analysis) {
		this.analysis = analysis;
	}
```

| 探したいこと | 昔の道具 | Spring での道具 |
|---|---|---|
| このメソッドを呼ぶのは誰 | 呼び出し階層 | 呼び出し階層（インターフェース側で開く） |
| この部品を使うのは誰 | `new` を検索 | 型の参照検索。コンストラクタ引数が受け取り手 |
| この部品の本物はどれ | `new` の先を見る | 「実装を探す」。二つ出たら `@Primary`（hamon ではテストの中だけ）を読む。`@Profile` は hamon には無い |
| この Bean は実際に生きてるか | 読めば分かる | `/actuator/beans` か `--debug` の条件レポート |

---

## 四席目：その三、文字列の川

八「あとはどこで切れるんです」

隠「ここからが本当の難所だ。コードが**文字列**になって川を渡るところ」

**MyBatis の XML。** `ImpactMapper#traceUpstream` から階層を開いて、SQL の中身へは行けない。

```xml
<mapper namespace="io.github.akiraisozaki.hamon.impact.ImpactMapper">
  <select id="traceUpstream" ...>
```

隠「結び付きは `namespace` と `id` の**文字列一致**だ。IDE の呼び出し階層は Java の構文しか見ねえから、ここで川に落ちる。
MyBatis のプラグインを入れれば XML と Java を行き来できるが、呼び出し階層の木には乗らねえ」

八「ここは hamon が橋をかけたんでしたね」

隠「Phase 4 でな。`MyBatisMapperAnalysis` が XML の `<select id>` を `namespace#id` という名の METHOD ノードにする。
`io.github.akiraisozaki.hamon.impact.ImpactMapper#traceUpstream`。これは JavaParser が Mapper インターフェースのメソッドに付ける名前と**同じ文字列**だ。
だから XML から出た辺と Java から出た辺が、同じ一つのノードに落ちる。IDE が落ちる川の上に、名前の一致で橋を渡した」

**表とカラム。** `node_path` という列を変えたらどこに響くか。SQL の中の `node_path`、`ImpactRow` の `nodePath`、yaml の `map-underscore-to-camel-case`。
この三つは**名前の変換規則**でつながっている。IDE は知らない。

隠「ここも半分は橋がある。`DatabaseSchema#dependenciesOf` が SQL の文字列から識別子を拾って、Oracle の辞書 `USER_TAB_COLUMNS` にある表・列の名前と突き合わせる。
SQL パーサは使わねえ、識別子の一致だけだ（ADR-0004）。だから `NODE.NAME` という列から `ImpactMapper#traceUpstream` へ辺が張れる。
ただし `node_path` が `nodePath` に化ける Java 側の変換は、まだ誰も見てねえ」

**設定。** `hamon.analysis.source-roots` を改名したら。

```yaml
hamon:
  analysis:
    source-roots:
```

```java
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots, List<Path> mapperRoots) {
```

隠「yaml のキーと record の引数名は、ハイフンとキャメルの変換で結ばれてる。文字列だ。IDE の Spring 拡張が入ってりゃ補完はしてくれるが、呼び出し階層には出ねえ。橋もまだねえ」

**契約書。** `openapi.yaml` の `operationId: surveyImpact` が `ImpactApi#surveyImpact` になる。これも文字列。
yaml を変えたら生成コードが変わって、実装がコンパイルで落ちる。**ここは落ちてくれるから安全だ**。だがフロントは、

```ts
    const { data } = await api.GET('/nodes', { params: { query: { q } } })
```

```ts
    const { data, error, response } = await api.GET('/impact', { params: { query: { target, minProbability } } })
```

隠「`'/impact'` と `'/nodes'` という文字列だ。TypeScript の型は守ってくれるが、Java の IDE から見れば向こう岸だ。
Java の呼び出し階層で `surveyImpact` を開いても、フロントの `App.tsx` は永遠に出てこねえ。これも橋はまだだ」

八「じゃあ『この API を呼んでる画面はどこだ』は」

隠「フロント側で `'/impact'` を文字列検索するか、生成型 `paths['/impact']` の参照を探す。Java と TypeScript の間に、呼び出し階層をつなぐ道具はねえ」

---

## 五席目：その四、影武者。その五、看板

隠「あと二つは小さいが、知らねえと迷う」

**影武者。** `@Transactional`、`@Validated`、それに `@Retryable`。`GraphStore#replaceWith` が呼ばれるとき、実際には影武者が先に動く。
`JevDependencyJudge#judge` も同じで、Jev が 429 や 5xx を返すと影武者が間隔を倍にしながら三回まで呼び直す。
だが呼び出し階層には影武者は出ない。**三回呼ばれることも、木には一回しか出ない**。「この処理の前後に何が挟まってるか」は、注釈を読むしかない。
だから影響調査のとき、**メソッドの注釈は呼び出し元と同じ重さで読む**。`@Transactional` を外せば、呼び出し元を変えなくても挙動が変わる。

```java
// judgment/JevDependencyJudge.java
	@Retryable(includes = { HttpServerErrorException.class, HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
			maxRetries = 3, delay = 1000, multiplier = 2)
	public Map<String, Double> judge(Ambiguity ambiguity) {
```

```java
// HamonApplication.java
@EnableResilientMethods // @Retryable を有効にする（Jev 呼び出しの再試行）
```

**看板。** `@Profile` は ADR-0003 で hamon から消えた。今 `DependencyJudge` の「実装を探す」をやると、本番の `JevDependencyJudge` と、
`AnalysisApiTest` の `@TestConfiguration` が `@Primary` で返す `FixedDependencyJudge` の二つが出る。本番で生きてるのは注釈の無い方だ。
「実装が二つ出たら、どっちがテスト専用か確かめろ」。看板の読み方は変わったが、読まなきゃ分からねえのは同じだ。

---

## 六席目：で、hamon は

八「ご隠居、聞いてりゃあ、切れる場所が hamon の『あいまいな辺』の話とそっくりですね」

隠「そっくりじゃねえ、同じものだ。IDEAS.md にこう書いてあったろう」

> あいまいな辺：文字列・設定・同名メソッド・リフレクション

隠「IDE の呼び出し階層が切れる場所と、静的解析が断定できねえ場所は、同じ場所だ。そこに Jev で確率をつけようというのが hamon の商売だ」

八「じゃあ hamon は今、その五つを埋めてるんですか」

隠「正直に言うと、半分だ。実際に `POST /api/analysis` でこのリポジトリを解析して、`GET /api/impact?minProbability=0` で聞いてみた。2026-11-01 の実測、67 ノード、69 辺」

隠「まず、IDE と同じ場所で切れてるところ」

```
変更点: judgment.JevDependencyJudge#judge
  → 候補なし。入ってくる辺が一本もねえ。インターフェースの壁で切れてる
変更点: judgment.DependencyJudge#judge
  1.00 analysis.DatabaseSchema#dependenciesOf
  1.00 analysis.JavaSourceAnalysis#addEdges
  1.00 analysis.JavaSourceAnalysis#analyze
  1.00 analysis.MyBatisMapperAnalysis#analyze
  1.00 analysis.Analysis#run
  1.00 analysis.AnalysisController#analyzeSources
  1.00 mcp.HamonTools#analyzeSources
```

隠「インターフェースで聞けば七つ出る。実装で聞けば零。IDE とまったく同じだ。目利きの Jev 本人を直しても、hamon は『誰にも響かねえ』と言う。嘘だ」

隠「次に、Phase 3 のころ切れてて、今はつながったところ」

```
変更点: graph.GraphStore#replaceWith
  1.00 analysis.Analysis#run
  1.00 analysis.AnalysisController#analyzeSources
  1.00 mcp.HamonTools#analyzeSources
変更点: impact.ImpactMapper#traceUpstream
  1.00 impact.ImpactSurvey#surveyImpact
  1.00 impact.ImpactController#surveyImpact
  1.00 mcp.HamonTools#surveyImpact
```

隠「Phase 3 じゃ `replaceWith` の実装には一本も入ってこず、`traceUpstream` は `ImpactController` に届かなかった。
`GraphStore` を具象クラスにして、`ImpactGraph` のインターフェースを捨てて、どっちも Controller まで届くようになった。
**壁を埋めたんじゃなくて、壁を壊した**。実装が一つなら、それが一番安い」

隠「で、これが hamon の商売の実物だ。列から画面の手前まで」

```
変更点: NODE.KIND
  1.00 graph.GraphMapper#insertNode
  1.00 graph.GraphMapper#searchNodes
  1.00 graph.GraphMapper#selectAllNodes
  1.00 impact.ImpactMapper#traceUpstream
  1.00 graph.GraphStore#replaceWith
  1.00 analysis.Analysis#run
  1.00 impact.ImpactSurvey#surveyImpact
  1.00 analysis.AnalysisController#analyzeSources
  1.00 impact.ImpactController#surveyImpact
  1.00 graph.NodeSearchController#searchNodes
  1.00 mcp.HamonTools#analyzeSources
  1.00 mcp.HamonTools#surveyImpact

  path の例: NODE.KIND > graph.GraphMapper#searchNodes > graph.NodeSearchController#searchNodes
```

八「十二。列を一つ変えたら Controller が三つと MCP のツールが二つ」

隠「`node` 表の `kind` 列を右クリックしても、IDE は何も出さねえ。列は Java じゃねえからだ。
hamon は『この列を使ってる SQL 文』から『その SQL 文を呼ぶ Mapper メソッド』、そこから先は普通の Java の呼び出しで Controller まで登る。
川の上の橋は一本でいい。渡った先は IDE と同じ陸だ」

八「Jev はどこで働いたんです」

隠「一か所だけ。`traceUpstream` の SQL に `id` という名前が出てくる。`node` にも `edge` にも `id` 列がある。識別子の一致だけじゃ決められねえ。だから目利きに聞いた」

```
Jev jev-1.14.0 が io.github.akiraisozaki.hamon.impact.ImpactMapper#traceUpstream を判定: {NODE.ID=0.92, EDGE.ID=0.13}
```

隠「SQL を読めば `changed.id`、`dependent.id`、`n.id` と node 側ばかり触ってて、edge の `id` は使ってねえ。目利きの答えはそのとおりだ。
前に流したときは 0.93 と 0.12 だった。呼ぶたびに僅かに揺れる。だから確からしさは小数で持って、閾値で切る」

八「じゃあ残りの穴は」

隠「こうなる」

- **実装の辺**。インターフェース → `@Override` した実装。今残ってるのは `DependencyJudge` → `JevDependencyJudge` の一か所だけ。実装が一つなら確からしさ 1 で張れる。未対応
- **Mapper の辺、SQL → 表・列の辺**。済（Phase 4）。`namespace#id` の名前一致と、辞書との識別子一致
- **設定の辺**。`@ConfigurationProperties` の record → yaml のキー。未対応
- **契約の辺**。`openapi.yaml` の `operationId` → 生成インターフェース → フロントの `api.GET('/impact')`。
  これができると Java の変更から React の画面まで一本でつながる。IDE には絶対できない。未対応
- **生成インターフェース**。`build/generated` を解析対象に入れるかどうかは未決。入れれば `ImpactApi#surveyImpact` がノードになり、契約の辺の片端ができる
- **MCP の `HamonTools`**。これは普通の Java だ。`@McpTool` の注釈が付いてるだけで、呼び出し階層も hamon も素直に追える。上の実測に `HamonTools` が出てるのがその証拠だ

隠「IDE の呼び出し階層は、Java の中では hamon より速くて正確だ。hamon の勝ち目は、**IDE が川に落ちる場所に橋をかける**ことだけだ。
だから Spring 化で呼び出し階層が頼りなくなったのは、おめえにとって悲しい話じゃねえ。**商売の種が増えた**という話だ」

---

## サゲ

八「ご隠居、整理します。呼び出し階層は Spring でも使える。ただし、インターフェース、コンストラクタ、文字列、影武者、看板。この五つで切れる」

隠「そうだ」

八「で、その五つを埋めるのが hamon の仕事」

隠「そうだ」

八「ところで、今の hamon はインターフェースのところで、一か所だけ IDE と同じに切れてると」

隠「そう言った」

八「じゃあ hamon で hamon の影響調査をしても、まだ漏れますね」

隠「漏れる。ただし漏れるのは、目利きの Jev 本人を直したときだけだ」

八「それ、どうやって見つけたんです」

隠「おめえが右クリックで呼び出し階層を開いたのと同じことを、頭の中でやった。それから本当に解析を流して確かめた」

八「……結局、右クリックの神様はまだ現役ですか」

隠「神様は引退しねえ。弟子が育つまでは。ただな、列から Controller までは、もう弟子の方が速い」

お後がよろしいようで。

---

## 付録：切れる 5 か所と hamon の対応状況（2026-11-01）

| # | 切れる場所 | IDE での代わりの手 | hamon の対応 |
|---|---|---|---|
| 1 | インターフェース → 実装 | インターフェース側で階層を開く。「実装を探す」 | 未対応。残りは `DependencyJudge` → `JevDependencyJudge` の 1 か所（`GraphStore` と `ImpactGraph` は具象化・削除で解消、ADR-0003） |
| 2 | コンストラクタ（Spring がリフレクションで呼ぶ） | 型の参照検索でコンストラクタ引数を見る | 対象外（Bean 配線は辺にしない方針。要検討） |
| 3 | 文字列の川 | プラグイン、文字列検索 | MyBatis XML・表・列は済（Phase 4、ADR-0004。あいまいな列は Jev、Phase 5）。yaml キー・OpenAPI・フロントは未対応 |
| 4 | 影武者（`@Transactional`、`@Validated`、`@Retryable`） | 注釈を読む | 対象外（挙動の変化は辺で表しにくい） |
| 5 | 看板 | `@Profile` は削除済み（ADR-0003）。残るのはテストの `@Primary` だけ。注釈の無い方が本番 | 対象外 |
