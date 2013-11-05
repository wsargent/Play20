package play.libs.ws.ning

import org.specs2.mutable._

object NingWSSpec extends Specification {

  "NingWSRequest" should {

    "should respond to getMethod" in {
      val request : NingWSRequest = new NingWSRequest("GET")
      request.getMethod must be_==("GET")
    }

  }

}
