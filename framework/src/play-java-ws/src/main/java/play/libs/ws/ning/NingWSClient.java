package play.libs.ws.ning;

import com.ning.http.client.AsyncHttpClient;
import play.libs.ws.WSClient;

public class NingWSClient implements WSClient<AsyncHttpClient> {

    private play.api.libs.ws.WSClient client;

    public NingWSClient(play.api.libs.ws.WSClient client) {
        this.client = client;
    }

    public AsyncHttpClient getUnderlying() {
        return this.client.underlying();
    }

}
