package org.i40u.newshub.api

import org.i40u.newshub.Kernel
import org.i40u.newshub.storage.Storage

/**
 * @author ilya40umov
 */
trait Api {
  // TODO implement
}

trait DefaultApi extends Api {
  self: Kernel with Storage =>

}
