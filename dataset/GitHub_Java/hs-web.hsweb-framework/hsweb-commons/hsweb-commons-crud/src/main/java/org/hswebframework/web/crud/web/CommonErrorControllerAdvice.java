package org.hswebframework.web.crud.web;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.authorization.exception.UnAuthorizedException;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.exception.NotFoundException;
import org.hswebframework.web.exception.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MediaTypeNotSupportedStatusException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import javax.validation.ConstraintViolationException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Slf4j
public class CommonErrorControllerAdvice {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ResponseMessage<?>> handleException(BusinessException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ResponseMessage<?>> handleException(UnsupportedOperationException e) {
        return Mono.just(ResponseMessage.error("unsupported", e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Mono<ResponseMessage<?>> handleException(UnAuthorizedException e) {
        return Mono.just(ResponseMessage.error(401, "unauthorized", e.getMessage()).result(e.getState()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Mono<ResponseMessage<?>> handleException(AccessDenyException e) {
        return Mono.just(ResponseMessage.error(403, e.getCode(), e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ResponseMessage<?>> handleException(NotFoundException e) {
        return Mono.just(ResponseMessage.error(404, "not_found", e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(ValidationException e) {
        return Mono.just(ResponseMessage.error(400, "illegal_argument", e.getMessage()).result(e.getDetails()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(ConstraintViolationException e) {
        return handleException(new ValidationException(e.getMessage(), e.getConstraintViolations()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(BindException e) {
        return handleException(new ValidationException(e.getMessage(), e.getBindingResult().getAllErrors()
                .stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .map(err -> new ValidationException.Detail(err.getField(), err.getDefaultMessage(), null))
                .collect(Collectors.toList())));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(WebExchangeBindException e) {
        return handleException(new ValidationException(e.getMessage(), e.getBindingResult().getAllErrors()
                .stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .map(err -> new ValidationException.Detail(err.getField(), err.getDefaultMessage(), null))
                .collect(Collectors.toList())));
    }


    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(MethodArgumentNotValidException e) {
        return handleException(new ValidationException(e.getMessage(), e.getBindingResult().getAllErrors()
                .stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .map(err -> new ValidationException.Detail(err.getField(), err.getDefaultMessage(), null))
                .collect(Collectors.toList())));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(javax.validation.ValidationException e) {
        return Mono.just(ResponseMessage.error(400, "illegal_argument", e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Mono<ResponseMessage<?>> handleException(TimeoutException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage.error(504, "timeout", e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ResponseMessage<?>> handleException(RuntimeException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage.error(e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ResponseMessage<?>> handleException(NullPointerException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage.error(e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(IllegalArgumentException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage.error(400, "illegal_argument", e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Mono<ResponseMessage<?>> handleException(MediaTypeNotSupportedStatusException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage
                .error(415, "unsupported_media_type", "不支持的请求类型")
                .result(e.getSupportedMediaTypes()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Mono<ResponseMessage<?>> handleException(NotAcceptableStatusException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage
                .error(406, "not_acceptable_media_type", "不支持的响应类型")
                .result(e.getSupportedMediaTypes()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Mono<ResponseMessage<?>> handleException(MethodNotAllowedException e) {
        log.error(e.getMessage(), e);
        return Mono.just(ResponseMessage
                .error(405, "method_not_allowed", "不支持的请求方法:" + e.getHttpMethod())
                .result(e.getSupportedMethods()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(ServerWebInputException e) {
        Throwable exception=e;
        do {
            exception = exception.getCause();
            if (exception instanceof ValidationException) {
                return handleException(((ValidationException) exception));
            }

        } while (exception != null && exception != e);

        return Mono.just(ResponseMessage.error(400, "illegal_argument", e.getMessage()));
    }

}
