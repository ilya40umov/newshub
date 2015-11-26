package org.i40u.newshub

/**
 * @author ilya40umov
 */
class NewsHubPackageSpec extends NewsHubSpec {

  "The md5Hash function" should "be consistent for the same inputs " +
    "and return only alphanumeric strings consisting of 32 characters" in {
    md5Hash("http://stackoverflow.com/") shouldBe "e4a42d992025b928a586b8bdc36ad38d"
    md5Hash("https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-id-field.html") should
      be("4c3476a3b40ae2ecb5f785e92183c76d")
  }

}
