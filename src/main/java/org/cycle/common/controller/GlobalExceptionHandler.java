package org.cycle.common.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 全局异常处理器：将常见异常映射为统一的 Result 响应体，便于前端识别处理
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Result<?> handleBadRequest(IllegalArgumentException ex) {
        logger.warn("参数校验失败: {}", ex.getMessage());
        return Result.fail(400, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public Result<?> handleConflict(IllegalStateException ex) {
        logger.warn("状态冲突: {}", ex.getMessage());
        return Result.fail(409, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Result<?> handleException(Exception ex) {
        logger.error("服务器异常", ex);
        return Result.fail(500, "服务器异常: " + ex.getMessage());
    }
}

