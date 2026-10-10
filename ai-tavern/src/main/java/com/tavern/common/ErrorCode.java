package com.tavern.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "操作失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(400, "参数错误"),
    USER_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    USER_BANNED(1004, "账号已被封禁"),
    TOKEN_INSUFFICIENT(2001, "积分不足，请先充值或签到"),
    CHARACTER_NOT_FOUND(3001, "角色不存在"),
    SESSION_NOT_FOUND(4001, "会话不存在"),
    ALREADY_CHECKED_IN(5001, "今日已签到"),
    AI_ERROR(6001, "AI 服务异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
