/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play.api.libs.oauth

import _root_.oauth.signpost.{ OAuthConsumer, OAuthProvider }
import _root_.oauth.signpost.exception.OAuthException
import _root_.oauth.signpost.basic.DefaultOAuthConsumer
import _root_.oauth.signpost.commonshttp.CommonsHttpOAuthProvider
import _root_.oauth.signpost.{ OAuthConsumer, AbstractOAuthConsumer }
import oauth._

import play.api.libs.ws._

import scala.concurrent.stm.Ref

/**
 * Library to access resources protected by OAuth 1.0a.
 *  @param info the service information, including the required URLs and the application id and secret
 *  @param use10a whether the service should use the 1.0 version of the spec, or the 1.0a version fixing a security issue.
 *  You must use the version corresponding to the
 */
case class OAuth(info: ServiceInfo, use10a: Boolean = true) {

  private val provider = {
    val p = new CommonsHttpOAuthProvider(info.requestTokenURL, info.accessTokenURL, info.authorizationURL)
    p.setOAuth10a(use10a)
    p
  }

  /**
   * Request the request token and secret.
   *
   * @param callbackURL the URL where the provider should redirect to (usually a URL on the current app)
   * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
   */
  def retrieveRequestToken(callbackURL: String): Either[OAuthException, RequestToken] = {
    val consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret)
    try {
      provider.retrieveRequestToken(consumer, callbackURL)
      Right(RequestToken(consumer.getToken(), consumer.getTokenSecret()))
    } catch {
      case e: OAuthException => Left(e)
    }
  }

  /**
   * Exchange a request token for an access token.
   *
   * @param token the token/secret pair obtained from a previous call
   * @param verifier a string you got through your user, with redirection
   * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
   */
  def retrieveAccessToken(token: RequestToken, verifier: String): Either[OAuthException, RequestToken] = {
    val consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret)
    consumer.setTokenWithSecret(token.token, token.secret)
    try {
      provider.retrieveAccessToken(consumer, verifier)
      Right(RequestToken(consumer.getToken(), consumer.getTokenSecret()))
    } catch {
      case e: OAuthException => Left(e)
    }
  }

  /**
   * The URL where the user needs to be redirected to grant authorization to your application.
   *
   * @param token request token
   */
  def redirectUrl(token: String): String = {
    _root_.oauth.signpost.OAuth.addQueryParameters(
      provider.getAuthorizationWebsiteUrl(),
      _root_.oauth.signpost.OAuth.OAUTH_TOKEN,
      token
    )
  }

}

/**
 * A consumer key / consumer secret pair that the OAuth provider gave you, to identify your application.
 */
case class ConsumerKey(key: String, secret: String)

/**
 * A request token / token secret pair, to be used for a specific user.
 */
case class RequestToken(token: String, secret: String)

/**
 * The information identifying a oauth provider: URLs and the consumer key / consumer secret pair.
 */
case class ServiceInfo(requestTokenURL: String, accessTokenURL: String, authorizationURL: String, key: ConsumerKey)

/**
 * A signature calculator for the Play WS API.
 *
 * Example:
 * {{{
 * WS.url("http://example.com/protected").sign(OAuthCalculator(service, tokens)).get()
 * }}}
 */
case class OAuthCalculator(consumerKey: ConsumerKey, token: RequestToken) extends AbstractOAuthConsumer(consumerKey.key, consumerKey.secret) with WSSignatureCalculator {

  import _root_.oauth.signpost.http.HttpRequest

  this.setTokenWithSecret(token.token, token.secret)

  override protected def wrap(request: Any) = request match {
    case (r: WSRequestHolder, m: String, b: Option[WSRequestBody]) => new WSRequestAdapter(r, m, b)
    case _ => throw new IllegalArgumentException("OAuthCalculator expects requests of type Tuple3(play.api.libs.WS.WSRequestHolder, String, Option[WSRequestBody])")
  }


  override def sign[T <: WSRequestHolder](request: T, method: String, body: Option[WSRequestBody]): T = {
    val wrappedRequest = wrap((request, method, body))
    sign(wrappedRequest)
    wrappedRequest.asInstanceOf[WSRequestAdapter[T]].unwrap
  }

  class WSRequestAdapter[T <: WSRequestHolder](req: T, method: String, body: Option[WSRequestBody]) extends HttpRequest {

    val request = Ref[T](req)

    import scala.collection.JavaConverters._

    override def unwrap(): T = request.single.get

    override def getAllHeaders(): java.util.Map[String, String] =
      request.single.get.headers.map { entry => (entry._1, entry._2.headOption) }
        .filter { entry => entry._2.isDefined }
        .map { entry => (entry._1, entry._2.get) }.asJava

    override def getHeader(name: String): String = request.single.get.headers(name).headOption.getOrElse("")

    override def getContentType(): String = getHeader("Content-Type")

    override def getMessagePayload() = body match {
      case Some(f: WSRequestBodyFile) => new java.io.FileInputStream(f.getFile)
      case Some(b: WSRequestBodyWriteable[AnyRef]) => new java.io.ByteArrayInputStream(b.transform)
      case None => new java.io.ByteArrayInputStream(Array[Byte]())
    }

    override def getMethod(): String = method

    override def setHeader(name: String, value: String) {
      request.single.transform(
        _.withHeaders(name -> value).asInstanceOf[T]
      )
    }

    override def getRequestUrl() = request.single.get.url

    override def setRequestUrl(url: String) {
      request.single.transform(
        _.setUrl(url).asInstanceOf[T]
      )
    }
  }

}
