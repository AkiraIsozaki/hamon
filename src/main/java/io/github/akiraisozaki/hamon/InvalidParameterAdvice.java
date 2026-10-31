package io.github.akiraisozaki.hamon;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

/**
 * 生成された API インターフェースの @Validated による検証失敗を 400 にする。
 * （放っておくと Spring はコントローラ由来と判断できず 500 にする）
 */
@RestControllerAdvice
class InvalidParameterAdvice {

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail invalidParameter(ConstraintViolationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
