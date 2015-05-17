package org.neold

import java.util.UUID
import java.util.concurrent.TimeUnit

import org.neold.core.Neold
import org.neold.core.Neold._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object TestingTools {

    val neo = Neold()

    val TEST_LABEL = "TestNode"
    val TEST_PROPERTY = "TestProperty"
    val TEST_UUID = UUID.randomUUID().toString

    val countQuery = s"""MATCH (n:$TEST_LABEL {$TEST_PROPERTY: {propertyValue}}) RETURN count(n)"""
    val insertQuery = s"""CREATE (n:$TEST_LABEL {$TEST_PROPERTY: {propertyValue}}) RETURN n"""
    val insertQueryObjectParam = s"""CREATE (n:$TEST_LABEL {propertyValue}) RETURN n"""
    def insertQueryId(id: Int) = { s"""CREATE (n:$TEST_LABEL {$TEST_PROPERTY: {propertyValue}, id: $id}) RETURN n""" }
    val deleteQuery = s"""MATCH (n:$TEST_LABEL {$TEST_PROPERTY: {propertyValue}}) DELETE n"""
    val fullDeleteQuery = s"MATCH (n:$TEST_LABEL) DELETE n"
    val getQuery = s"MATCH (n:$TEST_LABEL) RETURN n.$TEST_PROPERTY as $TEST_PROPERTY ORDER BY n.id"
    val params = Map("propertyValue" -> TEST_UUID)
    val paramsObject  = Map("propertyValue" -> s"""{ "$TEST_PROPERTY": "$TEST_UUID" }""")
    val escapeParams = Map("propertyValue" -> :?("{test}"))
    val unescapeParams = Map("propertyValue" -> :!("10"))

    def waitCompletion[A](request : Future[A]) : A = {
        Await.result(request, Duration.create(5, TimeUnit.SECONDS))
    }
}
