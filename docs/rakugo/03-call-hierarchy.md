# 右クリックの神様（IDE の呼び出し階層は Spring でも使えるか）

> 結論: 使える。ただし 5 か所で木が切れる。その 5 か所は hamon の「あいまいな辺」そのものであり、
> 今の hamon（Phase 3）はそのうち「インターフェースの壁」で IDE と同じ場所で切れている（2026-11-01 に実測）。

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

隠「まず安心させとこう。普通の呼び出しは切れねえ。`ImpactRow#toItem` の上で呼び出し階層を開けば、`ImpactController#surveyImpact` が出る。
`JavaSourceAnalysis#analyze` を開けば `AnalysisController#analyzeSources` が出る。Spring は普通の Java を普通に呼ぶ。ここは昔と同じだ」

八「それは良かった」

隠「メソッド参照も大抵は追える。`ImpactGraphConfiguration` の `mapper::traceUpstream` は、`ImpactMapper#traceUpstream` の呼び出し元として IDE が拾う。
ラムダの中の呼び出しも拾う。だから木の幹は生きてる」

---

## 二席目：切れる場所、その一、インターフェースの壁

八「じゃあどこで切れるんです」

隠「`MyBatisGraphStore#replaceWith` の上で呼び出し階層を開いてみろ」

`analysis/AnalysisController.java`

```java
	private final GraphStore store;   // インターフェース

	public ResponseEntity<AnalysisResult> analyzeSources() {
		DependencyGraph graph = analysis.analyze(properties.sourceRoots());
		store.replaceWith(graph);      // コード上は GraphStore#replaceWith を呼んでいる
```

隠「Controller が呼んでるのは `GraphStore#replaceWith`、インターフェースの方だ。実装クラスのメソッドは誰も名指ししてねえ。
IDE によっては『この実装の呼び出し元』として空になるし、親メソッドの呼び出し元まで出してくれる IDE でも、
**実装が二つあるときにどっちが本当に呼ばれてるか**は IDE には分からねえ」

八「じゃあどうすれば」

隠「インターフェースの方、`GraphStore#replaceWith` で開く。すると Controller が出る。
実装側から辿るときは、まず『オーバーライド元へ』でインターフェースに上がって、そこから階層を開く。
どの実装が生きてるかは、`@Profile` と `application.yml` の看板を読む」

八「それは昔もあった話じゃねえですか。インターフェースは」

隠「あった。ただ昔はインターフェースが少なかった。Spring は DI のために**役はみんなインターフェース越し**になる。だから壁に当たる回数が十倍になる。慣れの問題だ」

---

## 三席目：その二、コンストラクタに呼び出し元がない

八「もう一つ気味の悪いのが、`ImpactController` のコンストラクタで呼び出し階層を開いても、誰も出てこねえ」

隠「それは本当に誰も呼んでねえ。呼んでるのは Spring がリフレクションでだ。おめえのコードにはいねえ」

八「じゃあ『この Controller は誰が使ってるんだ』はどう調べる」

隠「問いが違う。Controller を**使う**のは Spring MVC で、HTTP で叩かれる。だから調べるのは『どの URL が割り当たってるか』だ。
`@RequestMapping` を読む。hamon なら契約書 `openapi.yaml` を読めば一覧になってる」

隠「で、ここからが Spring らしいところだ。『この Bean を誰が受け取ってるか』は、呼び出し階層じゃなくて**型の利用箇所**で探す。
`GraphStore` の型を右クリックして『参照を検索』。すると `AnalysisController` のコンストラクタ引数が出る。それが『受け取り手』だ。
Spring では**呼び出し元を探すときは、メソッドじゃなくて型で探す場面が増える**。これが一番大きな癖の違いだ」

| 探したいこと | 昔の道具 | Spring での道具 |
|---|---|---|
| このメソッドを呼ぶのは誰 | 呼び出し階層 | 呼び出し階層（インターフェース側で開く） |
| この部品を使うのは誰 | `new` を検索 | 型の参照検索。コンストラクタ引数が受け取り手 |
| この部品の本物はどれ | `new` の先を見る | 「実装を探す」+ `@Profile` と `@Bean` を読む |
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

