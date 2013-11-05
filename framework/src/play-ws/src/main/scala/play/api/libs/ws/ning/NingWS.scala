/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play.api.libs.ws.ning

import com.ning.http.client.{ Response => AHCResponse, Cookie => AHCCookie, ProxyServer => AHCProxyServer, _ }
import com.ning.http.client.Realm.{ RealmBuilder, AuthScheme }
import com.ning.http.util.AsyncHttpProviderUtils

import collection.immutable.TreeMap

import scala.concurrent.{ Future, Promise, ExecutionContext }

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import play.api.libs.ws._
import play.api.http.{ Writeable, ContentTypeOf }
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input.El
import play.api.{ Application, Play }

import play.core.utils.CaseInsensitiveOrdered

class NingWSClient(config: AsyncHttpClientConfig) extends WSClient {
  private val asyncHttpClient = new AsyncHttpClient(config)

  def underlying[T] = asyncHttpClient.asInstanceOf[T]

  def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = asyncHttpClient.executeRequest(request, handler)

  def close() = asyncHttpClient.close()
}

/**
 * A WS Request.
 */
class NingWSRequest(client: NingWSClient, _method: String, _auth: Option[(String, String, WSAuthScheme)], _calc: Option[WSSignatureCalculator])
    extends RequestBuilderBase[NingWSRequest](classOf[NingWSRequest], _method, false)
    with WSRequest {

  import scala.collection.JavaConverters._

  def getStringData: String = body.getOrElse("")

  protected var body: Option[String] = None

  override def setBody(s: String) = {
    this.body = Some(s)
    super.setBody(s)
  }

  protected var calculator: Option[WSSignatureCalculator] = _calc

  protected var headers: Map[String, Seq[String]] = Map()

  protected var _url: String = null

  //this will do a java mutable set hence the {} response
  _auth.map(data => auth(data._1, data._2, authScheme(data._3))).getOrElse({})

  private def authScheme(scheme: WSAuthScheme): AuthScheme = scheme match {
    case WSAuthScheme.DIGEST => AuthScheme.DIGEST
    case WSAuthScheme.BASIC => AuthScheme.BASIC
    case WSAuthScheme.NTLM => AuthScheme.NTLM
    case WSAuthScheme.SPNEGO => AuthScheme.SPNEGO
    case WSAuthScheme.KERBEROS => AuthScheme.KERBEROS
    case WSAuthScheme.NONE => AuthScheme.NONE
    case _ => throw new RuntimeException("Unknown scheme " + scheme)
  }

  /**
   * Add http auth headers. Defaults to HTTP Basic.
   */
  private def auth(username: String, password: String, scheme: AuthScheme = AuthScheme.BASIC): WSRequest = {
    this.setRealm((new RealmBuilder)
      .setScheme(scheme)
      .setPrincipal(username)
      .setPassword(password)
      .setUsePreemptiveAuth(true)
      .build())
  }

  /**
   * Return the current headers of the request being constructed
   */
  def allHeaders: Map[String, Seq[String]] = {
    mapAsScalaMapConverter(request.asInstanceOf[com.ning.http.client.Request].getHeaders()).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
  }

  /**
   * Return the current query string parameters
   */
  def queryString: Map[String, Seq[String]] = {
    mapAsScalaMapConverter(request.asInstanceOf[com.ning.http.client.Request].getParams()).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
  }

  /**
   * Retrieve an HTTP header.
   */
  def header(name: String): Option[String] = headers.get(name).flatMap(_.headOption)

  /**
   * The HTTP method.
   */
  def method: String = _method

  /**
   * The URL
   */
  def url: String = _url

  /**
   * Set an HTTP header.
   */
  override def setHeader(name: String, value: String): NingWSRequest = {
    headers = headers + (name -> List(value))
    super.setHeader(name, value)
  }

  /**
   * Add an HTTP header (used for headers with multiple values).
   */
  override def addHeader(name: String, value: String): NingWSRequest = {
    headers = headers + (name -> (headers.get(name).getOrElse(List()) :+ value))
    super.addHeader(name, value)
  }

  /**
   * Defines the request headers.
   */
  override def setHeaders(hdrs: FluentCaseInsensitiveStringsMap): NingWSRequest = {
    headers = ningHeadersToMap(hdrs)
    super.setHeaders(hdrs)
  }

  /**
   * Defines the request headers.
   */
  override def setHeaders(hdrs: java.util.Map[String, java.util.Collection[String]]): NingWSRequest = {
    headers = ningHeadersToMap(hdrs)
    super.setHeaders(hdrs)
  }

  /**
   * Defines the request headers.
   */
  def setHeaders(hdrs: Map[String, Seq[String]]): NingWSRequest = {
    headers = hdrs
    hdrs.foreach(header => header._2.foreach(value =>
      super.addHeader(header._1, value)
    ))
    this
  }

  /**
   * Defines the query string.
   */
  def setQueryString(queryString: Map[String, Seq[String]]): NingWSRequest = {
    for ((key, values) <- queryString; value <- values) {
      this.addQueryParameter(key, value)
    }
    this
  }

  private def ningHeadersToMap(headers: java.util.Map[String, java.util.Collection[String]]) =
    mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap

  private def ningHeadersToMap(headers: FluentCaseInsensitiveStringsMap) = {
    val res = mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
    //todo: wrap the case insensitive ning map instead of creating a new one (unless perhaps immutabilty is important)
    TreeMap(res.toSeq: _*)(CaseInsensitiveOrdered)
  }

  private[libs] def execute: Future[NingWSResponse] = {
    import com.ning.http.client.AsyncCompletionHandler
    var result = Promise[NingWSResponse]()
    calculator.map(_.sign(this))
    client.executeRequest(this.build(), new AsyncCompletionHandler[AHCResponse]() {
      override def onCompleted(response: AHCResponse) = {
        result.success(NingWSResponse(response))
        response
      }

      override def onThrowable(t: Throwable) = {
        result.failure(t)
      }
    })
    result.future
  }

  /**
   * Defines the URL.
   */
  override def setUrl(url: String): NingWSRequest = {
    _url = url
    super.setUrl(url)
  }

  private[libs] def executeStream[A](consumer: WSResponseHeaders => Iteratee[Array[Byte], A])(implicit ec: ExecutionContext): Future[Iteratee[Array[Byte], A]] = {
    import com.ning.http.client.AsyncHandler
    var doneOrError = false
    calculator.map(_.sign(this))

    var statusCode = 0
    val iterateeP = Promise[Iteratee[Array[Byte], A]]()
    var iteratee: Iteratee[Array[Byte], A] = null

    client.executeRequest(this.build(), new AsyncHandler[Unit]() {

      import com.ning.http.client.AsyncHandler.STATE

      override def onStatusReceived(status: HttpResponseStatus) = {
        statusCode = status.getStatusCode()
        STATE.CONTINUE
      }

      override def onHeadersReceived(h: HttpResponseHeaders) = {
        val headers = h.getHeaders()
        iteratee = consumer(DefaultWSResponseHeaders(statusCode, ningHeadersToMap(headers)))
        STATE.CONTINUE
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
        if (!doneOrError) {
          iteratee = iteratee.pureFlatFold {
            case Step.Done(a, e) => {
              doneOrError = true
              val it = Done(a, e)
              iterateeP.success(it)
              it
            }

            case Step.Cont(k) => {
              k(El(bodyPart.getBodyPartBytes()))
            }

            case Step.Error(e, input) => {
              doneOrError = true
              val it = Error(e, input)
              iterateeP.success(it)
              it
            }
          }
          STATE.CONTINUE
        } else {
          iteratee = null
          // Must close underlying connection, otherwise async http client will drain the stream
          bodyPart.markUnderlyingConnectionAsClosed()
          STATE.ABORT
        }
      }

      override def onCompleted() = {
        Option(iteratee).map(iterateeP.success)
      }

      override def onThrowable(t: Throwable) = {
        iterateeP.failure(t)
      }
    })
    iterateeP.future
  }

}

