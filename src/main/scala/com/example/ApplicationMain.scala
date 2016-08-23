package com.example

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}

object ApplicationMain extends App {
  val system = ActorSystem("MyActorSystem")
  val printer = system.actorOf(Props(new PrinterSupervisor), "printer-supervisor")
//  val pingActor = system.actorOf(PingActor.props, "pingActor")
//  pingActor ! PingActor.Initialize
  // This example app will ping pong 3 times and thereafter terminate the ActorSystem - 
  // see counter logic in PingActor
//  system.awaitTermination()
  printer ! "Hello World !"
  printer ! "Hello World 2 !"
  Thread.sleep(1000)
  printer ! 1
  Thread.sleep(1000)
  printer ! "Hi !"
//  system.awaitTermination()
  system.terminate()
}

class Printer extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg: String =>
      log.info(s"receive message: $msg")
//      context.stop(self)
    case _ =>
      throw new Exception("unknown message")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.info(s"Printer Pre Restart: ${reason.getMessage}")
  }
  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.info(s"Printer Post Restart: ${reason.getMessage}")
  }

  override def postStop(): Unit = {
    log.info("Printer Post Stop")
    super.postStop()
  }
}

class PrinterSupervisor extends Actor with ActorLogging {
  override def supervisorStrategy = OneForOneStrategy() {
    case ex: Exception =>
      log.info("Restart")
      Restart
  }
  val printer = context.actorOf(Props(new Printer))
  override def receive: Receive = {
    case msg =>
//      context.actorOf(Props(new Printer)) ! msg
      printer forward  msg
  }
}