**表とカラム。** `node_path` という列を変えたらどこに響くか。SQL の中の `node_path`、`ImpactRow` の `nodePath`、yaml の `map-underscore-to-camel-case`。
この三つは**名前の変換規則**でつながっている。IDE は知らない。

**設定。** `hamon.analysis.source-roots` を改名したら。

```yaml
hamon:
  analysis:
    source-roots:
```

```java
@ConfigurationProperties("hamon.analysis")
public record AnalysisProperties(List<Path> sourceRoots) {
```

隠「yaml のキーと record の引数名は、ハイフンとキャメルの変換で結ばれてる。文字列だ。IDE の Spring 拡張が入ってりゃ補完はしてくれるが、呼び出し階層には出ねえ」

**契約書。** `openapi.yaml` の `operationId: surveyImpact` が `ImpactApi#surveyImpact` になる。これも文字列。
yaml を変えたら生成コードが変わって、実装がコンパイルで落ちる。**ここは落ちてくれるから安全だ**。だがフロントは、

```ts
const { data, error, response } = await api.GET('/impact', { params: { query: { target, minProbability } } })
```

隠「`'/impact'` という文字列だ。TypeScript の型は守ってくれるが、Java の IDE から見れば向こう岸だ。
Java の呼び出し階層で `surveyImpact` を開いても、フロントの `App.tsx` は永遠に出てこねえ」

八「じゃあ『この API を呼んでる画面はどこだ』は」

隠「フロント側で `'/impact'` を文字列検索するか、生成型 `paths['/impact']` の参照を探す。Java と TypeScript の間に、呼び出し階層をつなぐ道具はねえ」

---

## 五席目：その四、影武者。その五、看板

隠「あと二つは小さいが、知らねえと迷う」

**影武者。** `@Transactional` と `@Validated`。`MyBatisGraphStore#replaceWith` が呼ばれるとき、実際には影武者が先に動く。
だが呼び出し階層には影武者は出ない。「この処理の前後に何が挟まってるか」は、注釈を読むしかない。
だから影響調査のとき、**メソッドの注釈は呼び出し元と同じ重さで読む**。`@Transactional` を外せば、呼び出し元を変えなくても挙動が変わる。

**看板。** `@Profile`。IDE は `MyBatisGraphStore` も `DataJdbcGraphStore` も両方「実装」として出す。だが本番で生きてるのは片方。
どっちかは `application.yml` の `spring.profiles.default` と起動引数で決まる。コードだけ読んでも分からない。「実装が二つ出たら、看板を確かめろ」。

---

## 六席目：で、hamon は

八「ご隠居、聞いてりゃあ、切れる場所が hamon の『あいまいな辺』の話とそっくりですね」

隠「そっくりじゃねえ、同じものだ。IDEAS.md にこう書いてあったろう」

> あいまいな辺：文字列・設定・同名メソッド・リフレクション

隠「IDE の呼び出し階層が切れる場所と、静的解析が断定できねえ場所は、同じ場所だ。そこに Jev で確率をつけようというのが hamon の商売だ」

八「じゃあ hamon は今、その五つを埋めてるんですか」

隠「正直に言うと、まだ埋めてねえ。それどころか、**今の hamon は IDE と同じ場所で切れてる**」

八「えっ」

隠「`JavaSourceAnalysis` は `graph.traceUpstream(...)` を型解決して、呼び先を `ImpactGraph#traceUpstream` にする。インターフェースの方だ。
で、`ImpactMapper#traceUpstream` への辺は `ImpactGraphConfiguration#myBatisImpactGraph` からメソッド参照で張られる。
実際に `src/main/java` を解析して、`traceUpstream` と `replaceWith` を終点にする辺を全部出すとこうなる（2026-11-01 実測）」

