| Neold | [![Build Status](https://travis-ci.org/elbywan/neold.svg?branch=master)](https://travis-ci.org/elbywan/neold) | [![Coverage Status](https://coveralls.io/repos/elbywan/neold/badge.svg?branch=master)](https://coveralls.io/r/elbywan/neold?branch=master)
=====

**Neold** is an high performance, programmer-friendly asynchronous Neo4j REST client driver written in scala.

------

- Main branch : releases.
- Dev branch : snapshots.

------

##Installation

Add the following lines to your sbt build file :

```scala
resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies += "com.github.elbywan" %% "neold" % "0.2"
```

To play around with the library, type `sbt console`.

##Usage

Neold can interact with the [Transactional endpoint](http://neo4j.com/docs/stable/rest-api.html) or the [Batch endpoint](http://neo4j.com/docs/stable/rest-api-batch-ops.html).

*All the sample code below require the following import line :*
`import org.neold.core.Neold, org.neold.core.Neold._`

*If you want to copy/paste these code snippets somewhere, don't forget that they are <b>asynchronous</b>.*

*If you want to use the library <b>synchronously</b> :*
```scala
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import java.util.concurrent.TimeUnit

val future = neo.executeImmediate(query, params)
val result = Await.result(future, Duration.create(5, TimeUnit.SECONDS))
```

#### Neold setup

#####Server location

```scala
//Setups Neold with the default endpoint : http://localhost:7474/db/data
Neold("localhost", 7474, "db/data")
Neold("localhost", 7474)
Neold()
//Those 3 lines are equivalent
```

#####Authentication

Provide your [user and password](http://neo4j.com/docs/stable/rest-api-security.html#rest-api-authenticate-to-access-the-server) in the `username` and `password` parameters.
The `secure` flag (default to false) controls whether the request is sent over https.

```scala
Neold(username = "Joe", password = "Dalton", secure = true)
```

#### On shutdown
Call `Neold.shutdown()` to shut down the underlying thread pool.

### Transactional endpoint

The transactional endpoint is the default way to interact with Neo4j.

####Executing statements and commiting in a single step

```scala
val neo = Neold()

//Single statement
val query = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n"""
neo.executeImmediate1(query, Map("PROPERTY" -> "myProperty")){
    //On success
    result: String => println(result)
}

//Multiple statements
val countQuery = """MATCH n RETURN n"""
val insertQuery = """CREATE (n:Node {PROPERTIES}) RETURN n"""
val params = Map("PROPERTIES" -> """{ "prop1": "firstProperty", "prop2": "secondProperty" }""")
val statements = (insertQuery -> params) :: (countQuery -> params) :: List()
neo.executeImmediate(statements : _*){
    //On success
    result: String => println(result)
}
```

####Transactions

*Note: The examples below post one query at a time. It is possible to post multiple queries in a single call.*

```scala
val neo = Neold()
val countQuery = """MATCH (n:Node {prop: {PROPERTY}}) RETURN n"""
val insertQuery = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n"""
val parameters = Map("PROPERTY" -> "MyProperty")
```

##### Opening a transaction

```scala
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    //Transaction scope
    println(result)
}
```

##### Posting a statement to an open transaction

```scala
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    transaction.post1(insertQuery, parameters){ result : String =>
        //Print the json result
        println(result)
    }
}
```

##### Commit a transaction

```scala
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    transaction.post1(insertQuery, parameters){ result : String =>
        //When done, commit
        transaction.commit()
    }
}
```

##### Rollback a transaction

```scala
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    transaction.post1(insertQuery, parameters){ result : String =>
        //When done, rollback
        transaction.rollback()
    }
}
```

### Batch

The batch endpoint is designed for maximum performance.
Statements are buffered locally, before being sent to the endpoint for processing.

#### Adding statements to the buffer

```scala
val neo = Neold()
val countQuery = """MATCH (n:Node {prop: {PROPERTY}}) RETURN n"""
val insertQuery = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n"""
val parameters = Map("PROPERTY" -> "MyProperty")

neo.bufferQuery(countQuery, parameters)
neo.bufferQuery(insertQuery, parameters)
neo.bufferQuery(countQuery, parameters)
```

#### Executing the batch queue

```scala
neo.performBatch(){ result: String =>
    println(result)
}
```

### Force / disable parameter escaping

When you build a parameter Map, Neold automatically escapes certain characters to comply with the Json format, 
and adds double quotes around the parameter.
Furthermore, when a parameter begins with `{` or `[`, it is considered an object or an array, and the quotes are omitted.

You can completely disable (or force) the escaping and the quotes by using the following methods :

```scala
neo.executeImmediate(
    //Property forcefully escaped using the method :?, even if its first character is a {.
    "CREATE (n:Node {prop: {escaped}})" -> Map("escaped" -> :?("{{//\\escaped//\\}}")), 
    //Property not escaped using the method :!, and is passed as an integer instead of a string.
    "CREATE (n:Node {prop: {notescaped}})" -> Map("notescaped" -> :!("10")), 
    "MATCH (n:Node) RETURN n" -> Map(),
    "MATCH (n:Node) DELETE n" -> Map()
){
    result : String => println(result) 
    /////
    /// Json response :
    // {"results":[{"columns":[],"data":[]},{"columns":[],"data":[]},{"columns":["n"],"data":[{"row":[{"prop":"{{//\\escaped//\\}}"}]},{"row":[{"prop":10}]}]},{"columns":[],"data":[]}],"errors":[]}
    /// Properties :
    // "prop":"{{//\\escaped//\\}}"
    // "prop":10
    ///
}
```

### Result handling

Every Neo4j result is returned as a raw Json string by default to provide optimal performance.
You can either use the Json library of you choice to exploit the data, or use the included adapters and helper methods.

Adapters take a Json string as a parameter, and return an Option or an Either object.

#### Transactional Adapter

Below a small sample of code which should explain how Adapters work :

```scala
import org.neold.adapters.Adapters._
import org.neold.adapters.Adapters.TransactionalAdapter._

val neo = Neold()
val query = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n.prop as prop"""
neo.executeImmediate1(query, Map("PROPERTY" -> "myProperty")){ result : String =>
    //Maps the results to an Option value containing two nested arrays and a map
    //First array : result index
    //Second array : row index
    //Map : column name -> value
    //If there are errors, None
    val resultsOpt = toOption(result)

    //Helper method, by default maps the value at position (result 0 / row 0)
    //Equivalent to resultsOpt.map{ _(0)(0)("prop")}
    println(mapResultRow(resultsOpt){ _("prop")}.getOrElse(""))

    //Maps the results to a Left value containing the same two arrays / map as above
    //If there are errors, then a Right value is assigned, containing Errors as an Array[(code, message)]
    val resultsEither = toEither(result)
    resultsEither.fold(
        _.foreach{error => println(error)},
        results => println(results(0)(0)("prop"))
    )
}
```

#### Batch Adapter

Same as above :

```scala
import org.neold.adapters.Adapters._
import org.neold.adapters.Adapters.BatchAdapter._

val neo = Neold()
val query = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n.prop as prop"""

neo.bufferQuery(query, Map("PROPERTY" -> "myProperty"))
neo.performBatch(){ result : String =>
    val resultsOpt = toOption(result)
    println(mapResultRow(resultsOpt){ _("prop")}.getOrElse(""))

    val resultsEither = toEither(result)
    resultsEither.fold(
            _.foreach{error => println(error)},
            results => println(results(0)(0)("prop"))
        )
}
```

### Error handling

As all functions return Future objects, the methods `onSuccess` and `onFailure` can be used to check whether the call was successful or not.

```scala
//Incorrect Neo4j coordinates
val neo = Neold("badUrl!")
val query = "Not important"
neo.executeImmediate1(query).onFailure{
    case f : Exception => throw f
}
```

### Concurrency handling

To synchronize your calls, for instance inside a transaction you can use the Scala comprehensions.

If you are not familiar with these concepts the Dispatch homepage contains an excellent [guide](http://dispatch.databinder.net/Working+with+multiple+futures.html).

Below an example of a synchronized transaction :

```scala
val neo = Neold()
val countQuery = "MATCH (n: UserNode) RETURN count(n) as total"
val createQuery = "CREATE (n:UserNode {name: {name}}) RETURN n"
val deleteQuery = "MATCH (n:UserNode) DELETE n"
neo.initTransaction(countQuery){
    transaction => countBefore : String =>
        println(countBefore)
        //Synced
        for{
            create1 <- transaction.post1(createQuery, Map("name" -> "Toto"))
            countPlusOne <- transaction.post1(countQuery)
            create2 <- transaction.post1(createQuery, Map("name" -> "Titi"))
            countPlusTwo <- transaction.post1(countQuery)
            deletion <- transaction.post1("MATCH (n:UserNode) DELETE n")
            countZero <- transaction.post1(countQuery)
            rollBack <- transaction.rollback()
        } yield{
            println(create1)
            println(countPlusOne)
            println(create2)
            println(countPlusTwo)
            println(deletion)
            println(countZero)
            println(rollBack)
        }
}
```

##Dependencies

The following libraries are used :

- [Dispatch](https://github.com/dispatch/reboot) : asynchronous HTTP interaction library.
- [play-json](https://www.playframework.com/documentation/2.4.0-M2/ScalaJson) : Json parsing library.

##TODO

- Full REST API support
- Constraints
- Better concurrency
