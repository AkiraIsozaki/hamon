package fixture;

class Invoker {
	void run() {
		Unknown.cancel(); // Unknown はどこにも無いので型解決できない → 同名メソッド全部へのあいまいな辺
	}
}
