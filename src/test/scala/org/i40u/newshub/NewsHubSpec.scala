package org.i40u.newshub

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpecLike, Matchers, FlatSpec}

/**
 * @author ilya40umov
 */
trait NewsHubSpec extends FlatSpec with Matchers with MockFactory

trait NewsHubSpecLike extends FlatSpecLike with Matchers with MockFactory
