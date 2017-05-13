package akka.http

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, _}
import akka.http.scaladsl.server.Directives.{complete, extractMethod, _}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import org.scalatest.FunSuite
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

class BasicSuite extends FunSuite {

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  ignore("Conexión") {
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
      Http().bind(interface = "localhost", port = 8080)
    val bindingFuture: Future[Http.ServerBinding] =
      serverSource.to(Sink.foreach { connection =>
        println(s"Conexion: ${connection.localAddress}")
      }).run()

  }

  ignore("Definicion como inciar un server") {

    object Server extends HttpApp {
      def route: Route =
        path("hola") {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hola akka-http</h1>"))
          }
        }
    }

    Server.startServer("localhost", 8080)

  }

  ignore("Usando header") {


    val route = respondWithHeader(RawHeader("header", "Hola soy  un header")) {
      get {
        pathSingleSlash {
          complete {
            "Hola Akka!"
          }
        }
      }
    }


    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())


  }

  ignore("Usando pathPrefix") {

    val route: Route =
      get {
        pathPrefix("numero" / LongNumber) { id =>
          complete(s"Numero: $id")
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    //pone en pantalla el numero que ponga en la ruta http://localhost:8080/numero/#
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Usando request-responses") {

    val requestHandler: HttpRequest => HttpResponse = {

      case HttpRequest(POST, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/plain(UTF-8)`,
          "Akka POST"))

      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/plain(UTF-8)`,
          "Akka GET"))
    }

    val bindingFuture = Http().bindAndHandleSync(requestHandler, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Usando Route con GET y mostrando el Json") {
    case class MostrarItem(name: String, id: Long)

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val MostrarItemFormat = jsonFormat2(MostrarItem)
    }


    import PrettyJsonFormatSupport._

    val route =
      get {
        pathSingleSlash {
          complete {
            MostrarItem("akka", 77)
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando Route con get, post, Json") {

    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
    import io.circe.generic.auto._

    case class Item(id: Int)
    val route: Route =
      path("numero" / LongNumber) { id =>
        get {
          complete(s"Numero: $id")
        }
      } ~
        pathSingleSlash {
          get {
            parameter("numero".as[Int]) { id =>
              complete(s"Numero: $id")
            }
          }
        } ~
        path("jsonitem") {
          post {
            //JSON to Item
            entity(as[Item]) { item =>
              println(s"Item : $item")
              complete(item)
            }
          }
        }


    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Usando put") {

    val route =
      (put & path("datos")) {
        withoutSizeLimit {
          extractDataBytes { bytes =>
            val finalEscrito: Future[ByteString] = bytes.runWith(Sink.head)

            onComplete(finalEscrito) { result =>
              complete("Se ingreso: " + result.get.utf8String)
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  ignore("Usando Directive ") {
    val numeroGetOPostOPutConMetodo = path("numero" / IntNumber) & (get | post | put) & extractMethod
    val route =
      numeroGetOPostOPutConMetodo { (id, m) =>
        complete(s"recibe ${m.name} request con numero $id")
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando Directive con parameters") {
    val order = path("valor" / DoubleNumber) & parameters('opt ?, 'cantidad)
    val route =
      order { (valor, opt, cantidad) =>
        complete(s"El valor es $valor, option: $opt y la cantidad: $cantidad ")
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando map y tmap") {

    //map
    val parametroString: Directive1[String] = parameter("text".as[String])

    val tamañoDirective: Directive1[Int] = parametroString.map(text => text.length)

    //tmap
    val parametrosInt: Directive[(Int, Int)] = parameters(("a".as[Int], "b".as[Int]))

    val directive: Directive1[String] = parametrosInt.tmap {
      case (a, b) => (a + b).toString
    }

    val route = path("suma") {
      get {
        directive {
          s => complete(s"La suma es: $s")
        }
      }
    } ~
      path("palabra") {
        get {
          tamañoDirective {
            t => complete(s"El tamaño es: $t")
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando extract") {

    val uriLength: Directive1[Int] = extract(_.request.uri.toString.length)
    val route =
      uriLength { len =>
        complete(s"Tamaño del request URI $len")
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando case class") {
    case class Tweet(uid: Int, txt: String)

    object MyJsonProtocol
      extends akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
        with spray.json.DefaultJsonProtocol {

      implicit val tweetFormat = jsonFormat2(Tweet.apply)
    }

    val tweets = parameters("uid".as[Int], "txt".as[String]).as(Tweet)

    val route =
      path("tw") {
        tweets {
          x => complete(s"El $x")
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())


  }

  ignore("Usando Rejection default") {

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

    val route =
      Route.seal(
        pathSingleSlash {
          get {
            complete("Solo existe esta route")
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

  ignore("Usando Rejection") {
    case class MsjError(codigo: Int, mensaje: String)

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val MsjFormat = jsonFormat2(MsjError)
    }

    import PrettyJsonFormatSupport._


    implicit def rejectionHandler =
      RejectionHandler.newBuilder()
        .handle { case MissingQueryParamRejection(param) =>
          val msj = MsjError(BadRequest.intValue, s"Falta parametro $param").toJson.toString()
          complete(HttpResponse(BadRequest, entity = HttpEntity(ContentTypes.`application/json`, msj)))
        }.handle { case MethodRejection(m) =>
        val msj = MsjError(BadRequest.intValue, s"Es con el metodo ${m.value}").toJson.toString()
        complete(HttpResponse(BadRequest, entity = HttpEntity(ContentTypes.`application/json`, msj)))
      }.handleNotFound {
        val msj = MsjError(BadRequest.intValue, "No encontrado").toJson.toString()
        complete(HttpResponse(BadRequest, entity = HttpEntity(ContentTypes.`application/json`, msj)))
      }.result()


    val route: Route =
      path("saludo") {
        get {
          parameter("name".as[String]) { name =>
            complete(s"Hola $name, Como estas?")
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)


    StdIn.readLine
    bindingFuture.flatMap(_.unbind()).onComplete(_ => actorSystem.terminate())


  }

  ignore("Usando ExceptionHandler") {

    import Directives._
    import StatusCodes._

    val myExceptionHandler = ExceptionHandler {
      case _: ArithmeticException => complete(HttpResponse(InternalServerError, entity = "división por cero!!"))
    }

    val route = path("div") {
      handleExceptions(myExceptionHandler) {
        parameters("a".as[Int], "b".as[Int]) { (a, b) =>
          complete(s"Resultado: ${a / b}")
        }
      }
    }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())


  }

  ignore("Consumiendo Json en Source") {

    case class Item(id: Int)

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val ItemFormat = jsonFormat1(Item)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val route =
      path("item") {
        entity(asSourceOf[Item]) { numeros =>
          val numerosSubmitted: Future[String] = numeros.runWith(Sink.fold("")(_ + "\n" + _))
          complete(numerosSubmitted.map(x => s"Items $x"))
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Source en Json") {

    case class Item(id: Int)

    object PrettyJsonFormatSupport {

      import DefaultJsonProtocol._

      implicit val Mostrar = PrettyPrinter
      implicit val ItemFormat = jsonFormat1(Item)
    }

    import PrettyJsonFormatSupport._
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val route =
      path("item") {
        val item = Source.single(Item(2))
        complete(item)
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())

  }

  ignore("Usando WebSocket con route") {

    val flow = Flow[String].map(x => s"Recibiendo: $x Tamaño: ${x.length}")

    val webSocket: Flow[Message, TextMessage, NotUsed] =
      Flow[Message].collect {
        case tm: TextMessage => TextMessage(tm.textStream.via(flow))
      }

    val route =
      path("msj") {
        get {
          handleWebSocketMessages(webSocket)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println("Enter para cerrar")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  test("") {

  }


}