/**
 * A WS Request builder.
 */
case class NingWSRequestHolder(client: NingWSClient,
    url: String,
    headers: Map[String, Seq[String]],
    queryString: Map[String, Seq[String]],
    calc: Option[WSSignatureCalculator],
    auth: Option[(String, String, WSAuthScheme)],
    followRedirects: Option[Boolean],
    requestTimeout: Option[Int],
    virtualHost: Option[String],
    proxyServer: Option[WSProxyServer],
    body: Option[WSRequestBody]) extends WSRequestHolder {

  /**
   * sets the signature calculator for the request
   * @param calc
   */
  def sign(calc: WSSignatureCalculator): WSRequestHolder = this.copy(calc = Some(calc))

  /**
   * sets the authentication realm
   */
  def withAuth(username: String, password: String, scheme: WSAuthScheme): WSRequestHolder =
    this.copy(auth = Some((username, password, scheme)))

  /**
   * adds any number of HTTP headers
   * @param hdrs
   */
  def withHeaders(hdrs: (String, String)*): WSRequestHolder = {
    val headers = hdrs.foldLeft(this.headers)((m, hdr) =>
      if (m.contains(hdr._1)) m.updated(hdr._1, m(hdr._1) :+ hdr._2)
      else m + (hdr._1 -> Seq(hdr._2))
    )
    this.copy(headers = headers)
  }

  /**
   * adds any number of query string parameters to the
   */
  def withQueryString(parameters: (String, String)*): WSRequestHolder =
    this.copy(queryString = parameters.foldLeft(queryString) {
      case (m, (k, v)) => m + (k -> (v +: m.get(k).getOrElse(Nil)))
    })

  /**
   * Sets whether redirects (301, 302) should be followed automatically
   */
  def withFollowRedirects(follow: Boolean): WSRequestHolder =
    this.copy(followRedirects = Some(follow))

  @scala.deprecated("use withRequestTimeout instead", "2.1.0")
  def withTimeout(timeout: Int): WSRequestHolder =
    this.withRequestTimeout(timeout)

  /**
   * Sets the maximum time in millisecond you accept the request to take.
   * Warning: a stream consumption will be interrupted when this time is reached.
   */
  def withRequestTimeout(timeout: Int): WSRequestHolder =
    this.copy(requestTimeout = Some(timeout))

  def withVirtualHost(vh: String): WSRequestHolder = {
    this.copy(virtualHost = Some(vh))
  }

  def withProxyServer(proxyServer: WSProxyServer): WSRequestHolder = {
    this.copy(proxyServer = Some(proxyServer))
  }

  def withBody(file: File) =
    this.copy(body = Some(new WSRequestBodyFile(file)))

  def withBody[T](content: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]) =
    this.copy(body = Some(new WSRequestBodyWritable(content, wrt, ct)))

  /**
   * Execute an arbitrary method on the request asynchronously.
   *
   * @param method The method to execute
   */
  //TODO use the correct prepare method
  def execute(method: String) = prepare(method).execute

  def stream[A](method: String, consumer: WSResponseHeaders => Iteratee[Array[Byte], A])(implicit ec: ExecutionContext) =
    prepare(method).executeStream(consumer)

  private[play] def prepare(method: String): NingWSRequest = {
    val request: NingWSRequest = new NingWSRequest(client, method, auth, calc).setUrl(url)
      .setHeaders(headers)
      .setQueryString(queryString)

    prepareBody(request)

    followRedirects.map(request.setFollowRedirects)
    requestTimeout.map {
      t: Int =>
        val config = new PerRequestConfig()
        config.setRequestTimeoutInMs(t)
        request.setPerRequestConfig(config)
    }

    virtualHost.map {
      v =>
        request.setVirtualHost(v)
    }

    prepareProxy(request)

    request
  }

  private def prepareBody(request: NingWSRequest) {
    body match {
      case Some(f: WSRequestBodyFile) =>
        import com.ning.http.client.generators.FileBodyGenerator

        val bodyGenerator = new FileBodyGenerator(f.getFile)
        request.setBody(bodyGenerator)
      case Some(b: WSRequestBodyWritable[AnyRef]) =>
        request.addHeader("Content-Type", b.getContentType.mimeType.getOrElse("text/plain"))
        request.setBody(b.transform)
      case None => ()
    }
  }

  private[play] def prepareProxy(request: NingWSRequest) {
    proxyServer.map {
      p =>
        import com.ning.http.client.ProxyServer.Protocol
        val protocol: Protocol = p.protocol.getOrElse("http").toLowerCase match {
          case "http" => Protocol.HTTP
          case "https" => Protocol.HTTPS
          case "kerberos" => Protocol.KERBEROS
          case "ntlm" => Protocol.NTLM
          case "spnego" => Protocol.SPNEGO
          case _ => scala.sys.error("Unrecognized protocol!")
        }

        val proxyServer = new AHCProxyServer(
          protocol,
          p.host,
          p.port,
          p.principal.getOrElse(null),
          p.password.getOrElse(null))

        p.encoding.map {
          e =>
            proxyServer.setEncoding(e)
        }

        p.nonProxyHosts.map {
          nonProxyHosts =>
            nonProxyHosts.foreach {
              nonProxyHost =>
                proxyServer.addNonProxyHost(nonProxyHost)
            }
        }

        p.ntlmDomain.map {
          ntlm =>
            proxyServer.setNtlmDomain(ntlm)
        }

        request.setProxyServer(proxyServer)
    }
  }
}

