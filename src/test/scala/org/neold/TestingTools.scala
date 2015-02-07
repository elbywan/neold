package org.neold

import java.util.UUID
import java.util.concurrent.TimeUnit

import org.neold.core.Neold

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
    val deleteQuery = s"""MATCH (n:$TEST_LABEL {$TEST_PROPERTY: {propertyValue}}) DELETE n"""
    val params = Map("propertyValue" -> TEST_UUID)
    var paramsObject  = Map("propertyValue" -> s"""{ "$TEST_PROPERTY": "$TEST_UUID" }""")

    def waitCompletion[A](request : Future[A]) : A = {
        Await.result(request, Duration.create(5, TimeUnit.SECONDS))
    }
}
