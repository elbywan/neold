package org.neold

import TestingTools._

import org.scalatest._
import org.neold.adapters.Adapters._
import org.neold.adapters.Adapters.BatchAdapter._

class BatchTest extends FlatSpec with Matchers {

    "[BATCH ENDPOINT] Neold" can "buffer and execute statements" in {
        def countOpt(results : String, id : Int) = {
            mapResultRow(toOption(results), 0, id){
                _("count(n)").toInt
            }.getOrElse(-1)
        }
        def countEither(results : String, id : Int) = {
            toEither(results).fold(
                _ => -1,
                _(id)(0)("count(n)").toInt
            )
        }

        neo.bufferQuery(countQuery, params)
        neo.bufferQuery(insertQueryObjectParam, paramsObject)
        neo.bufferQuery(countQuery, params)
        neo.bufferQuery(deleteQuery, params)
        neo.bufferQuery(countQuery, params)

        val results = waitCompletion(neo.performBatch()())

        val beforeCount = countOpt(results, 0)
        val duringCount = countOpt(results, 2)
        val afterCount = countOpt(results, 4)

        beforeCount should equal (duringCount - 1)
        beforeCount should equal (afterCount)
    }

}
