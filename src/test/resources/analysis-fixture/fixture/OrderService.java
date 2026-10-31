package fixture;

class OrderService {
	private final OrderRepository repository = new OrderRepository();

	void cancel() {
		repository.updateStatus();
		audit();
	}

	void audit() {
	}
}
