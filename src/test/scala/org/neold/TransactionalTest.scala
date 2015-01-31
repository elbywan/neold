package org.neold

import TestingTools._
import org.scalatest._
import org.neold.core.Neold, org.neold.core.Neold._
import play.api.libs.json._

class TransactionalTest extends FlatSpec with Matchers {

    "[TRANSACTIONAL ENDPOINT] Neold" should "be able to create an index" in {
        val stringResult = waitCompletion(neo.createIndex(TEST_LABEL, TEST_PROPERTY)())
        val jsonResult = Json.parse(stringResult)
        (jsonResult \ "label").as[String] should equal (TEST_LABEL)
    }

    it should "be able to list indexes " in {
        val stringResult = waitCompletion(neo.listIndexes(TEST_LABEL)())
        val jsonResult : JsArray = Json.parse(stringResult).asInstanceOf[JsArray]
        jsonResult.value.exists{ (value : JsValue) => (value \ "label").asOpt[String] equals Some(TEST_LABEL) } shouldBe true
    }

    it should "be able to drop an index" in {
        waitCompletion(neo.dropIndex(TEST_LABEL, TEST_PROPERTY))
        val stringResult = waitCompletion(neo.listIndexes(TEST_LABEL)())
        val jsonResult : JsArray = Json.parse(stringResult).asInstanceOf[JsArray]
        jsonResult.value.exists{ (value : JsValue) => (value \ "label").asOpt[String] equals Some(TEST_LABEL) } shouldBe false
    }
    
    it can "execute and commit a single statement" in {
        def getCount() : Int = {
            val jsonResults = Json.parse(waitCompletion(neo.executeImmediate1(countQuery, params)())) \ "results"
            ((jsonResults(0) \ "data")(0) \ "row")(0).as[Int]
        }
        val beforeCount = getCount()
        waitCompletion(neo.executeImmediate1(insertQuery, params)())
        val duringCount = getCount()
        waitCompletion(neo.executeImmediate1(deleteQuery, params)())
        val afterCount = getCount()

        beforeCount should equal (duringCount - 1)
        beforeCount should equal (afterCount)
    }

    it can "execute and commit multiple statements" in {
        val statements = (countQuery -> params) ::
            (insertQuery -> params) ::
            (countQuery -> params)  ::
            (deleteQuery -> params) ::
            (countQuery -> params)  ::
            List()

        val results = (Json.parse(waitCompletion(neo.executeImmediate(statements : _*)())) \ "results").asInstanceOf[JsArray]

        results.value.length should equal (5)
        val beforeCount = ((results(0) \ "data")(0) \ "row")(0).as[Int]
        val duringCount = ((results(2) \ "data")(0) \ "row")(0).as[Int]
        val afterCount = ((results(4) \ "data")(0) \ "row")(0).as[Int]

        beforeCount should equal (duringCount - 1)
        beforeCount should equal (afterCount)
    }

    it can "execute and rollback transactions" in {
        def getCount(rawString : String) : Int = {
            val jsonResults = Json.parse(rawString) \ "results"
            ((jsonResults(0) \ "data")(0) \ "row")(0).as[Int]
        }
        def getCountImmediate() : Int = {
            val jsonResults = Json.parse(waitCompletion(neo.executeImmediate1(countQuery, params)())) \ "results"
            ((jsonResults(0) \ "data")(0) \ "row")(0).as[Int]
        }

        waitCompletion(neo.initTransaction(countQuery, params){t => s:String => ()}) match {
            case (transaction, result) =>
                val beforeCount = getCount(result)
                waitCompletion(transaction.post1(insertQuery, params)())
                val duringCount = getCount(waitCompletion(transaction.post1(countQuery, params)()))
                waitCompletion(transaction.rollback()())
                val afterCount = getCountImmediate()

                beforeCount should equal (duringCount - 1)
                beforeCount should equal (afterCount)
        }
    }

    it can "execute and commit transactions" in {
        def getCount(rawString : String) : Int = {
            val jsonResults = Json.parse(rawString) \ "results"
            ((jsonResults(0) \ "data")(0) \ "row")(0).as[Int]
        }
        def getCountImmediate() : Int = {
            val jsonResults = Json.parse(waitCompletion(neo.executeImmediate1(countQuery, params)())) \ "results"
            ((jsonResults(0) \ "data")(0) \ "row")(0).as[Int]
        }

        waitCompletion(neo.initTransaction(countQuery, params){t => s:String => ()}) match {
            case (transaction, result) =>
                val beforeCount = getCount(result)
                waitCompletion(transaction.post1(insertQuery, params)())
                val duringCount = getCount(waitCompletion(transaction.post1(countQuery, params)()))
                waitCompletion(transaction.commit()())
                val afterCount = getCountImmediate()
                waitCompletion(neo.executeImmediate1(deleteQuery, params)())

                beforeCount should equal (duringCount - 1)
                beforeCount should equal (afterCount - 1)
        }
    }

}
