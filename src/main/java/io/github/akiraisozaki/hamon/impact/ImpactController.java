package io.github.akiraisozaki.hamon.impact;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.akiraisozaki.hamon.api.ImpactApi;
import io.github.akiraisozaki.hamon.api.model.ImpactItem;

/** openapi.yaml から生成された {@link ImpactApi} の実装。パラメータの検証はインターフェース側の注釈に任せる。 */
@RestController
@RequestMapping("/api") // openapi.yaml の servers.url。生成コードには付かないのでここで付ける
class ImpactController implements ImpactApi {

	private final ImpactSurvey survey;

	ImpactController(ImpactSurvey survey) {
		this.survey = survey;
	}

	@Override
	public ResponseEntity<List<ImpactItem>> surveyImpact(String target, Double minProbability) {
		return ResponseEntity.ok(survey.surveyImpact(target, minProbability));
	}
}
