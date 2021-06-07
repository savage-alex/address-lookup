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

package osgb.inmodel

import play.api.libs.json.{Format, Json}

case class LookupRequest(postcode: Option[String] = None, line1: Option[String] = None, line2: Option[String] = None,
                         line3: Option[String] = None, line4: Option[String] = None, uprn: Option[String] = None,
                         outcode: Option[String] = None, fuzzy: Option[String] = None, town: Option[String] = None,
                         filter: Option[String] = None, limit: Option[Int] = None)

object LookupRequest {
  implicit val formats: Format[LookupRequest] = Json.format[LookupRequest]
}

case class LookupPostcode(postcode: String)

object LookupPostcode {
  implicit val formats: Format[LookupPostcode] = Json.format[LookupPostcode]
}