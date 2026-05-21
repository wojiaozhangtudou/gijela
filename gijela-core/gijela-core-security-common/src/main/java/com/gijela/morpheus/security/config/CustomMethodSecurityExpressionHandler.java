package com.gijela.morpheus.security.config;

import com.gijela.morpheus.security.permission.CustomMethodSecurityExpressionRoot;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;

import java.util.function.Supplier;

/**
 * 将自定义的 ExpressionRoot 注入到方法安全表达式处理器
 */
public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomMethodSecurityExpressionHandler.class);

    @Override
    public EvaluationContext createEvaluationContext(Supplier<? extends Authentication> authentication, MethodInvocation invocation) {
        Authentication auth = authentication.get();
        log.info("[security] createEvaluationContext(Supplier) called for authentication={} method={}", auth == null ? null : auth.getName(), invocation.getMethod().getName());
        MethodSecurityExpressionOperations root = createCustomSecurityExpressionRoot(auth);
        StandardEvaluationContext context = createEvaluationContextInternal(auth, invocation);
        if (getBeanResolver() != null) {
            context.setBeanResolver(getBeanResolver());
        }
        context.setRootObject(root);
        return context;
    }

    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(Authentication authentication, MethodInvocation invocation) {
        log.info("[security] createSecurityExpressionRoot called for authentication={} method={}", authentication == null ? null : authentication.getName(), invocation.getMethod().getName());
        return createCustomSecurityExpressionRoot(authentication);
    }

    private MethodSecurityExpressionOperations createCustomSecurityExpressionRoot(Authentication authentication) {
        CustomMethodSecurityExpressionRoot root = new CustomMethodSecurityExpressionRoot(authentication);
        root.setPermissionEvaluator(getPermissionEvaluator());
        root.setTrustResolver(new AuthenticationTrustResolverImpl());
        root.setRoleHierarchy(getRoleHierarchy());
        return root;
    }
}
