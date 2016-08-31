package akka.wamp.router

import akka.Done
import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.wamp.Wamp
import akka.wamp.messages.Validator
import akka.wamp.serialization.JsonSerializationFlows

import scala.concurrent.Future
import scala.util.{Failure, Success}


private[wamp] class RouterManager extends Actor  {
  
  implicit val ec = context.system.dispatcher
  implicit val materializer = ActorMaterializer()
  // TODO close the materializer at some point
  
  val iface = context.system.settings.config.getString("akka.wamp.router.iface")
  
  val port = context.system.settings.config.getInt("akka.wamp.router.port")

  val strictUris = context.system.settings.config.getBoolean("akka.wamp.serialization.validate-strict-uris")
  
  val serializationFlows = new JsonSerializationFlows(new Validator(strictUris), materializer)
  
  override def receive: Receive = {
    case cmd @ Wamp.Bind(router) => {
      val binder = sender()
      val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
        Http(context.system).
          bind(iface, port)

      // when serverSource fails because of very dramatic situations 
      // such as running out of file descriptors or memory available to the system
      val reactToTopLevelFailures: Flow[Http.IncomingConnection, Http.IncomingConnection, _] =
        Flow[Http.IncomingConnection].
          watchTermination()((_, termination) => termination.onFailure {
            case cause => 
              binder ! Wamp.ConnectionFailed(cause)
          })

      val handleConnection: Sink[Http.IncomingConnection, Future[Done]] =
        Sink.foreach { conn =>
          val transport = context.actorOf(Transport.props(router, serializationFlows))
          transport ! conn
        }

      val binding: Future[Http.ServerBinding] =
        serverSource.
          via(reactToTopLevelFailures).
          to(handleConnection).
          run()

      binding.onComplete {
        case Success(b) =>
          val host = b.localAddress.getHostString
          val port = b.localAddress.getPort
          val path = context.system.settings.config.getString("akka.wamp.router.path")
          val url = s"ws://$host:$port/$path"
          router ! Wamp.Bound(url)
        case Failure(cause) =>
          router ! Wamp.BindFailed(cause)
      }
    }
  }
}
