package org.cycle.common.controller;

import lombok.Data;
import java.io.Serializable;

/**
 * 全局统一响应体
 * 包含：code(响应编码)、msg(响应内容)、success(是否成功)、data(返回数据，可选)
 */
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 响应编码（如200成功、500失败、400参数错误等）
     */
    private Integer code;

    /**
     * 响应内容（提示信息）
     */
    private String msg;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 返回数据（可选）
     */
    private T data;

    // ========== 静态构造方法（简化调用） ==========
    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success(null, "操作成功");
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return success(data, "操作成功");
    }

    /**
     * 成功响应（带数据+自定义提示）
     */
    public static <T> Result<T> success(T data, String msg) {
        Result<T> response = new Result<>();
        response.setCode(200);
        response.setMsg(msg);
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    /**
     * 失败响应（自定义编码+提示）
     */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> response = new Result<>();
        response.setCode(code);
        response.setMsg(msg);
        response.setSuccess(false);
        response.setData(null);
        return response;
    }

    /**
     * 失败响应（默认编码500+自定义提示）
     */
    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }

    /**
     * 失败响应（默认编码500+默认提示）
     */
    public static <T> Result<T> fail() {
        return fail(500, "操作失败");
    }
}