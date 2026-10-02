package com.pricewatch.common;

import com.pricewatch.account.SignUpRequiredException;
import com.pricewatch.product.ProductNotFoundException;
import com.pricewatch.scraper.ScrapeFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Turns exceptions into RFC 7807 "problem details" JSON; the frontend shows the {@code detail} text. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail notFound(ProductNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Product not found");
        return problem;
    }

    /** 403 with reason SIGN_UP_REQUIRED, so the app can offer the sign-up card instead of an error. */
    @ExceptionHandler(SignUpRequiredException.class)
    public ProblemDetail signUpRequired(SignUpRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Sign up to keep tracking");
        problem.setProperty("reason", "SIGN_UP_REQUIRED");
        return problem;
    }

    @ExceptionHandler(ScrapeFailedException.class)
    public ProblemDetail scrapeFailed(ScrapeFailedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle(switch (e.getReason()) {
            case BLOCKED -> "The shop blocks automated price checks";
            case TEMPORARY -> "The shop could not be reached";
            case PAGE_GONE -> "The product page no longer exists";
            case NO_PRICE, INVALID -> "Could not read the price";
        });
        problem.setProperty("reason", e.getReason());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
        problem.setTitle("Invalid request");
        return problem;
    }
}
