package com.slokam.social.infrastructure.logging;

import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.publishing.PublishFailure;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Traces application bean calls without logging arguments, results, or unsafe exception text. */
@Aspect
@Component
@Slf4j
public class MethodLoggingAspect {
  @Around(
      "execution(public * com.slokam.social..*(..))"
          + " && !within(com.slokam.social.infrastructure.logging..*)"
          + " && !within(com.slokam.social.configuration..*)"
          + " && !@within(org.springframework.boot.context.properties.ConfigurationProperties)"
          + " && !within(com.slokam.social.infrastructure.persistence.*Entity)")
  public Object trace(ProceedingJoinPoint call) throws Throwable {
    String method = call.getSignature().toShortString();
    long started = System.nanoTime();
    log.debug("Method start: {}", method);
    boolean success = false;
    try {
      Object result = call.proceed();
      success = true;
      return result;
    } catch (Throwable failure) {
      log.error(
          "Method failed: {} exceptionType={} reason={}",
          method,
          failure.getClass().getSimpleName(),
          failure instanceof ProviderException || failure instanceof PublishFailure
              ? failure.getMessage()
              : "Operation failed; exception text omitted to protect credentials");
      // Only source locations are logged: exception messages/causes can contain tokens or SQL
      // values.
      if (log.isDebugEnabled())
        for (StackTraceElement frame : failure.getStackTrace())
          if (frame.getClassName().startsWith("com.slokam.social."))
            log.debug("Failure location: {}", frame);
      throw failure;
    } finally {
      log.debug(
          "Method end: {} status={} durationMs={}",
          method,
          success ? "SUCCESS" : "FAILED",
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
  }
}
