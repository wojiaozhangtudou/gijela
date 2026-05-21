package com.gijela.morpheus.security.token;

/** Token 版本校验扩展点：返回 true 表示版本有效 */
public interface TokenVersionValidator {
    boolean isValid(String username, Integer tokenVersionInToken);
}
