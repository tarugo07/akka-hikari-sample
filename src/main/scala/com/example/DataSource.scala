package com.example

import java.net.ConnectException
import java.sql.{ Connection, SQLException }
import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.{ Escalate, Restart }
import akka.actor._
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }

import scala.concurrent.duration._
import scala.language.postfixOps

object DataSourceProtocol {

  case object GetConnection

  case class ConnectionResult(conn: Connection)

}

object DataSource {

  val name = "data-source"

  def props(hikariConfig: HikariConfig): Props = Props(new DataSource(hikariConfig))

}

class DataSource(hikariConfig: HikariConfig) extends Actor with ActorLogging {

  import com.example.DataSourceProtocol._

  private val dataSource = new HikariDataSource(hikariConfig)

  @scala.throws[Exception](classOf[Exception])
  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.warning("Restarted DataSource: {}", reason.getMessage)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    if (!dataSource.isClosed) dataSource.close()
    super.postStop()
  }

  override def receive: Receive = {
    case GetConnection =>
      val conn = dataSource.getConnection
      sender ! ConnectionResult(conn)
    case _ =>
      log.warning("Unsupported Protocol")
  }

}

class DataSourceSupervisor extends Actor with ActorLogging {

  val sysConfig = context.system.settings.config

  val hikariConfig: HikariConfig = {
    // TODO: HikariCPの設定を見直し
    val config = new HikariConfig()
    config.setDriverClassName(sysConfig.getString("db.driver"))
    config.setJdbcUrl(sysConfig.getString("db.url"))
    config.setUsername(sysConfig.getString("db.username"))
    config.setPassword(sysConfig.getString("db.password"))
    config.setMinimumIdle(sysConfig.getInt("db.hikaricp.minimumIdle"))
    config.setMaximumPoolSize(sysConfig.getInt("db.hikaricp.maximumPoolSize"))
    config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(sysConfig.getInt("db.hikaricp.connectionTimeout")))
    config.setIdleTimeout(TimeUnit.SECONDS.toMillis(sysConfig.getInt("db.hikaricp.idleTimeout")))
    config.setAutoCommit(true)
    config
  }

  val dataSource = context.actorOf(DataSource.props(hikariConfig), DataSource.name)

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: SQLException => Restart
      case _: ConnectException => Restart
      case _: Exception => Escalate
    }

  override def receive: Receive = {
    case msg: Any => dataSource forward msg
  }

}

object DataSourceSupervisor {

  val name = "data-source-supervisor"

  def props: Props = Props(new DataSourceSupervisor)

}

class IdGenerator(dataSourceSupervisor: ActorRef) extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  var connection: Option[Connection] = None

  override def receive: Receive = connectionClosed

  private def connectionClosed: Receive = {
    case IdWorkerClientProtocol.GenerateId(from: ActorRef) =>
      context.become(waitingForConnection(from))
      dataSourceSupervisor ! DataSourceProtocol.GetConnection
    case obj: Any => log.warning("Unsupported Protocol")
  }

  private def waitingForConnection(replyTo: ActorRef): Receive = {
    case DataSourceProtocol.ConnectionResult(conn) =>
      log.debug("Got Connection. {}", conn)
      connection = Some(conn)
      generateId(replyTo)
      context.stop(self)
    case _: Any => log.warning("Unsupported Protocol")
  }

  private def generateId(replyTo: ActorRef): Unit = {
    connection.foreach { conn =>
      val stmt1 = conn.prepareStatement("UPDATE message_sequence_number SET id = LAST_INSERT_ID(id+1)")
      val stmt2 = conn.prepareStatement("SELECT LAST_INSERT_ID() AS id")
      try {
        stmt1.executeUpdate()
        val rs = stmt2.executeQuery()
        val id = if (rs.next()) rs.getLong("id") else 0L
        replyTo ! IdWorkerClientProtocol.IdGenerated(id)
      } finally {
        stmt1.close()
        stmt2.close()
        if (!conn.isClosed) {
          conn.close()
        }
        connection = None
      }
    }
  }

}

object IdGenerator {

  def props(dataSourceSupervisor: ActorRef): Props = Props(new IdGenerator(dataSourceSupervisor))

}

object IdWorkerClientProtocol {

  case class GenerateId(from: ActorRef)

  case class IdGenerated(value: Long)

}

class IdWorkerClientForRDB extends Actor with ActorLogging {

  val dataSourceSupervisor = context.actorOf(DataSourceSupervisor.props, DataSourceSupervisor.name)

  override def receive: Receive = {
    case msg: IdWorkerClientProtocol.GenerateId =>
      context.actorOf(IdGenerator.props(dataSourceSupervisor)) forward msg
    case _: Any =>
      log.warning("Unsupported Protocol")
  }

}

object IdWorkerClientForRDB {

  val name = "id-worker-client-for-rdb"

  def props: Props = Props(new IdWorkerClientForRDB)

}
