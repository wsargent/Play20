package play.libs.ws;

import play.libs.F;

import java.util.List;
import java.util.Map;

/**
 *
 */
public interface WSRequest {


    F.Promise<WSResponse> execute();
}
