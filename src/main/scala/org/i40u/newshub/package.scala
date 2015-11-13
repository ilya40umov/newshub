package org.i40u

import java.security.MessageDigest

/**
 * @author ilya40umov
 */
package object newshub {

  def md5Hash(string: String): String = {
    MessageDigest.getInstance("MD5").digest(string.getBytes).map("%02x".format(_)).mkString
  }

}
