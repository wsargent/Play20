package play.libs.ws;

/**
 * Sign a WS call.
 */
public interface WSSignatureCalculator {

    /**
     * Sign a request
     */
    public void sign(WSRequest request);

}