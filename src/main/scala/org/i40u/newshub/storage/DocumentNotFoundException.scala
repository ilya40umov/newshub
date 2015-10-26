package org.i40u.newshub.storage

/**
 * @author ilya40umov
 */
class DocumentNotFoundException(message: String = null, cause: Throwable = null)
  extends RuntimeException(message, cause)
