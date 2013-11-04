/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play.libs.ws;

import play.libs.ws.ning.NingWSClient;
import play.libs.ws.ning.NingWSRequestHolder;


/**
 * Asynchronous API to to query web services, as an http client.
 *
 * The value returned is a Promise<Response>, and you should use Play's asynchronous mechanisms to use this response.
 */
public class WS {

    public static WSClient client() {
        // XXX Need to abstract this to the API
        return new NingWSClient(play.api.libs.ws.WS.client(play.api.Play.unsafeApplication()));
    }

    /**
     * Prepare a new request. You can then construct it by chaining calls.
     *
     * @param url the URL to request
     */
    public static WSRequestHolder url(String url) {
        // XXX Need to abstract this to the API
        return new NingWSRequestHolder((NingWSClient) client(), url);
    }

}