```
ImpactController#surveyImpact               ──1.0──▶ ImpactGraph#traceUpstream        （インターフェース。ここで止まる）
AnalysisController#analyzeSources           ──1.0──▶ GraphStore#replaceWith           （同上）
ImpactGraphConfiguration#myBatisImpactGraph ──1.0──▶ ImpactMapper#traceUpstream
ImpactGraphConfiguration#dataJdbcImpactGraph──1.0──▶ ImpactRepository#traceUpstream
```

隠「`MyBatisGraphStore#replaceWith` と `DataJdbcGraphStore#replaceWith` に入る辺は一本もねえ。
`ImpactMapper#traceUpstream` を変更点にして影響を調べると、`ImpactGraphConfiguration` は出るが、`ImpactController` は出ねえ。インターフェースの壁で切れてる」

八「それじゃ IDE と変わらねえ」

隠「今はな。だから次にやることが見えてくる。**Spring の辺**を足すんだ」

- **実装の辺**。`MyBatisGraphStore#replaceWith` が `GraphStore#replaceWith` を `@Override` してたら、インターフェース → 実装 に辺を張る。
  実装が一つなら確からしさ 1。複数なら `@Profile` を読んで決めるか、Jev に聞く
- **Mapper の辺**。`@Mapper` インターフェースのメソッド → 同名 `id` の XML の SQL。namespace と id の一致で確からしさ 1。Phase 4 の予定どおり
- **SQL → 表・カラムの辺**。SQL パーサで `node`, `edge`, `node_path` を拾う。Phase 4
- **設定の辺**。`@ConfigurationProperties` の record → yaml のキー
- **契約の辺**。`openapi.yaml` の `operationId` → 生成インターフェース → フロントの `api.GET('/impact')`。
  これができると Java の変更から React の画面まで一本でつながる。IDE には絶対できない

隠「IDE の呼び出し階層は、Java の中では hamon より速くて正確だ。hamon の勝ち目は、**IDE が川に落ちる場所に橋をかける**ことだけだ。
だから Spring 化で呼び出し階層が頼りなくなったのは、おめえにとって悲しい話じゃねえ。**商売の種が増えた**という話だ」

---

## サゲ

八「ご隠居、整理します。呼び出し階層は Spring でも使える。ただし、インターフェース、コンストラクタ、文字列、影武者、看板。この五つで切れる」

隠「そうだ」

八「で、その五つを埋めるのが hamon の仕事」

隠「そうだ」

八「ところで、今の hamon はインターフェースのところで IDE と同じに切れてると」

隠「そう言った」

八「じゃあ hamon で hamon の影響調査をしても、まだ漏れますね」

隠「漏れる」

八「それ、どうやって見つけたんです」

隠「おめえが右クリックで呼び出し階層を開いたのと同じことを、頭の中でやった。それから本当に解析を流して確かめた」

八「……結局、右クリックの神様はまだ現役ですか」

隠「神様は引退しねえ。弟子が育つまでは」

お後がよろしいようで。

---

## 付録：切れる 5 か所と hamon の対応状況

| # | 切れる場所 | IDE での代わりの手 | hamon の対応 |
|---|---|---|---|
| 1 | インターフェース → 実装 | インターフェース側で階層を開く。「実装を探す」 | 未対応。実装の辺を足す（候補: Phase 4 か 5） |
| 2 | コンストラクタ（Spring がリフレクションで呼ぶ） | 型の参照検索でコンストラクタ引数を見る | 対象外（Bean 配線は辺にしない方針。要検討） |
| 3 | 文字列の川（MyBatis XML、カラム名、yaml キー、OpenAPI、フロント） | プラグイン、文字列検索 | Phase 4（XML・SQL）、以降（設定・契約） |
| 4 | 影武者（`@Transactional`、`@Validated`） | 注釈を読む | 対象外（挙動の変化は辺で表しにくい） |
| 5 | 看板（`@Profile`） | yaml と起動引数を読む | 未対応。実装が複数のときの確からしさに使える |
