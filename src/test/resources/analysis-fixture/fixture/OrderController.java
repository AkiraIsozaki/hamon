package fixture;

class OrderController {
	private final OrderService service = new OrderService();

	void cancel() {
		service.cancel();
		String.valueOf(1); // JDK への呼び出し。辺にならない
	}
}
