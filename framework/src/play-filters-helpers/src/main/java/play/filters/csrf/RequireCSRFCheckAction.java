/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.filters.csrf;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import play.api.http.SessionConfiguration;
import play.api.libs.crypto.CSRFTokenSigner;
import play.api.mvc.RequestHeader;
import play.api.mvc.Session;
import play.inject.Injector;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;

public class RequireCSRFCheckAction extends Action<RequireCSRFCheck> {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RequireCSRFCheckAction.class);

    private final CSRFConfig config;
    private final SessionConfiguration sessionConfiguration;
    private final CSRF.TokenProvider tokenProvider;
    private final CSRFTokenSigner tokenSigner;
    private final Injector injector;

    @Inject
    public RequireCSRFCheckAction(CSRFConfig config, SessionConfiguration sessionConfiguration, CSRF.TokenProvider tokenProvider, CSRFTokenSigner csrfTokenSigner, Injector injector) {
        this.config = config;
        this.sessionConfiguration = sessionConfiguration;
        this.tokenProvider = tokenProvider;
        this.tokenSigner = csrfTokenSigner;
        this.injector = injector;
    }

    @Override
    public CompletionStage<Result> call(Http.Context ctx) {

        CSRFActionHelper csrfActionHelper = new CSRFActionHelper(sessionConfiguration, config, tokenSigner);

        RequestHeader request = csrfActionHelper.tagRequestFromHeader(ctx._requestHeader());
        // Check for bypass
        if (!csrfActionHelper.requiresCsrfCheck(request)) {
            return delegate.call(ctx);
        } else {
            // Get token from cookie/session
            Option<String> headerToken = csrfActionHelper.getTokenToValidate(request);
            logger.trace("call: headerToken = {}", headerToken);
            if (headerToken.isDefined()) {
                String tokenToCheck = null;

                // Get token from query string
                Option<String> queryStringToken = csrfActionHelper.getHeaderToken(request);
                if (queryStringToken.isDefined()) {
                    tokenToCheck = queryStringToken.get();
                } else {

                    // Get token from body
                    final Map<String, String[]> formUrlEncodedMap = ctx.request().body().asFormUrlEncoded();
                    formUrlEncodedMap.forEach((k, v) -> {
                        logger.trace("formUrlEncodedMap: k = {}, v = {}", k, v);
                    });
                    if (formUrlEncodedMap != null) {
                        String[] values = formUrlEncodedMap.get(config.tokenName());
                        if (values != null) {
                            if (values.length == 1) {
                                tokenToCheck = values[0];
                            } else {
                                logger.error("Illegal number of tokens: {}", Arrays.asList(values));
                            }
                        }
                        logger.trace("call: tokenToCheck from asFormUrlEncoded body = " + tokenToCheck);
                    } else if (ctx.request().body().asMultipartFormData() != null) {
                        Map<String, String[]> form = ctx.request().body().asMultipartFormData().asFormUrlEncoded();
                        String[] values = form.get(config.tokenName());
                        if (values != null) {
                            if (values.length == 1) {
                                tokenToCheck = values[0];
                            } else {
                                logger.error("Illegal number of tokens: {}", Arrays.asList(values));
                            }
                        }
                    }
                }

                logger.trace("call: tokenToCheck = " + tokenToCheck);
                if (tokenToCheck != null) {
                    final String headerTokenValue = headerToken.get();
                    if (tokenProvider.compareTokens(tokenToCheck, headerTokenValue)) {
                        return delegate.call(ctx);
                    } else {
                        logger.trace("call: tokenToCheck = {}, headerTokenValue = {}", tokenToCheck, headerTokenValue);
                        return handleTokenError(ctx, request, "CSRF tokens don't match");
                    }
                } else {
                    return handleTokenError(ctx, request, "CSRF token not found in body or query string");
                }
            } else {
                return handleTokenError(ctx, request, "CSRF token not found in session");
            }
        }
    }

    private CompletionStage<Result> handleTokenError(Http.Context ctx, RequestHeader request, String msg) {

        if (CSRF.getToken(request).isEmpty()) {
            if (config.cookieName().isDefined()) {
                Option<String> domain = Session.domain();
                ctx.response().discardCookie(config.cookieName().get(), Session.path(),
                        domain.isDefined() ? domain.get() : null, config.secureCookie());
            } else {
                ctx.session().remove(config.tokenName());
            }
        }

        CSRFErrorHandler handler = injector.instanceOf(configuration.error());
        return handler.handle(new play.core.j.RequestHeaderImpl(request), msg);
    }
}
