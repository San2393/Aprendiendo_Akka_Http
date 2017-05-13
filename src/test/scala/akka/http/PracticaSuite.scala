package akka.http

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{complete, path, pathSingleSlash, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.util.ByteString
import org.scalatest.FunSuite
import spray.json.{DefaultJsonProtocol, PrettyPrinter}

import scala.concurrent.duration._
import scala.io.StdIn


class PracticaSuite extends FunSuite {

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  ignore("Server") {

    val requestHandler: HttpRequest => HttpResponse = {

      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(status = StatusCodes.OK, entity = HttpEntity(
          ContentTypes.`text/plain(UTF-8)`,
          "Hola"))
    }

    val bindingFuture = Http().bindAndHandleSync(requestHandler, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("server 2") {
    val serverSource = Http().bind(interface = "localhost", port = 8080)

    val requestHandler: HttpRequest => HttpResponse = {
      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("Hola")))
    }

    val bindingFuture = serverSource.to(Sink.foreach { connection =>
      connection handleWithSyncHandler requestHandler
    }).run()

    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Server 3") {
    val route =
      pathSingleSlash {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Hola"))
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Ejemplo combinando Rejection y Json") {
    import akka.http.scaladsl.model._
    import akka.http.scaladsl.server.RejectionHandler

    implicit def myRejectionHandler =
      RejectionHandler.default
        .mapRejectionResponse {
          case res@HttpResponse(_, _, ent: HttpEntity.Strict, _) =>

            val message = ent.data.utf8String

            res.copy(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Error: $message"))

          case x => x // pass through all other types of responses
        }


    case class Suma(a: Int, b: Int)

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val SumaFormat = jsonFormat2(Suma)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val route =
      Route.seal(
        path("suma") {
          post {
            entity(asSourceOf[Suma]) { numeros =>
              val numerosSubmitted = numeros.runWith(Sink.fold("") { (acum, item) =>
                (item.a + item.b) + "\n" + acum

              })
              complete(numerosSubmitted.map(x => s"Resultado:\n$x"))
            }
          }
        }
      )

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  test("Usando WebSocket con route y Graph") {

    val flowTest: Flow[Message, Message, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        var cont = 0

        val bcast = b.add(Broadcast[Message](1))
        val merge = b.add(Merge[Message](2))

        val source = Source.tick(1 second, 3 second, "Hola ")

        source.runFold(0) { (con,_) =>
          cont = con
          con + 1
        }


        val num = Flow[String]
          .map[Message](x => TextMessage(x + cont))

        val msj = b.add(Flow[Message].map[Message] { x => x })

        bcast ~> merge
        source ~> num ~> merge ~> msj
        FlowShape(bcast.in, msj.out)
      })

    val route =
      path("msj") {
        get {
          handleWebSocketMessages(flowTest)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

}
