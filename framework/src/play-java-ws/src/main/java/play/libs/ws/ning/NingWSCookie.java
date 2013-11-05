package play.libs.ws.ning;

import play.libs.ws.*;

/**
 * The Ning implementation of a WS cookie.
 */
public class NingWSCookie implements WSCookie {

    private final com.ning.http.client.Cookie ahcCookie;

    public NingWSCookie(com.ning.http.client.Cookie ahcCookie) {
        this.ahcCookie = ahcCookie;
    }

    /**
     * Returns the underlying "native" object for the cookie.
     */
    public com.ning.http.client.Cookie getUnderlying() {
        return ahcCookie;
    }

    public String getDomain() {
        return ahcCookie.getDomain();
    }

    public String getName() {
        return ahcCookie.getName();
    }

    public String getValue() {
        return ahcCookie.getValue();
    }

    public String getPath() {
        return ahcCookie.getPath();
    }

    public Integer getMaxAge() {
        return ahcCookie.getMaxAge();
    }

    public Boolean isSecure() {
        return ahcCookie.isSecure();
    }

    public Integer getVersion() {
        return ahcCookie.getVersion();
    }
}
