package io.github.akiraisozaki.hamon.analysis;

import org.springframework.test.context.ActiveProfiles;

/** AnalysisApiTest と同じ検証を Spring Data JDBC 実装に対して行う（ADR-0001 の比較） */
@ActiveProfiles("datajdbc")
class AnalysisApiDataJdbcTest extends AnalysisApiTest {
}
