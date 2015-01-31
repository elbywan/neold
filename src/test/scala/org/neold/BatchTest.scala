package org.neold

import TestingTools._

import org.scalatest._
import play.api.libs.json._

class BatchTest extends FlatSpec with Matchers {

    "[BATCH ENDPOINT] Neold" can "buffer and execute statements" in {
        def parseCount(raw: JsArray, id: Integer): Int ={
            (((raw)(id) \ "body") \ "data")(0)(0).as[Int]
        }

        neo.bufferQuery(countQuery, params)
        neo.bufferQuery(insertQuery, params)
        neo.bufferQuery(countQuery, params)
        neo.bufferQuery(deleteQuery, params)
        neo.bufferQuery(countQuery, params)

        val results = (Json.parse(waitCompletion(neo.performBatch()()))).asInstanceOf[JsArray]

        val beforeCount = parseCount(results, 0)
        val duringCount = parseCount(results, 2)
        val afterCount = parseCount(results, 4)

        beforeCount should equal (duringCount - 1)
        beforeCount should equal (afterCount)
    }

}
