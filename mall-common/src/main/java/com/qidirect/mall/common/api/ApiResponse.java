package com.qidirect.mall.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qidirect.mall.common.exception.ErrorCode;
import lombok.Data;

/**
 * 统一返回体：{code, msg, data}。code=0 表示成功。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(0);
        response.setMsg("ok");
        response.setData(data);
        return response;
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