/**
 * WSPlugin implementation hook.
 */
class NingWSPlugin(app: Application) extends WSPlugin {

  @volatile var loaded = false

  override lazy val enabled = true

  override def onStart() {
    loaded = true
  }

  override def onStop() {
    if (loaded) {
      ningAPI.resetClient()
      loaded = false
    }
  }

  def api = ningAPI

  private lazy val ningAPI = new NingWSAPI(app)

}

class NingWSAPI(app: Application) extends WSAPI {

  import javax.net.ssl.SSLContext

  private val clientHolder: AtomicReference[Option[NingWSClient]] = new AtomicReference(None)

  private[play] def newClient(): NingWSClient = {
    val playConfig = app.configuration
    val asyncHttpConfig = new AsyncHttpClientConfig.Builder()
      .setConnectionTimeoutInMs(playConfig.getMilliseconds("ws.timeout.connection").getOrElse(120000L).toInt)
      .setIdleConnectionTimeoutInMs(playConfig.getMilliseconds("ws.timeout.idle").getOrElse(120000L).toInt)
      .setRequestTimeoutInMs(playConfig.getMilliseconds("ws.timeout.request").getOrElse(120000L).toInt)
      .setFollowRedirects(playConfig.getBoolean("ws.followRedirects").getOrElse(true))
      .setUseProxyProperties(playConfig.getBoolean("ws.useProxyProperties").getOrElse(true))

    playConfig.getString("ws.useragent").map {
      useragent =>
        asyncHttpConfig.setUserAgent(useragent)
    }
    if (!playConfig.getBoolean("ws.acceptAnyCertificate").getOrElse(false)) {
      asyncHttpConfig.setSSLContext(SSLContext.getDefault)
    }
    new NingWSClient(asyncHttpConfig.build())
  }

