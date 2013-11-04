package play.libs.ws;

import play.libs.F;

/**
 *
 */
public interface WSRequest {


    F.Promise<WSResponse> execute();
}
