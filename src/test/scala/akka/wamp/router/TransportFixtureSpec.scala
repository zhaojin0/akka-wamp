package akka.wamp.router

import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit._
import akka.testkit._
import akka.wamp.messages.Validator
import akka.wamp.serialization.JsonSerializationFlows
import org.scalatest._

/**
  * The SUT - System Under Test of this tests suite is meant to be 
  * the ``router.Transport.httpRoute``
  * 
  * Test methods of this suite are setup with fresh fixture instance
  * providing the SUT and any necessary DOC - Depends-on Components
  * Custom test doubles are NOT replacing DOCs in this tests suite,
  * but rather Akka TestKit facilities are being used.
  * 
  * httpRoute is wrapped by ``testkit.Route.seal()`` when HTTP rejections
  * need to be checked
  */
class TransportFixtureSpec 
  extends fixture.FlatSpec 
    with MustMatchers with BeforeAndAfterAll
    with ScalatestRouteTest
    with ParallelTestExecution
    with SequentialIdGenerators
{
  // It can be whatever URL
  val URL = "http://127.0.0.1:8080/ws"
  
  def withHttpHandler(route: Route)(testScenario: (WSProbe) => Unit) = {
    val transport = WSProbe()
    WS(URL, transport.flow, List("wamp.2.json")) ~> route ~> check {
      testScenario(transport)
    }
  }

  case class FixtureParam(httpRoute: Route)
  
  override def withFixture(test: OneArgTest) = {
    val wampRouter = TestActorRef[Router](Router.props(scopes))
    
    val strictUris = system.settings.config.getBoolean("akka.wamp.serialization.validate-strict-uris")
    val serializationFlows = new JsonSerializationFlows(new Validator(strictUris), materializer)
    
    val transport = TestActorRef[Transport](Transport.props(wampRouter, serializationFlows))
    
    // httpRoute is the SUT - System Under Test
    val httpRoute: Route = transport.underlyingActor.httpRoute
    
    val theFixture = FixtureParam(httpRoute)
    try {
      withFixture(test.toNoArgTest(theFixture))
    }
    finally {
      system.stop(wampRouter)
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}