package datadog.trace.instrumentation.stripe;

import com.stripe.model.Customer;
import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentSpan;
import lombok.extern.slf4j.Slf4j;

/** Decorate Stripe's spans with relevant contextual information. */
@Slf4j
public class StripeClientDecorator extends ClientDecorator {

  public static final StripeClientDecorator DECORATE = new StripeClientDecorator();

  static final String COMPONENT_NAME = "stripe-sdk";

  @Override
  protected String spanType() {
    return DDSpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {COMPONENT_NAME};
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
  }

  @Override
  protected String service() {
    return COMPONENT_NAME;
  }

  /** Decorate trace based on service execution metadata. */
  public AgentSpan onServiceExecution(
      final AgentSpan span, final Object serviceExecutor, final String methodName) {

    final String simpleClassName =
        serviceExecutor.getClass().getCanonicalName().replaceFirst("^com\\.stripe\\.", "");

    span.setTag(DDTags.RESOURCE_NAME, String.format("%s.%s", simpleClassName, methodName));

    return span;
  }

  /** Annotate the span with the results of the operation. */
  public AgentSpan onResult(final AgentSpan span, Object result) {

    // Nothing to do here, so return
    if (result == null) {
      return span;
    }

    // Provide helpful metadata for some of the more common response types
    span.setTag("stripe.type", result.getClass().getCanonicalName());

    // Instrument the most popular resource types directly
    if (result instanceof Customer) {
      final Customer customer = (Customer) result;
      span.setTag("stripe.email", customer.getEmail());
    }

    // TODO:instrument the remaining types

    return span;
  }
}
