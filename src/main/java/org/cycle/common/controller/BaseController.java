package org.cycle.common.controller;

import org.springframework.web.bind.annotation.RestController;

/**
 * 抽象Controller：统一响应格式封装
 * 所有业务Controller继承此类，自动复用统一响应方法
 */
@RestController // 标记为RestController，子类无需重复加
public abstract class BaseController {

    // ========== 通用响应方法（子类直接调用） ==========
    /**
     * 成功响应（无数据）
     */
    protected <T> Result<T> success() {
        return Result.success();
    }

    /**
     * 成功响应（带数据）
     */
    protected <T> Result<T> success(T data) {
        return Result.success(data);
    }

    /**
     * 成功响应（带数据+自定义提示）
     */
    protected <T> Result<T> success(T data, String msg) {
        return Result.success(data, msg);
    }

    /**
     * 失败响应（自定义编码+提示）
     */
    protected <T> Result<T> fail(Integer code, String msg) {
        return Result.fail(code, msg);
    }

    /**
     * 失败响应（默认编码500+自定义提示）
     */
    protected <T> Result<T> fail(String msg) {
        return Result.fail(msg);
    }

    /**
     * 失败响应（默认编码500+默认提示）
     */
    protected <T> Result<T> fail() {
        return Result.fail();
    }
}