# Spring という子の生まれと性分

> 「`new` しているところを探しに行って、あれ、ないぞ？」となる人のための一席。
> 歴史の年代は手元の知識で書いている。細かい月日は公式の履歴で確認すること。

## 枕：あれ、ないぞ

八「ご隠居、どうにも馴染めねえんです。`ImpactController` が `ImpactSurvey` を使ってる。じゃあ誰が `ImpactSurvey` を `new` してるんだと、ソースを全部ひっくり返したんですが、ねえんですよ。
`new ImpactController` もねえ。`new GraphStore` もねえ。幽霊屋敷ですよ」

隠「おめえ、長屋に越してきたとき、井戸を誰が掘ったか探したか」

八「探しませんよ。井戸はあるもんだ」

隠「そういうことだ。おめえの探し方は『井戸を掘った職人』を探してる。Spring の長屋じゃあ、井戸は大家が掘ってある。
探すのは『井戸があるか』と『誰が井戸を使うと言ってるか』だ」

八「……それが『考え方を根本から改めろ』ってやつですか」

隠「改めるのは一か所だけだ。それを教える前に、この子がどういう時代に生まれたか、話しとこう。性分は生まれで決まる」

---

## 一席目：生まれの話、J2EE という大店

隠「おめえの自社フレームワーク、二千年ごろだったな」

八「ええ、そのころです」

隠「ちょうどそのころ、Java の世界には J2EE という大店があった。Sun が旗を振ってな、『業務アプリはこう作れ』と。
中でも EJB という仕組みが、まあ重かった。Bean 一つ作るのにインターフェースを二枚三枚書いて、XML の配備記述子を書いて、
アプリケーションサーバーという大きな蔵に放り込まねえと動かねえ。テストするにも蔵を起こさねえといけねえ」

八「うちのフレームワークもそんなもんでしたよ。だから自前で作ったんだ」

隠「そうだろう。あのころは日本中、いや世界中の現場が『J2EE は重すぎる、自前で軽いのを作ろう』とやった。おめえの会社のオレオレフレームワークも、その一つだ」

八「じゃあ Spring も」

隠「Spring も、その一つだ。二千二年、Rod Johnson というオーストラリア人が本を書いた。『Expert One-on-One J2EE Design and Development』。
本の中身は『EJB なんか使わなくても、普通の Java クラスとちょっとした仕掛けで業務アプリは作れる』。
その『ちょっとした仕掛け』が付録のコードで、それが翌年オープンソースになって Spring になった。二千四年に 1.0 だ」

八「へえ。じゃあ Spring は、あたしの会社のフレームワークと同い年の兄弟みてえなもんですか」

隠「兄弟だ。ただ、片方は世界中の現場で叩かれて育ち、片方はおめえの会社の中で育った。それだけの違いだ」

八「それだけの違いが大きいんですがね」

隠「その Rod Johnson が本で言ったことが、今も Spring の性分になってる。三つ覚えとけ」

1. **POJO（Plain Old Java Object）**。業務のクラスは普通の Java クラスでいい。フレームワークのインターフェースを継がせない。
   だから `JavaSourceAnalysis` はテストで `new` して使える
2. **IoC（Inversion of Control、制御の反転）**。部品が部品を取りに行くんじゃなく、容れ物が部品を部品に渡す。「ない」と言ってる `new` は、ここで反転した
3. **AOP（横断的な処理の分離）**。トランザクションやログや検証を、業務のコードに混ぜず、影武者（プロキシ）に持たせる。`@Transactional` と `@Validated` がそれ

---

## 二席目：性分の話、制御の反転とは何か

八「その『制御の反転』てえのが分からねえ」

隠「おめえの昔のコードはこうだったろう」

```java
// 昔風。使う側が、使われる側を取りに行く
class ImpactController {
	private final ImpactSurvey survey = new ImpactSurvey(new ImpactMapperImpl(new SqlSessionFactory(...)));
}
```

隠「Controller が自分で Mapper を作り、Mapper が自分で接続を作る。『使う側が取りに行く』。
これだと Controller が MyBatis を知ってる。Mapper の作り方が変われば Controller を直す。テストで偽物を渡せねえ」

八「で、Spring だと」

