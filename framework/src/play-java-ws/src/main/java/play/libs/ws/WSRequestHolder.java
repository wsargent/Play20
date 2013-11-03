package play.libs.ws;


import com.fasterxml.jackson.databind.JsonNode;
import com.ning.http.client.Realm;
import play.libs.F;

import java.io.File;
import java.io.InputStream;

public interface WSRequestHolder {

    String getUsername();

    String getPassword();

    WSAuthScheme getScheme();

    WSSignatureCalculator getCalculator();

    int getTimeout();

    Boolean getFollowRedirects();

    F.Promise<WSResponse> get();

    F.Promise<WSResponse> patch(String body);

    F.Promise<WSResponse> post(String body);

    F.Promise<WSResponse> put(String body);

    F.Promise<WSResponse> patch(JsonNode body);

    F.Promise<WSResponse> post(JsonNode body);

    F.Promise<WSResponse> put(JsonNode body);

    F.Promise<WSResponse> patch(InputStream body);

    F.Promise<WSResponse> post(InputStream body);

    F.Promise<WSResponse> put(InputStream body);

    F.Promise<WSResponse> post(File body);

    F.Promise<WSResponse> put(File body);

    F.Promise<WSResponse> delete();

    F.Promise<WSResponse> head();

    F.Promise<WSResponse> options();

    F.Promise<WSResponse> execute(String method);
}
