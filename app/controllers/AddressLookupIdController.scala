/*
 * Copyright 2022 HM Revenue & Customs
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

import model.address.AddressRecord
import play.api.libs.json.Json
import play.api.mvc._
import repositories.ABPAddressRepository
import services.ResponseProcessor

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AddressLookupIdController @Inject()(abpAddressSearch: ABPAddressRepository, responseProcessor: ResponseProcessor,
                                          ec: ExecutionContext, cc: ControllerComponents)
  extends AddressController(cc) {

  implicit private val xec: ExecutionContext = ec

  def findById(id: String): Action[AnyContent] = Action.async { request =>
    findByIdRequest(request, id)
  }

  private[controllers] def findByIdRequest[A](request: Request[A], id: String): Future[Result] = {
    getOriginHeaderIfSatisfactory(request.headers)

    Try(abpAddressSearch.findID(id).map { a =>
      import AddressRecord.formats._
      a.headOption.fold(NotFound(s"id matched nothing")) { ad =>
        Ok(Json.toJson(responseProcessor.convertAddress(ad)))
      }
    }).getOrElse(Future.successful(BadRequest(s"Check the id supplied: $id")))
  }
}
