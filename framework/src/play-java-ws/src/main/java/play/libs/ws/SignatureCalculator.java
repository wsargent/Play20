package play.libs.ws;

/**
 * Sign a WS call.
 */
public interface SignatureCalculator {

    /**
     * Sign a request
     */
    public void sign(WSRequest request);

}