package play.libs.ws;


import com.ning.http.client.FluentStringsMap;
import com.ning.http.client.PerRequestConfig;
import com.ning.http.client.RequestBuilderBase;
import play.libs.ws.ning.NingWSRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public interface WSRequest {

    Object getUnderlying();

    Map<String, List<String>> getAllHeaders();

    String getMethod();

    List<String> getHeader(String name);

    String getUrl();

    WSRequest setUrl(String url);

    WSRequest setHeader(String name, String value);
}
