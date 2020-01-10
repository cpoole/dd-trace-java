package datadog.trace.instrumentation.stripe;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.stripe.StripeClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import com.stripe.Stripe;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument Stripe SDK to identify calls as a separate service. */
@AutoService(Instrumenter.class)
public class StripeInstrumentation extends Instrumenter.Default {

  public StripeInstrumentation() {
    super("stripe-sdk");
  }

  /** Match any child class of the base Stripe service classes. */
  @Override
  public ElementMatcher<? super net.bytebuddy.description.type.TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("com.stripe.net.ApiResource").or(named("com.stripe.model.StripeObject")));
  }

  /** Return the helper classes which will be available for use in instrumentation. */
  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".StripeClientDecorator",
    };
  }

  /** Return bytebuddy transformers for instrumenting the Stripe SDK. */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    return singletonMap(
        isMethod().and(isPublic()).and(not(isAbstract())),
        StripeInstrumentation.class.getName() + "$StripeClientAdvice");
  }

  /** Advice for instrumenting Stripe service classes. */
  public static class StripeClientAdvice {

    /** Method entry instrumentation. */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(
        @Advice.This final Object that, @Advice.Origin("#m") final String methodName) {

      final AgentSpan span = startSpan("stripe.sdk");
      DECORATE.afterStart(span);
      DECORATE.onServiceExecution(span, that, methodName);

      return activateSpan(span, true);
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentScope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Object response) {
      if (scope == null) {
        return;
      }
      // If we have a scope (i.e. we were the top-level Stripe SDK invocation),
      try {
        final AgentSpan span = scope.span();

        DECORATE.onResult(span, response);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
      } finally {
        scope.close(); // won't finish the span.
        CallDepthThreadLocalMap.reset(Stripe.class); // reset call depth count
      }
    }
  }
}
