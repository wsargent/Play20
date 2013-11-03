package play.libs.ws.ning;

import com.ning.http.client.Realm;
import play.libs.ws.WSAuthScheme;
import play.libs.ws.WSClient;

public class NingWSClient implements WSClient {

    private play.api.libs.ws.WSClient client;

    public NingWSClient(play.api.libs.ws.WSClient client) {
        this.client = client;
    }

    public Object getUnderlying() {
        return this.client.underlying();
    }

    public Realm.AuthScheme getAuthScheme(WSAuthScheme scheme) {
        return Realm.AuthScheme.valueOf(scheme.name());
    }
}