```java
class ImpactController implements ImpactApi {
	private final ImpactSurvey survey;

	ImpactController(ImpactSurvey survey) {   // 「欲しい」と手を出すだけ
		this.survey = survey;
	}
}
```

隠「Controller は『`ImpactSurvey` が欲しい』と手を出すだけ。誰が、いつ、どの実装を作って渡すかは、容れ物の側の都合。これが『反転』だ。
ハリウッドの原則とも言う。『こっちから電話するな、こっちから掛ける』」

八「じゃあ、いったい誰が作ってるんです。幽霊じゃねえんでしょう」

隠「作り手は三種類しかいねえ。これを覚えりゃあ『あれないぞ』は半分消える」

| 作り手 | 印 | hamon での例 |
|---|---|---|
| クラス自身が名乗る | `@Component` `@RestController` `@Configuration` `@Mapper` | `JavaSourceAnalysis`、`GraphStore`、`ImpactController`、`HamonTools` |
| 番頭が作って返す | `@Configuration` の中の `@Bean` メソッド | 本番コードには無い。テストの `AnalysisApiTest` の `@TestConfiguration` で `DependencyJudge` を返す `fixedJudge` だけ |
| 蔵（starter）が勝手に作る | 依存を足しただけ。コードには出てこない | DataSource、SqlSessionFactory、`@Mapper` 三人の実装、MCP サーバー |

隠「`new` を探すのはやめろ。この三つの印を探せ。印がねえクラスは Bean じゃねえ。Bean じゃねえもんは、おめえが `new` する。
hamon で言えば `DependencyGraph` も `DependencyEdge` も `ImpactRow` も `new` してるだろう」

八「ああ、たしかに。`new DependencyEdge(...)` はありました」

隠「ここが肝だ。Spring の長屋には二種類の住人がいる。**役**と**物**だ」

- **役**は、長屋に一人いれば足りる働き手。Controller、Store、Mapper、解析屋、目利き（`JevDependencyJudge`）、取次（`HamonTools`）。依存を持ち、状態を持たず、起動から終了まで一人で勤める。これが Bean
- **物**は、仕事のたびに生まれて消える品物。グラフ、辺、行、設定値の塊。これは `new` する。record で作ってあるのがだいたいこれ

隠「おめえが探して見つからなかったのは全部**役**だ。**物**は今でも `new` してる。
だから『考え方を根本から改める』てえのは言いすぎで、改めるのは『役は `new` しない、印をつけて手を出す』の一か所だけだ」

---

## 三席目：複雑さの総量の話

八「でもね、ご隠居。複雑さの総量は変わらねえって言うじゃねえですか。`new` を書かねえ分、どこかに複雑さが隠れてる。それが見えねえのが気持ち悪い」

隠「おめえ、いいことを言った。半分当たってる」

八「半分ですか」

隠「複雑さは確かに Spring 側に移った。ただ移った先で、二つのことが起きてる。
まず**標準になった**。おめえの会社のフレームワークは、おめえの会社の人しか知らねえ。配線の仕方も、間違いの直し方も、辞めた先輩の頭の中だ。
Spring の配線は世界中の人間が同じ作法で書いて、同じ落とし穴に落ちて、同じ直し方を書き残してる。
これは複雑さが減ったんじゃなくて、**一度覚えれば全部の現場で使える複雑さ**になったということだ」

八「なるほど。うちのは覚えても他じゃ使えなかった」

隠「もう一つは**配線が消えたんじゃなくて、宣言になった**。昔は『A を作って B に渡して C に渡す』という手順を書いた。
今は『A はこういう役だ』『C は B が欲しい』という事実だけ書く。手順は Spring が導く。手順を書くと順番を間違える。事実を書くなら順番がねえ。間違いの種類がひとつ減る」

八「じゃあ悪いとこはねえんですか」

隠「ある。正直に言おう」

- **見えない。** 配線がコードにねえから、`new` を追う読み方が効かねえ。これはおめえの悩みそのものだ
- **スタックトレースが長い。** 影武者が何枚も挟まるから、例外のトレースの八割が Spring の中だ。自分のクラス名が出てる行だけ読む癖をつけろ
- **魔法が外れたときに難しい。** 動いてるときは楽だが、Bean が二つあって選べねえ、`@Primary` で立てた片方しか見てなかった、影武者が `this` 呼びで効かねえ。
  こういうときは「Spring がどう考えたか」を知らねえと直せねえ

