package org.i40u.newshub.http

import com.ning.http.client.ProxyServer

/**
 * @author ilya40umov
 */
object HttpProxy {
  def apply(proxyId: String): HttpProxy = proxyId.split(":") match {
    case Array(host, port) => HttpProxy(proxyId, new ProxyServer(host, port.toInt))
  }
}

case class HttpProxy(proxyId: String, proxyServer: ProxyServer)