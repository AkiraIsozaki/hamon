package io.github.akiraisozaki.hamon.judgment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /v1/systemone の本文。Ambiguity を Jev の形（state + 候補ごとの Noul 質問）にする。
 * 質問 ID はモデルには送られないので candidate_0, candidate_1 … と番号にし、答えは同じ番号で引く
 */
record JevRequest(String model, Map<String, String> state, Map<String, Question> questions) {

	/** Noul 質問。instructions は質問文と候補の説明を 1 つのオブジェクトにしたもの（構造化 instructions） */
	record Question(String type, Map<String, String> instructions) {
	}

	/** 応答。answers のキーは質問 ID。usage など他のフィールドは読まない */
	record Response(String model, Map<String, Answer> answers) {
	}

	record Answer(double noul) {
	}

	static JevRequest of(String model, Ambiguity ambiguity) {
		Map<String, Question> questions = new LinkedHashMap<>();
		List<Ambiguity.Candidate> candidates = ambiguity.candidates();
		for (int i = 0; i < candidates.size(); i++) {
			Map<String, String> instructions = new LinkedHashMap<>();
			instructions.put("question", ambiguity.question());
			instructions.putAll(candidates.get(i).fields());
			questions.put(questionId(i), new Question("noul", instructions));
		}
		return new JevRequest(model, ambiguity.state(), questions);
	}

	/** 候補ごとの答えを dependency ノード名に戻す */
	static Map<String, Double> probabilities(Ambiguity ambiguity, Response response) {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		List<Ambiguity.Candidate> candidates = ambiguity.candidates();
		for (int i = 0; i < candidates.size(); i++) {
			Answer answer = response.answers().get(questionId(i));
			if (answer == null) {
				throw new IllegalStateException("Jev の応答に " + questionId(i) + " がない: " + response);
			}
			probabilities.put(candidates.get(i).dependency(), answer.noul());
		}
		return probabilities;
	}

	private static String questionId(int index) {
		return "candidate_" + index;
	}
}