隠「だから、Spring と付き合うってのは『魔法を信じる』ことじゃねえ。『魔法の手順を一度は知っておいて、普段は忘れてる』ことだ」

---

## 四席目：XML 地獄と Boot の生まれ

八「ところで `application.yml` には DB の繋ぎ先も書いてねえ。これも最初からそうだったんですか」

隠「いや、Spring にも暗い時代があった。二千四年から八年ごろ、配線は全部 XML に書いた」

```xml
<!-- 昔の Spring。こういうのが何百行 -->
<bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
  <property name="url" value="jdbc:oracle:thin:@db:1521/FREEPDB1"/>
</bean>
<bean id="impactMapper" class="...">
  <property name="sqlSessionFactory" ref="sqlSessionFactory"/>
</bean>
<bean id="impactController" class="...ImpactController">
  <constructor-arg ref="impactGraph"/>
</bean>
```

八「うわ、これじゃ EJB の配備記述子と変わらねえ」

隠「みんなそう言った。XML 地獄とな。『EJB の重さから逃げてきたのに、Spring 自身が重くなった』と。そこで直しが二回入る」

- 二千七年、Spring 2.5 で注釈が入った。`@Autowired`、`@Component`。クラスに印をつけりゃ XML に書かなくていい
- 二千九年、Spring 3.0 で Java 設定が入った。`@Configuration` と `@Bean`。XML を Java で書けるようになった。テストの `FixedJudgeConfiguration` はこの子孫。
  二千十一年の 3.1 で `@Profile` も入った。hamon は 2b で使い、2c で消した（ADR-0003）

隠「それでも『Spring を始めるには設定を百行書く』は残った。そこで二千十四年、**Spring Boot** だ」

八「Boot ってのは Spring と別物なんですか」

隠「Spring の上に乗った『支度係』だ。思想は Ruby on Rails から来た**設定より規約（convention over configuration）**。
『みんなが書く設定なら、最初から書いてあることにしよう。変えたい人だけ書け』。
だから `application.yml` には**既定と違うところだけ**書く。hamon の yaml が短いのは、怠けてるんじゃなくて、既定で足りてるからだ」

八「DB の繋ぎ先を環境変数で渡すのは」

隠「これも時代だ。二千十年代、アプリはクラウドのコンテナで動かすもんになった。同じ jar を開発・試験・本番で動かし、違いは環境変数だけで渡す。
**Twelve-Factor App** てえ作法だ。Boot は最初からこれに合わせて、環境変数を設定として読む。
hamon の Dev Container が `SPRING_DATASOURCE_URL` で渡してるのは、まさにその作法に乗ってる」

八「じゃあ hamon の yaml にある `spring.ai.mcp.server.*` とか `mybatis.configuration.*` とか `hamon.analysis.*`、`hamon.jev.*` は」

隠「全部『既定と違うところ』か『Spring が知らねえ自前の値』だ。Spring の既定で済むものは一行もねえ。それが Boot との正しい付き合い方だ」

年表にするとこうなる。

| 年 | 出来事 | hamon に残っている痕跡 |
|---|---|---|
| 1999〜2002 | J2EE / EJB 2.x の全盛。重い。各社が自前フレームワークを作る | 開発者の「2000 年頃の自社フレームワーク」 |
| 2002 | Rod Johnson の本。POJO + IoC + AOP | コンストラクタ注入、`@Transactional` |
| 2003〜2004 | Spring オープンソース化、1.0 | |
| 2004〜2008 | XML 設定の時代 | （ない。幸い） |
| 2007 | Spring 2.5、注釈 | `@Component`、`@Autowired`（テストのみ） |
| 2009 | Spring 3.0、Java 設定 | `@Configuration` + `@Bean`（今はテストの `@TestConfiguration` にだけ） |
| 2011 | Spring 3.1、プロファイル | `@Profile`（2b で使い、2c で削除。ADR-0003） |
| 2014 | Spring Boot 1.0、設定より規約、starter | `build.gradle` の starter、短い `application.yml` |
| 2022 | Boot 3、Java 17 と Jakarta EE | `jakarta.validation` の import |
| 2025 | Boot 4、Framework 7 | このリポジトリ（Boot 4.1.2）。`@Retryable` が本体に入った（`org.springframework.resilience`） |
| 2025 | Spring AI 2.0 | MCP サーバー（`@McpTool`、ADR-0006） |

