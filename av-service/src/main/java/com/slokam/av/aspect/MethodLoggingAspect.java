package com.slokam.av.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Logs metadata only: arguments and return values may contain credentials or personal data. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class MethodLoggingAspect {
    @AfterThrowing(pointcut = "@annotation(org.springframework.scheduling.annotation.Scheduled)", throwing = "failure")
    public void scheduledFailure(JoinPoint invocation, Throwable failure) {
        Logger log = LoggerFactory.getLogger(invocation.getSignature().getDeclaringType());
        log.error("Scheduled operation failed method={} failureType={}",
                invocation.getSignature().toShortString(), failure.getClass().getName());
        for (StackTraceElement frame : failure.getStackTrace()) log.error("    at {}", frame);
    }

    @Around("execution(public * com.slokam.av.controller..*(..))"
            + " || execution(public * com.slokam.av.service..*(..))"
            + " || execution(public * com.slokam.av.security..*(..))"
            + " || execution(public * com.slokam.av.scheduler..*(..))"
            + " || execution(public * com.slokam.av.mapper..*(..))")
    public Object trace(ProceedingJoinPoint invocation) throws Throwable {
        return traceInvocation(invocation, LoggerFactory.getLogger(invocation.getSignature().getDeclaringType()));
    }

    @Around("execution(* com.slokam.av.repository..*(..))"
            + " || execution(public * org.springframework.data.repository.Repository+.*(..))")
    public Object traceRepository(ProceedingJoinPoint invocation) throws Throwable {
        return traceInvocation(invocation, LoggerFactory.getLogger("com.slokam.av.repository"));
    }

    private Object traceInvocation(ProceedingJoinPoint invocation, Logger log) throws Throwable {
        if (!log.isTraceEnabled()) return invocation.proceed();
        String method = invocation.getSignature().toShortString();
        long start = System.nanoTime();
        String outcome = "success";
        log.trace("Start {}", method);
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            outcome = "failure:" + failure.getClass().getSimpleName();
            throw failure;
        } finally {
            log.trace("Exit {} outcome={} durationMs={}", method, outcome,
                    (System.nanoTime() - start) / 1_000_000.0);
        }
    }
}
