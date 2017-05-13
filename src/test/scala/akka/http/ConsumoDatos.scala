package akka.http

import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.spingo.op_rabbit.{Message, RabbitControl, RabbitMarshaller, RecoveryStrategy}
import org.scalatest.FunSuite
import spray.json.{DefaultJsonProtocol, PrettyPrinter, _}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

class ConsumoDatos extends FunSuite {

  ignore("Ejemplo practico") {
    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))


    val url = Uri("http://localhost:9000/")
    val sourceStreams = Http().singleRequest(HttpRequest(uri = url)).map { response =>
      response.entity.dataBytes.map { chunk =>
        Source.tick(1 seconds, 1 seconds, chunk.utf8String)
      }
    }

    sourceStreams.foreach(source => source.runForeach(sourceTicks => sourceTicks.runForeach {
      datos =>
        rabbitControl ! Message.topic(datos, "Datos")
    }))


  }

  ignore("Enviando datos Json rabbit ") {
    case class Msj(saludo: String)

    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val ItemFormat = jsonFormat1(Msj)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()


    implicit val msjMarshaller = new RabbitMarshaller[Msj] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Msj) =
        value.toString.getBytes(utf8)
    }


    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))


    val url = Uri("http://localhost:9000/")
    val sourceStreams = Http().singleRequest(HttpRequest(uri = url)).map { response =>
      response.entity.dataBytes.map { chunk =>
        Source.tick(1 seconds, 2 seconds, chunk.utf8String)
      }
    }

    val flowJson = Flow[String].map[Msj](mensajes => mensajes.parseJson.convertTo[Msj])

    sourceStreams.foreach(source => source.runForeach(sourceTicks => sourceTicks.via(flowJson).runForeach {
      datos =>

        rabbitControl ! Message.topic(datos, "Datos")
        println(datos)

    }))


  }

  ignore("Envio por consulta todos los datos Json completo ") {
    case class Total(registros: String, total: String)
    case class Reporte(fechaInicio: String, fechaFin: String, fechaReporte: String, ramo: String, moneda: String, tipoEntidad: String, totales: Total)


    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val totalFormat = jsonFormat2(Total)
      implicit val ReporteFormat = jsonFormat7(Reporte)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()


    implicit val totalMarshaller = new RabbitMarshaller[Total] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Total) =
        value.toString.getBytes(utf8)
    }
    implicit val reporteMarshaller = new RabbitMarshaller[Reporte] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Reporte) =
        value.toString.getBytes(utf8)
    }


    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))
    val fechaHoy = Calendar.getInstance().getTime

    val formatoFecha = new SimpleDateFormat("yyyy/MM/dd")

    val aleatorioRamoMoneda = List(("030", "COP"), ("040", "COP"), ("030", "USD"), ("040", "USD"))
    val aleatorioTipoEntidad = List("PAGO", "RESERVA", "REINTEGRO", "FACTURA")


    val fechaInicial = "2017/04/01"
    val fechaFinal = "2017/04/01"
    val fechaReporte = formatoFecha.format(fechaHoy)
    val ramo = "040"
    val moneda = "COP"
    val tipomsj = "PAGO"


    val url = Uri(s"http://localhost:9000/totales?fi=$fechaInicial&ff=$fechaFinal&ramo=$ramo&moneda=$moneda&tipomsj=$tipomsj")
    val sourceStreams = Http().singleRequest(HttpRequest(uri = url)).map { response =>
      response.entity.dataBytes.map { chunk =>
        Source.tick(1 seconds, 10 seconds, chunk.utf8String)
      }
    }

    val flowJson = Flow[String].map[Total](mensajes => mensajes.parseJson.convertTo[Total])

    sourceStreams.foreach(source => source.runForeach(sourceTicks => sourceTicks.via(flowJson).runForeach {
      datos =>

        aleatorioTipoEntidad.foreach {
          entidad =>
            aleatorioRamoMoneda.foreach {
              tipo =>
                val (ram, mon) = tipo
                rabbitControl ! Message.topic(Reporte(fechaInicial, fechaFinal, fechaReporte, ram, mon, entidad, datos), "Datos")

            }

        }
    }))

    println("Enter para cerrar")
    StdIn.readLine()
    sourceStreams
      .onComplete(_ => actorSystem.terminate())


  }

  ignore("Enviando datos del reporte Json infinito ticks") {
    case class Total(registros: String, total: String)
    case class Reporte(fechaInicio: String, fechaFin: String, fechaReporte: String, ramo: String, moneda: String, tipoEntidad: String, totales: Total)


    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val totalFormat = jsonFormat2(Total)
      implicit val ReporteFormat = jsonFormat7(Reporte)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()


    implicit val totalMarshaller = new RabbitMarshaller[Total] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Total) =
        value.toString.getBytes(utf8)
    }
    implicit val reporteMarshaller = new RabbitMarshaller[Reporte] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Reporte) =
        value.toString.getBytes(utf8)
    }


    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))
    val fechaHoy = Calendar.getInstance().getTime

    val formatoFecha = new SimpleDateFormat("yyyy/MM/dd")

    val aleatorioRamoMoneda = List(("030", "COP"), ("040", "COP"), ("030", "USD"), ("040", "USD"))
    val aleatorioTipoEntidad = List("PAGO", "RESERVA", "REINTEGRO", "FACTURA")


    val fechaInicial = "2017/04/01"
    val fechaFinal = "2017/04/01"
    val fechaReporte = formatoFecha.format(fechaHoy)

    aleatorioTipoEntidad.foreach {
      entidad =>

        aleatorioRamoMoneda.foreach {
          tipo =>
            val (ram, mon) = tipo

            val url = Uri(s"http://localhost:9000/totales?fi=$fechaInicial&ff=$fechaFinal&ramo=$ram&moneda=$mon&tipomsj=$entidad")
            val sourceStreams: Future[Source[Source[String, Cancellable], Any]] = Http().singleRequest(HttpRequest(uri = url)).map { response =>
              response.entity.dataBytes.map { chunk =>
                Source.tick(1 seconds, 10 seconds, chunk.utf8String)
              }
            }

            val flowJson = Flow[String].map[Total](mensajes => mensajes.parseJson.convertTo[Total])

            sourceStreams.foreach(source => source.runForeach(sourceTicks => sourceTicks.via(flowJson).runForeach {
              datos =>

                rabbitControl ! Message.topic(Reporte(fechaInicial, fechaFinal, fechaReporte, ram, mon, entidad, datos), "Datos")
            }))
        }
    }
    println("Enter para cerrar")
    StdIn.readLine()
    actorSystem.terminate

  }

  ignore("Enviando datos del reporte Json") {
    case class Total(registros: String, total: String)
    case class Reporte(fechaInicio: String, fechaFin: String, fechaReporte: String, ramo: String, moneda: String, tipoEntidad: String, totales: Total)


    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    object JsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val totalFormat = jsonFormat2(Total)
      implicit val ReporteFormat = jsonFormat7(Reporte)
    }

    import JsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()


    implicit val totalMarshaller = new RabbitMarshaller[Total] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Total) =
        value.toString.getBytes(utf8)
    }
    implicit val reporteMarshaller = new RabbitMarshaller[Reporte] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Reporte) =
        value.toString.getBytes(utf8)
    }


    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))


    val aleatorioRamoMoneda = List(("030", "COP"), ("040", "COP"), ("030", "USD"), ("040", "USD"))
    val aleatorioTipoEntidad = List("PAGO", "RESERVA", "REINTEGRO", "FACTURA")


    val fechaInicial = "2017/04/01"
    val fechaFinal = "2017/04/01"

    val ticks = Source.tick(1 seconds, 10 seconds, "Hola")


    ticks.runForeach {
      _ =>

        val fechaHoy = Calendar.getInstance().getTime
        val formatoFecha = new SimpleDateFormat("yyyy/MM/dd-mm-ss")
        val fechaReporte = formatoFecha.format(fechaHoy)

        aleatorioTipoEntidad.foreach {
          entidad =>

            aleatorioRamoMoneda.foreach {
              tipo =>
                val (ram, mon) = tipo

                val url = Uri(s"http://localhost:9000/totales?fi=$fechaInicial&ff=$fechaFinal&ramo=$ram&moneda=$mon&tipomsj=$entidad")

                val sourceStreams = Http().singleRequest(HttpRequest(uri = url)).map(_.entity.dataBytes)

                val flowString = Flow[ByteString].map[String](_.utf8String)

                val flowJson = Flow[String].map[Total](_.parseJson.convertTo[Total])

                val flowMessage = Flow[Total].map(m => Message.topic(Reporte(fechaInicial, fechaFinal, fechaReporte, ram, mon, entidad, m), "Datos"))

                val rabbitSink: Sink[Any, NotUsed] = Sink.actorRef(rabbitControl, "rabbitControl")

                val futureGraph = sourceStreams.flatMap {
                  s =>
                    Future {
                      s
                        .via(flowString)
                        .via(flowJson)
                        .via(flowMessage)
                        .to(rabbitSink)
                    }
                }

                futureGraph.flatMap { g =>
                  Future {
                    g.run()
                  }
                }

            }

        }
    }
  }

  test("Enviando datos del reporte Json a rabbit completo ") {
    case class Total(registros: String, total: String)
    case class Reporte(fechaInicio: String, fechaFin: String, fechaReporte: String, ramo: String, moneda: String, tipoEntidad: String, totales: Total)

    implicit val actorSystem = ActorSystem("Datos")
    implicit val materializer = ActorMaterializer()
    implicit val recoveryStrategy = RecoveryStrategy.none
    implicit val executionContext = actorSystem.dispatcher

    object JsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val totalFormat = jsonFormat2(Total)
      implicit val ReporteFormat = jsonFormat7(Reporte)
    }
    import JsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    implicit val totalMarshaller = new RabbitMarshaller[Total] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Total) =
        value.toString.getBytes(utf8)
    }
    implicit val reporteMarshaller = new RabbitMarshaller[Reporte] {
      val contentType = "aplication/json"
      protected val contentEncoding = Some("UTF-8")
      private val utf8 = Charset.forName("UTF-8")

      def marshall(value: Reporte) =
        value.toString.getBytes(utf8)
    }

    val rabbitControl = actorSystem.actorOf(Props(new RabbitControl))

    val monedas = "COP" :: "USD" :: Nil
    val ramos = "030" :: "040" :: Nil
    val entidades = "PAGO" :: "RESERVA" :: "REINTEGRO" :: "FACTURA" :: Nil
    val fechaInicial = "2017/04/01"
    val fechaFinal = "2017/04/01"

    val listMonedaRamoEntidad = for {
      m <- monedas
      r <- ramos
      e <- entidades
    } yield (m, r, e)

    val listaSource = Source(listMonedaRamoEntidad)

    val ticks = Source.tick(1 seconds, 2 seconds, "Nada")

    val sourceTicksList = ticks.flatMapConcat { _ =>
      listaSource
    }

    sourceTicksList.runForeach { tuplaMonedaRamoEntidad =>

      val (moneda, ramo, entidad) = tuplaMonedaRamoEntidad

      val fechaHoy = Calendar.getInstance().getTime
      val formatoFecha = new SimpleDateFormat("yyyy/MM/dd-mm-ss")
      val fechaReporte = formatoFecha.format(fechaHoy)

      val url = Uri(s"http://localhost:9000/totales?fi=$fechaInicial&ff=$fechaFinal&ramo=$ramo&moneda=$moneda&tipomsj=$entidad")

      val sourceStreams = Http().singleRequest(HttpRequest(uri = url)).map(_.entity.dataBytes)

      val flowString = Flow[ByteString].map[String](_.utf8String)

      val flowJson = Flow[String].map[Total](_.parseJson.convertTo[Total])

      val flowMessage = Flow[Total].map(m => Message.topic(Reporte(fechaInicial, fechaFinal, fechaReporte, ramo, moneda, entidad, m), "Datos"))

      val sinkRabbit: Sink[Any, NotUsed] = Sink.actorRef(rabbitControl, "rabbitControl")

      val futureGraph = sourceStreams.flatMap { s =>
        Future {
          s
            .via(flowString)
            .via(flowJson)
            .via(flowMessage)
            .to(sinkRabbit)
        }
      }

      futureGraph.flatMap { g =>
        Future {
          g.run()
        }
      }
    }
  }
}