---

## 五席目：付き合い方の作法

八「で、結局あたしは『あれないぞ』のとき、どうすればいいんです」

隠「作法を五つ渡す。これだけでいい」

**その一、問いを変える。**
「どこで `new` してる」ではなく「誰がこの型を**欲しがってて**、誰がこの型を**名乗ってる**か」。
欲しがる側はコンストラクタの引数、名乗る側は注釈か `@Bean`。IDE で型の「実装を探す」を使う。
`DependencyJudge` の実装を探せば `JevDependencyJudge` と、テストの `FixedDependencyJudge` が出て、テストの `@Primary` が見える。

**その二、実際に聞く。** Spring は自分の頭の中を見せてくれる。

```
./gradlew bootRun --args='--debug'
```

起動ログに CONDITIONS EVALUATION REPORT が出る。「この自動構成は条件が合ったから有効にした、これは合わなかったから捨てた」が一覧になる。
「蔵が勝手に作った Bean」の正体はここで分かる。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, beans
```

こう足して `/actuator/beans` を叩けば、今生きてる Bean の全部と、誰が誰に依存してるかが JSON で出る。
hamon は Actuator がもう入っているので yaml に足すだけ（未検証。キー名は Boot のリファレンスで確認）。

**その三、役と物を分ける。**
新しいクラスを書くとき、まず「これは役か、物か」を決める。役なら `@Component` をつけてコンストラクタで依存を受け取る。物なら record にして `new` する。
迷ったら「これが二つあったら困るか」。困るなら役。

**その四、役を `new` しない、`static` で取りに行かない。**
プロダクションコードで `new GraphStore(mapper)` と書いた瞬間、影武者が外れて `@Transactional` が効かなくなる。シングルトンの `getInstance()` も同じ。
欲しいなら手を出す。これだけは自社フレームワーク時代の癖と真っ向ぶつかるから、意識して直す。

**その五、Spring が転んだら「Spring がどう考えたか」のログを読む。**
起動失敗のメッセージは実はよく書けてる。「`DependencyJudge` 型の Bean が二つ見つかった」「`hamon.jev` の束縛に失敗した」と、だいたい一行で原因を言ってる。
トレースの下の方じゃなくて、`APPLICATION FAILED TO START` の直後の `Description` と `Action` を読む。

八「五つですか。覚えられるかな」

隠「一つで足りる。『**手を出せば来る。来ないなら、名乗ってる者がいない**』。これだけだ」

---

## サゲ

八「ご隠居、だんだん分かってきました。Spring てえのは、あたしの会社のフレームワークと同じ時代に、同じ悩みから生まれた兄弟で」

隠「そうだ」

八「ただ兄弟のくせに、こっちは世界中で揉まれて、配線を手順じゃなくて宣言で書くようになって、XML で一度太って、注釈と Boot で痩せて」

隠「よく覚えた」

八「で、あたしは `new` を探すんじゃなくて、印と手を探せばいい」

隠「そういうことだ」

八「じゃあひとつ聞きたいんですが。うちの会社のフレームワーク、あれも Spring みてえに育ててりゃあ、今ごろ世界で使われてましたかね」

隠「育ててりゃあな」

八「なんで育たなかったんでしょう」

隠「おめえの会社のフレームワークは、おめえの会社の都合を全部知ってた。Spring は、誰の都合も知らねえ」

八「知らねえ方が強いんですか」

隠「知らねえから、誰にでも『手を出せば来る』と言える。おめえの会社の都合は、`application.yml` に書けば済む」

八「……うちのフレームワークは、都合が yaml じゃなくてコードの中に埋まってた」

隠「それをな、二十年経って、自分で掘り返す道具を作ってるのが」

八「hamon ですか」

隠「おめえの影響調査は、おめえの会社の二十年の影響調査だ」

お後がよろしいようで。