  def client: NingWSClient = {
    clientHolder.get.getOrElse({
      // A critical section of code. Only one caller has the opportuntity of creating a new client.
      synchronized {
        clientHolder.get match {
          case None => {
            val client = newClient()
            clientHolder.set(Some(client))
            client
          }
          case Some(client) => client
        }

      }
    })
  }

  def url(url: String) = NingWSRequestHolder(client, url, Map(), Map(), None, None, None, None, None, None, None)

  /**
   * resets the underlying AsyncHttpClient
   */
  private[play] def resetClient(): Unit = {
    clientHolder.getAndSet(None).map(oldClient => oldClient.close())
  }

}

/**
 * The Ning implementation of a WS cookie.
 */
private class NingWSCookie(ahcCookie: AHCCookie) extends WSCookie {

  private def noneIfEmpty(value: String): Option[String] = {
    if (value.isEmpty) None else Some(value)
  }

  /**
   * The underlying cookie object for the client.
   */
  def underlying[T] = ahcCookie.asInstanceOf[T]

  /**
   * The domain.
   */
  def domain: String = ahcCookie.getDomain

  /**
   * The cookie name.
   */
  def name: Option[String] = noneIfEmpty(ahcCookie.getName)

  /**
   * The cookie value.
   */
  def value: Option[String] = noneIfEmpty(ahcCookie.getValue)

