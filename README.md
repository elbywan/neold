| Neold | [![Build Status](https://travis-ci.org/elbywan/neold.svg?branch=dev)](https://travis-ci.org/elbywan/neold) | [![Coverage Status](https://coveralls.io/repos/elbywan/neold/badge.svg?branch=dev)](https://coveralls.io/r/elbywan/neold?branch=dev)
=====

**Neold** is an high performance, programmer-friendly asynchronous Neo4j REST client driver written in scala.

------

- Main branch : releases.
- Dev branch : snapshots.

------

##Installation

Add the following lines to your sbt build file :

```
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies += "com.github.elbywan" %% "neold" % "0.1-SNAPSHOT"
```

To play around with the library, type `sbt console`.

##Usage

Neold can interact with the [Transactional endpoint](http://neo4j.com/docs/stable/rest-api.html) or the [Batch endpoint](http://neo4j.com/docs/stable/rest-api-batch-ops.html).

*All the sample code below require the following import line :*
`import org.neold.core.Neold, org.neold.core.Neold._`

*If you want to copy/paste these code snippets somewhere, don't forget that they are <b>asynchronous</b>. You <b>have</b> to keep the main thread from exiting to see the results.*

*To use the library <b>synchronously</b> :*
```
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import java.util.concurrent.TimeUnit

val future = neo.executeImmediate(query, params)
val result = Await.result(future, Duration.create(5, TimeUnit.SECONDS))
```

#### Neold setup

#####Server location

```
//Setups Neold with the default endpoint : http://localhost:7474/db/data
Neold("localhost", 7474, "db/data")
Neold("localhost", 7474)
Neold()
//Those 3 lines are equivalent
```

#####Authentication

Provide your [authorization token](http://neo4j.com/docs/snapshot/rest-api-security.html#rest-api-security-getting-started)  in the `token` parameter.
The `secure` flag (default to false) controls whether the request is sent over https.

```
Neold(token = "YOUR_ACCESS_TOKEN", secure = true)
```

#### On shutdown
Call `Neold.shutdown()` to shut down the underlying thread pool.

### Transactional endpoint

The transactional endpoint is the default way to interact with Neo4j.

####Executing statements and commiting in a single step

```
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

```
val neo = Neold()
val countQuery = """MATCH (n:Node {prop: {PROPERTY}}) RETURN n"""
val insertQuery = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n"""
val parameters = Map("PROPERTY" -> "MyProperty")
```

##### Opening a transaction

```
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    //Transaction scope
    println(result)
}
```

##### Posting a statement to an open transaction

```
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    transaction.post1(insertQuery, parameters){ result : String =>
        //Print the json result
        println(result)
    }
}
```

##### Commit a transaction

```
neo.initTransaction(countQuery, parameters){ transaction => result : String =>
    transaction.post1(insertQuery, parameters){ result : String =>
        //When done, commit
        transaction.commit()
    }
}
```

##### Rollback a transaction

```
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

```
val neo = Neold()
val countQuery = """MATCH (n:Node {prop: {PROPERTY}}) RETURN n"""
val insertQuery = """CREATE (n:Node {prop: {PROPERTY}}) RETURN n"""
val parameters = Map("PROPERTY" -> "MyProperty")

neo.bufferQuery(countQuery, parameters)
neo.bufferQuery(insertQuery, parameters)
neo.bufferQuery(countQuery, parameters)
```

#### Executing the batch queue

```
neo.performBatch(){ result: String =>
    println(result)
}
```

### Error handling

As all functions return Future objects, the methods `onSuccess` and `onFailure` can be used to check whether the call was successful or not.

```
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

```
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
- [play-json](https://www.playframework.com/documentation/2.4.0-M2/ScalaJson) : Json handling library

##TODO

- Full REST API support
- API response encapsulation
