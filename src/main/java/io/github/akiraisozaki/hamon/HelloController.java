package io.github.akiraisozaki.hamon;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 0 の疎通確認用。Phase 1 で OpenAPI から生成した API に置き換える。 */
@RestController
class HelloController {

	@GetMapping("/hello")
	String hello() {
		return "Hello, hamon!";
	}
}
