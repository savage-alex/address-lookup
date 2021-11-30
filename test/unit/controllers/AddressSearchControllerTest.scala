/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import akka.stream.Materializer
import controllers.services.{ReferenceData, ResponseProcessor}
import model.address._
import model.internal.DbAddress
import model.request.{LookupByPostcodeRequest, LookupByUprnRequest}
import org.mockito.ArgumentMatchers.{eq => meq}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import repositories.AddressLookupRepository
import uk.gov.hmrc.http.UpstreamErrorResponse
import util.Utils._

import scala.concurrent.Future
import scala.concurrent.duration._

class AddressSearchControllerTest extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout: FiniteDuration = 1 second
  val cc = play.api.test.Helpers.stubControllerComponents()

  private val en = "en"

  import model.address.Country._

  val addr1Db = DbAddress("GB100001", List("10 Test Court", "Test Street", "Tester"), "Test upon Tyne", "FX1 5XD", Some("GB-ENG"), Some("GB"), Some(4510), Some("en"), None, Some(Location("12.345678", "-12.345678").toString), None, Some("TestLocalAuthority"))
  val addr1Ar = AddressRecord("GB100001", Some(100001L), Address(List("10 Test Court", "Test Street", "Tester"), "Test upon Tyne", "FX1 5XD", Some(England), GB), en, Some(LocalCustodian(4510, "Test upon Tyne")), Some(Location("12.345678", "-12.345678").toSeq), Some("TestLocalAuthority"))

  val dx1A = DbAddress("GB100002", List("1 Test Street"), "Testtown", "FZ22 7ZW", Some("GB-XXX"), Some("GB"), Some(9999), Some("en"), None, Some("54.914561,-1.3905597"), None, Some("TestLocalAuthority"))
  val dx1B = DbAddress("GB100003", List("2 Test Street"), "Testtown", "FZ22 7ZW", Some("GB-XXX"), Some("GB"), Some(9999), Some("en"), None, Some("54.914561,-1.3905597"), None, Some("TestLocalAuthority"))
  val dx1C = DbAddress("GB100004", List("3 Test Street"), "Testtown", "FZ22 7ZW", Some("GB-XXX"), Some("GB"), Some(9999), Some("en"), None, Some("54.914561,-1.3905597"), None, Some("TestLocalAuthority"))

  val fx1A = AddressRecord("GB100002", Some(100002L), Address(List("1 Test Street"), "Testtown", "FZ22 7ZW", Some(England), GB), en, Some(LocalCustodian(9999, "Somewhere")), Some(Location("0,0").toSeq), Some("TestLocalAuthority"))
  val fx1B = AddressRecord("GB100003", Some(100003L), Address(List("2 Test Street"), "Testtown", "FZ22 7ZW", Some(England), GB), en, Some(LocalCustodian(9999, "Somewhere")), Some(Location("0,0").toSeq), Some("TestLocalAuthority"))
  val fx1C = AddressRecord("GB100004", Some(100004L), Address(List("3 Test Street"), "Testtown", "FZ22 7ZW", Some(England), GB), en, Some(LocalCustodian(9999, "Somewhere")), Some(Location("0,0").toSeq), Some("TestLocalAuthority"))

  val addressDb1 = DbAddress("GB100005", List("Test Road"), "ATown", "FX11 7LX", Some("GB-ENG"), Some("GB"), Some(2935), Some("en"), None, Some(Location("12.345678", "-12.345678").toString), None, Some("TestLocalAuthority"))
  val addressDb2 = DbAddress("GB100006", List("ARoad", "ARoad"), "Atown", "FX11 7LA", Some("GB-ENG"), Some("GB"), Some(2935), Some("en"), None, Some(Location("12.345678", "-12.345678").toString), None, Some("TestLocalAuthority"))

  val addressAr1 = AddressRecord("GB100005", Some(100005L), Address(List("Test Road"), "ATown", "FX11 7LX", Some(England), GB), en, Some(LocalCustodian(2935, "Testland")), Some(Location("12.345678", "-12.345678").toSeq), Some("TestLocalAuthority"))
  val addressAr2 = AddressRecord("GB100006", Some(100006L), Address(List("Test Station", "Test Road"), "ATown", "FX11 7LA", Some(England), GB), en, Some(LocalCustodian(2935, "Testland")), Some(Location("12.345678", "-12.345678").toSeq), Some("TestLocalAuthority"))

  val searcher: AddressLookupRepository = mock[AddressLookupRepository]
  override implicit lazy val app: Application = {
    new GuiceApplicationBuilder()
        .overrides(inject.bind[AddressLookupRepository]toInstance(searcher))
        .build()
  }
  val controller: AddressSearchController = app.injector.instanceOf[AddressSearchController]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  class ResponseStub(a: List[AddressRecord]) extends ResponseProcessor(ReferenceData.empty) {
    override def convertAddressList(dbAddresses: Seq[DbAddress]): List[AddressRecord] = a
  }

  "postcode lookup with POST requests" should {

    "give bad request" when {

      """when search is called without 'X-Origin' header
       it should give a 'bad request' response via the appropriate exception
       and not log any error
      """ in {
        import LookupByPostcodeRequest._
        val jsonPayload = Json.toJson(LookupByPostcodeRequest(Postcode("FX11 4HG")))
        val request: Request[String] = FakeRequest("POST", "/lookup")
            .withBody(jsonPayload.toString())

        intercept[UpstreamErrorResponse] {
          val result = controller.search().apply(request)
          status(result) shouldBe Status.BAD_REQUEST
        }
      }
    }


    "successful findPostcode" when {

      """when search is called with correct parameters
       it should clean up the postcode parameter
       and give an 'ok' response
       and log the lookup including the size of the result list
      """ in {
        when(searcher.findPostcode(Postcode("FX11 4HG"), Some("FOO"))) thenReturn Future(List(addr1Db))
        val jsonPayload = Json.toJson(LookupByPostcodeRequest(Postcode("FX11 4HG"), Some("FOO")))
        val request = FakeRequest("POST", "/lookup")
          .withBody(jsonPayload.toString)
          .withHeadersOrigin

        val result = controller.search().apply(request)
        status(result) shouldBe Status.OK
      }

      """when search is called with blank filter
       it should clean up the postcode parameter
       and give an 'ok' response as if the filter parameter wass absent
       and log the lookup including the size of the result list
      """ in {
        when(searcher.findPostcode(Postcode("FX11 4HG"), None)) thenReturn Future(List(addr1Db))
        val jsonPayload = Json.toJson(LookupByPostcodeRequest(Postcode("FX11 4HG"), None))
        val request = FakeRequest("POST", "/lookup")
          .withBody(jsonPayload.toString)
          .withHeadersOrigin

        val result = controller.search().apply(request)
        status(result) shouldBe Status.OK
      }

      """when search is called with a postcode that will give several results
       it should give an 'ok' response containing the result list
       and log the lookup including the size of the result list
      """ in {
        when(searcher.findPostcode(Postcode("FX11 4HG"), None)) thenReturn Future(List(dx1A, dx1B, dx1C))
        val jsonPayload = Json.toJson(LookupByPostcodeRequest(Postcode("FX11 4HG")))
        val request = FakeRequest("POST", "/lookup")
          .withBody(jsonPayload.toString)
          .withHeadersOrigin

        val result = controller.search().apply(request)
        status(result) shouldBe Status.OK
      }
    }
  }

  "uprn lookup with POST request" should {
    "give success" when {
      """search is called with a valid uprn""" in {
        import LookupByUprnRequest._
        when(searcher.findUprn(meq("0123456789"))).thenReturn(Future.successful(List()))
        val jsonPayload = Json.toJson(LookupByUprnRequest("0123456789"))
        val request = FakeRequest("POST", "/lookup/by-uprn")
        .withBody(jsonPayload.toString)
        .withHeadersOrigin

        val response = controller.searchByUprn().apply(request)
        status(response) shouldBe 200
      }
    }

    "give bad request" when {
      """search is called with an invalid uprn""" in {
        val controller = new AddressSearchController(searcher, new ResponseStub(Nil), ec, cc)
        val jsonPayload = Json.toJson(LookupByUprnRequest("GB0123456789"))
        val request = FakeRequest("POST", "/lookup/by-uprn")
        .withBody(jsonPayload.toString)
        .withHeadersOrigin

        val response = controller.searchByUprn().apply(request)
        status(response) shouldBe 400
      }
    }

  }
}