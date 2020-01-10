package test

import com.stripe.model.Customer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.api.Tags

import static datadog.trace.instrumentation.api.AgentTracer.*

class StripeClientTest extends AgentTestRunner {

  Customer stripeCustomer = Mock()

  def setupSpec() {

  }

  def "create customer"() {
    setup:
    activateSpan(startSpan("test"), true)

    stripeCustomer.create(["email": "test@test.com"])

    def scope = activeScope()
    if (scope) {
      scope.close()
    }

    expect:

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "test"
          resourceName "test"
          errored false
          parent()
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "stripe-sdk"
          operationName "stripe-sdk"
          resourceName "model.Customer"
          errored false
          tags {
            "$Tags.COMPONENT" "stripe-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "stripe.email" "test@test.com"
            defaultTags()
          }
        }
      }
    }
  }
}
