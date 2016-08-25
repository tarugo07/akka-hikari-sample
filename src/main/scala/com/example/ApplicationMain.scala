package com.example

import akka.actor.Actor.Receive
import akka.actor._
import akka.actor.{ Actor, ActorLogging, ActorRefFactory, ActorSelection, ActorSystem, OneForOneStrategy, Props }

object ApplicationMain extends App {
  val system = ActorSystem("MyActorSystem")
//  val printer = system.actorOf(Props(new PrinterSupervisor), "printer-supervisor")
//  val pingActor = system.actorOf(PingActor.props, "pingActor")
//  pingActor ! PingActor.Initialize
  // This example app will ping pong 3 times and thereafter terminate the ActorSystem - 
  // see counter logic in PingActor
//  system.awaitTermination()
//  printer ! "Hello World !"
//  printer ! "Hello World 2 !"
//  Thread.sleep(1000)
//  printer ! 1
//  Thread.sleep(1000)
//  printer ! "Hi !"
//  system.awaitTermination()
//  system.terminate()

//  val dataSourceSupervisor = system.actorOf(DataSourceSupervisor.props, DataSourceSupervisor.name)

  val client = system.actorOf(IdWorkerClientForRDB.props, IdWorkerClientForRDB.name)

  val generator = system.actorOf(Generator.props(client), Generator.name)

  (0 to 10000).foreach(_ => generator ! Generator.GenerateId)



//  generator ! Generator.GenerateId
//  generator ! Generator.GenerateId
//  generator ! Generator.GenerateId
//  generator ! Generator.GenerateId



//  val connector = system.actorOf(Connector.props, Connector.name)
//
//  connector ! Connector.GetConnection
//  connector ! Connector.GetConnection
//  connector ! Connector.GetConnection
//  connector ! Connector.GetConnection

  Thread.sleep(10000)

  system.terminate()
}

object Generator {
  val name = "generate-id-actor"

  case object GenerateId

  def props(client: ActorRef): Props = Props(new Generator(client))
}

class Generator(client: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Generator.GenerateId =>
      client ! IdWorkerClientProtocol.GenerateId(self)
    case IdWorkerClientProtocol.IdGenerated(value) =>
      log.info("Get Id: {}", value)
  }
}

class Connector extends Actor with ActorLogging {

//  val dataSource = context.actorOf(DataSource.props, DataSource.name)

  def selectDataSourceSupervisor(implicit context: ActorRefFactory): ActorSelection =
    context.actorSelection("/user/" + DataSourceSupervisor.name)

  override def receive: Receive ={
    case Connector.GetConnection =>
      log.info("request to get connection")
//      dataSource ! DataSourceProtocol.GetConnection
      selectDataSourceSupervisor ! DataSourceProtocol.GetConnection
    case DataSourceProtocol.ConnectionResult(conn) =>
      log.info(s"got connection: $conn")
//      if (!conn.isClosed) {
//        conn.close()
//      }
  }

}

object Connector {
  case object GetConnection

  val name = "connector"
  def props: Props = Props(new Connector)
}

//class Printer extends Actor with ActorLogging {
//  override def receive: Receive = {
//    case msg: String =>
//      log.info(s"receive message: $msg")
////      context.stop(self)
//    case _ =>
//      throw new Exception("unknown message")
//  }
//
//  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
//    super.preRestart(reason, message)
//    log.info(s"Printer Pre Restart: ${reason.getMessage}")
//  }
//  override def postRestart(reason: Throwable): Unit = {
//    super.postRestart(reason)
//    log.info(s"Printer Post Restart: ${reason.getMessage}")
//  }
//
//  override def postStop(): Unit = {
//    log.info("Printer Post Stop")
//    super.postStop()
//  }
//}
//
//class PrinterSupervisor extends Actor with ActorLogging {
//  override def supervisorStrategy = OneForOneStrategy() {
//    case ex: Exception =>
//      log.info("Restart")
//      Restart
//  }
//  val printer = context.actorOf(Props(new Printer))
//  override def receive: Receive = {
//    case msg =>
////      context.actorOf(Props(new Printer)) ! msg
//      printer forward  msg
//  }
//}