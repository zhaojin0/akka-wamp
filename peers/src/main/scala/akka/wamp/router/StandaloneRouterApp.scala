package akka.wamp.router

import com.typesafe.config.ConfigFactory


object StandaloneRouterApp extends  App {
  import akka.actor.{Scope => _, _}
  import akka.io._
  import akka.wamp._

  val configFile = System.getProperty("config.file")
  val system = 
    if (configFile != null) 
      ActorSystem("wamp")
    else
      ActorSystem("wamp", ConfigFactory.load(configFile))
  
  system.actorOf(Props(new Binder()), name = "binder")

  class Binder extends Actor with ActorLogging {
    implicit val system = context.system
    implicit val ec = context.system.dispatcher

    val router = context.system.actorOf(Router.props(), "router")
    IO(Wamp) ! Wamp.Bind(router)

    def receive = {
      case signal @ Wamp.Bound(listener, url) =>
        log.info("[{}]    Successfully bound on {}", self.path.name, url)

      case Wamp.CommandFailed(cmd, cause) =>
        context.system.terminate().map[Unit](_ => System.exit(-1))

    }
  }
}
