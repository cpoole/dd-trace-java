package test

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.net.ApiResource
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.api.Tags

import static datadog.trace.instrumentation.api.AgentTracer.*

class StripeClientTest extends AgentTestRunner {

  def setupSpec() {
    Stripe.apiKey = "foo"
  }

  def "create customer"() {
    setup:

    GroovyMock(Customer, global: true)

    def retCustomer = ApiResource.GSON.fromJson(
      '{"email":"test@test.com"}',
      Customer.class
    )

    Customer.request(
      ApiResource.RequestMethod.POST,
      String.format("%s%s", Stripe.getApiBase(), "/v1/customers"),
      ["email": "test@test.com"],
      Customer.class,
      null
    ) >> retCustomer

    activateSpan(startSpan("test"), true)

    Customer.create(["email": "test@test.com"])
    println("HELLO")
    def scope = activeScope()
    if (scope) {
      println(activeSpan().getSpanName())
      scope.close()
    }

    expect:

    assertTraces(1) {
      trace(0, 1) {
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
