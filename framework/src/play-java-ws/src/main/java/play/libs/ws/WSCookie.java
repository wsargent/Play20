package play.libs.ws;


/**
 * A WS Cookie.
 */
public interface WSCookie {

    /**
     * Returns the underlying "native" object for the cookie.
     */
    public Object getUnderlying();

    public String getDomain();

    public String getName();

    public String getValue();

    public String getPath();

    public Integer getMaxAge();

    public Boolean isSecure();

    public Integer getVersion();

    // Cookie ports should not be used; cookies for a given host are shared across
    // all the ports on that host.
}