  /**
   * The path.
   */
  def path: String = ahcCookie.getPath

  /**
   * The maximum age.
   */
  def maxAge: Int = ahcCookie.getMaxAge

  /**
   * If the cookie is secure.
   */
  def secure: Boolean = ahcCookie.isSecure

  /**
   * The cookie version.
   */
  def version: Int = ahcCookie.getVersion

  /*
   * Cookie ports should not be used; cookies for a given host are shared across
   * all the ports on that host.
   */

  override def toString: String = ahcCookie.toString
}

/**
 * A WS HTTP response.
 */
case class NingWSResponse(ahcResponse: AHCResponse) extends WSResponse {

  import scala.xml._
  import play.api.libs.json._

  /**
   * Get the underlying response object.
   */
  @deprecated("Use underlying", "2.3.0")
  def getAHCResponse = ahcResponse

  /**
   * @return The underlying response object.
   */
  def underlying[T] = ahcResponse.asInstanceOf[T]

  /**
   * The response status code.
   */
  def status: Int = ahcResponse.getStatusCode

  /**
   * The response status message.
   */
  def statusText: String = ahcResponse.getStatusText

  /**
   * Get a response header.
   */
  def header(key: String): Option[String] = Option(ahcResponse.getHeader(key))

  /**
   * Get all the cookies.
   */
  def cookies: Seq[WSCookie] = {
    import scala.collection.JavaConverters._
    ahcResponse.getCookies.asScala.map(new NingWSCookie(_))
  }

  /**
   * Get only one cookie, using the cookie name.
   */
  def cookie(name: String): Option[WSCookie] = cookies.find(_.name == Option(name))

  /**
   * The response body as String.
   */
  lazy val body: String = {
    // RFC-2616#3.7.1 states that any text/* mime type should default to ISO-8859-1 charset if not
    // explicitly set, while Plays default encoding is UTF-8.  So, use UTF-8 if charset is not explicitly
    // set and content type is not text/*, otherwise default to ISO-8859-1
    val contentType = Option(ahcResponse.getContentType).getOrElse("application/octet-stream")
    val charset = Option(AsyncHttpProviderUtils.parseCharset(contentType)).getOrElse {
      if (contentType.startsWith("text/"))
        AsyncHttpProviderUtils.DEFAULT_CHARSET
      else
        "utf-8"
    }
    ahcResponse.getResponseBody(charset)
  }

  /**
   * The response body as Xml.
   */
  lazy val xml: Elem = Play.XML.loadString(body)

  /**
   * The response body as Json.
   */
  lazy val json: JsValue = Json.parse(ahcResponse.getResponseBodyAsBytes)

}
