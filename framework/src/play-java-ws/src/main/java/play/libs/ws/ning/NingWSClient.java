package play.libs.ws.ning;

import com.ning.http.client.AsyncHttpClient;
import play.libs.ws.WSClient;

public class NingWSClient extends AsyncHttpClient implements WSClient {

    public Object getUnderlying() {
        return this;
    }
}
