package io.github.akiraisozaki.hamon.impact;

import org.springframework.test.context.ActiveProfiles;

/** ImpactApiTest と同じ検証を Spring Data JDBC 実装に対して行う（ADR-0001 の比較） */
@ActiveProfiles("datajdbc")
class ImpactApiDataJdbcTest extends ImpactApiTest {
}
