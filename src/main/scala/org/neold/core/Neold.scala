package org.neold.core

import com.ning.http.client.Response
import dispatch._
import org.neold.formatters.FormatterTools.EscapedString
import org.neold.formatters.{LegacyCypherFormatter, TransactionalFormatter}

import scala.collection.mutable.ArrayBuffer

object Neold {

    //Utility types
    type Statement = (String, Map[String, Any])
    type Done = String => _

    //Force string escaping
    def :?(str: String) : EscapedString = {
        new EscapedString(str)
    }

    //Implicit dispatch thread pool
    implicit val executor = dispatch.Defaults.executor

    //Void action
    private implicit val doneNull : Done = { x: String => () }

    /**
     * Convenience method, creates a new configured instance of the Neold class.
     * @param host Neo4j hostname. (Default: localhost)
     * @param port Neo4j port. (Default: 7474)
     * @param endpoint Location of the REST endpoint. (Default: "db/data")
     * @param username Username
     * @param password Password
     * @param secure Https secure flag
     * @return
     */
    def apply(host: String = "localhost", port: Int = 7474, endpoint: String = "db/data", username : String = "",
              password : String = "", secure : Boolean = false) = {
        new Neold(host,
            port,
            endpoint = {
                if (endpoint charAt 0 equals '/')
                    endpoint substring 1
                else
                    endpoint
            } split "/", username, password, secure)
    }

    /**
     * Shutdown the thread pool.
     */
    def shutdown() = {
        Http.shutdown()
    }

    private def sendRequest(request : Req, done: Done) : Future[String] = {
        val response = Http(request OK as.String)

        response.onSuccess{
            case content => {
                done(content)
            }
        }

        response
    }

}

/**
 * Interacts with the Neo4j database asynchronously using REST API calls.
 *
 * @define DONE Action to perform on the result on completion
 * @define FUTURE A Future object holding the result returned by the database as a
 * @define NEO4JDOC Related Neo4J doc :
 *
 * @param host Neo4j hostname. (Default: localhost)
 * @param port Neo4j port. (Default: 7474)
 * @param endpoint Location of the REST endpoint. (Default: ["db", "data"])
 * @param username Username
 * @param password Password
 * @param secure Https secure flag
 */
class Neold private(host: String = "localhost", port: Int = 7474, endpoint: Seq[String] = Seq("db", "data"),
                    username : String = "", password: String = "", secure : Boolean = false){

    import org.neold.core.Neold._

    private val neoSvc = {
        var svc = endpoint.foldLeft(dispatch.host(host, port)){
            (svc, str) => svc / str
        }.addHeader("X-Stream", "true")

        if(!username.isEmpty)
            svc = svc.addHeader("Authorization", "Basic "+ new sun.misc.BASE64Encoder().encode((username + ":" + password).getBytes))

        if(secure)
            svc.secure
        else
            svc

    }

    /* ---------------------- */
    /*         Indexes        */

    /**
     * Creates an index.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/query-schema-index.html#schema-index-create-index-on-a-label Create an index on a label]]
     *
     * @param label Node label
     * @param property Node property
     * @param done $DONE
     * @return $FUTURE String
     */
    def createIndex(label: String, property: String)(implicit done: Done = doneNull) : Future[String] = {
        val request = (neoSvc / "schema" / "index" / label)
            .POST
            .setContentType("application/json", "UTF-8") <<
            s"""
                  | { "property_keys" : [ "$property" ] }
                """.stripMargin

        sendRequest(request, done)
    }

    /**
     * Drops an index.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/query-schema-index.html#schema-index-drop-index-on-a-label Drop an index on a label]]
     *
     * @param label Node label
     * @param property Node property
     * @return $FUTURE String
     */
    def dropIndex(label: String, property: String) : Future[String] = {
        Http((neoSvc / "schema" / "index" / label / property).DELETE.setContentType("application/json", "UTF-8") OK as.String)
    }

    /**
     * Returns a list of indexes.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-schema-indexes.html#rest-api-list-indexes-for-a-label List indexes for a label]]
     *
     * @param label Node label
     * @param done Node property
     * @return $FUTURE String
     */
    def listIndexes(label: String)(implicit done: Done = doneNull) : Future[String] = {
        val request = (neoSvc / "schema" / "index" / label)
            .GET
            .setContentType("application/json","UTF-8")

        sendRequest(request, done)
    }

    /* ---------------------- */
    /* Transactional endpoint */

    /**
     * Executes a single cypher query and commit it using the transactional endpoint.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-begin-and-commit-a-transaction-in-one-request Begin and commit a transaction in one request]]
     *
     * @param query Cypher query.
     * @param parameters Cypher parameters.
     * @param done $DONE
     * @return $FUTURE String
     */
    def executeImmediate1(query: String, parameters: Map[String, Any] = Map())(implicit done: Done = doneNull) : Future[String] = {
        executeImmediate((query, parameters))(done)
    }

    /**
     * Executes multiple cypher statements and commits them using the transactional endpoint.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-begin-and-commit-a-transaction-in-one-request Begin and commit a transaction in one request]]
     *
     * @param statements Sequence of cypher statements.
     * @param done $DONE
     * @return $FUTURE String
     */
    def executeImmediate(statements : Statement*)(implicit done: Done = doneNull) : Future[String] = {
        val request = (neoSvc / "transaction" / "commit")
            .POST
            .setContentType("application/json", "UTF-8") << TransactionalFormatter.formatMultipleStatements(statements :_*)

        sendRequest(request, done)
    }

    /**
     * Initializes a transaction by posting a single cypher statement.
     *
     * A transaction is symbolized by the [org.neold.Neold.Transaction] Object which allows posting more statements to the transaction, commit or rollback it.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-begin-a-transaction Begin a transaction]]
     *
     * {{{
     *      import org.neold.core.Neold, org.neold.core.Neold._
     *
     *      val neo = Neold()
     *      neo.initTransaction("MERGE (u:User {name: {name}}) RETURN u", Map("name" -> "Toto")){
     *          transaction : neo.Transaction => res : String =>
     *              //Transaction 'Scope'
     *              println(res) //res is the result of the first statement posted
     *              //This for construct synchronizes the async calls
     *              for{
     *                  res1 <- transaction.post1("MERGE (u:User {name: {name}}) RETURN u", Map("name" -> "Titi"))
     *                  res2 <- transaction.post1("MATCH n RETURN n")
     *                  res3 <- transaction.post1("MATCH n DELETE n")
     *                  res4 <- transaction.post1("MATCH n RETURN count(n) as total")
     *                  res5 <- transaction.rollback()
     *              } yield{
     *                  println(res1)
     *                  println(res2)
     *                  println(res3)
     *                  println(res4)
     *                  println(res5)
     *                  Neold shutdown
     *             }
     *    }
     * }}}
     *
     * @param query Cypher initial query
     * @param parameters Cypher parameters
     * @param scope Transaction scope function
     * @return $FUTURE (Transaction, String) tuple
     */
    def initTransaction(query: String, parameters: Map[String, Any] = Map())(scope: (Transaction => String => _)) : Future[(Transaction, String)] = {
        initTransaction((query, parameters))(scope)
    }
    /**
     * Initializes a transaction by posting initial cypher statements.
     *
     * A transaction is symbolized by the [org.neold.Neold.Transaction] Object which allows posting more statements to the transaction, commit or rollback it.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-begin-a-transaction Begin a transaction]]
     *
     * {{{
     *      import org.neold.core.Neold, org.neold.core.Neold._
     *
     *      val neo = Neold()
     *      neo.initTransaction("MERGE (u:User {name: {name}}) RETURN u", Map("name" -> "Toto")){
     *          transaction : neo.Transaction => res : String =>
     *              //Transaction 'Scope'
     *              println(res) //res is the result of the first statement posted
     *              //This for construct synchronizes the async calls
     *              for{
     *                  res1 <- transaction.post1("MERGE (u:User {name: {name}}) RETURN u", Map("name" -> "Titi"))
     *                  res2 <- transaction.post1("MATCH n RETURN n")
     *                  res3 <- transaction.post1("MATCH n DELETE n")
     *                  res4 <- transaction.post1("MATCH n RETURN count(n) as total")
     *                  res5 <- transaction.rollback()
     *              } yield{
     *                  println(res1)
     *                  println(res2)
     *                  println(res3)
     *                  println(res4)
     *                  println(res5)
     *                  Neold shutdown
     *             }
     *    }
     * }}}
     *
     * @param queries Initial cypher statements
     * @param scope Transaction scope function
     * @return $FUTURE (Transaction, String) tuple
     */
    def initTransaction(queries : Statement*)(scope: (Transaction => String => _)) : Future[(Transaction, String)] = {
        val request = (neoSvc / "transaction")
            .POST
            .setContentType("application/json", "UTF-8") << TransactionalFormatter.formatMultipleStatements(queries :_*)

        val response = Http(request OK {r: Response =>
            (r.getHeader("Location"), r.getResponseBody())
        })

        response.onSuccess{
            case (location, content) => {
                scope(Transaction(location))(content)
            }
        }

        response.map({ locAndContent => (Transaction(locAndContent._1), locAndContent._2) })
    }

    /**
     * Holds the location of the transaction and encapsulates rollback/post/commit methods.
     *
     * @param location URL of the transaction
     */
    case class Transaction(location: String){

        /**
         * Execute a single statement in the transaction.
         *
         * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-execute-statements-in-an-open-transaction Execute statements in an open transaction]]
         *
         * @param query Cypher query
         * @param parameters Cypher parameters
         * @param done $DONE
         * @return $FUTURE String
         */
        def post1(query : String, parameters: Map[String, Any] = Map())(implicit done: Done = doneNull): Future[String] = {
            post((query, parameters))(done)
        }

        /**
         * Execute several statements in the transaction.
         *
         * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-execute-statements-in-an-open-transaction Execute statements in an open transaction]]
         *
         * @param statements Cypher statements
         * @param done $DONE
         * @return $FUTURE String
         */
        def post(statements : Statement*)(done: Done) : Future[String] = {
            val request = url(location)
                .POST
                .setContentType("application/json", "UTF-8") << TransactionalFormatter.formatMultipleStatements(statements :_*)

            sendRequest(request, done)
        }

        /**
         * Commits the transaction.
         *
         * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-commit-an-open-transaction Commit an open transaction]]
         *
         * @param done $DONE
         * @return $FUTURE String
         */
        def commit()(implicit done: Done = doneNull) : Future[String] = {
            val request = url(location + "/commit")
                .POST
                .setContentType("application/json", "UTF-8") << TransactionalFormatter.formatMultipleStatements(Seq(): _*)

            sendRequest(request, done)
        }

        /**
         * Rollback the transaction.
         *
         * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-transactional.html#rest-api-rollback-an-open-transaction Rollback an open transaction]]
         *
         * @param done $DONE
         * @return $FUTURE String
         */
        def rollback()(implicit done: Done = doneNull) : Future[String] = {
            val request = url(location)
                .DELETE
                .setContentType("application/json", "UTF-8")

            sendRequest(request, done)
        }
    }

    /*                        */
    /* ---------------------- */


    /* ---------------------- */
    /*     Batch endpoint     */

    //Statements buffer
    private val batchBuffer : ArrayBuffer[String] = ArrayBuffer()

    /**
     * Add a statement to the batch buffer.
     *
     * @param query Cypher query.
     * @param parameters Cypher parameters
     * @return Index of the statement in the buffer (which is buffer size minus 1)
     */
    def bufferQuery(query : String, parameters: Map[String, Any] = Map()) : Int = {
        batchBuffer.append(
            s"""
              |{
              | "method" : "POST",
              | "to" : "/cypher",
              | "id" : ${batchBuffer.length},
              | "body": ${LegacyCypherFormatter.formatQuery(query, parameters)}
              |}
            """.stripMargin)
        batchBuffer.length - 1
    }

    /**
     * Sends the buffer to the neo4j server in order to perform the statements.
     *
     * $NEO4JDOC [[http://neo4j.com/docs/stable/rest-api-batch-ops.html#rest-api-execute-multiple-operations-in-batch Execute multiple operations in batch]]
     *
     * @param done $DONE
     * @return $FUTURE String
     */
    def performBatch()(implicit done: Done = doneNull) : Future[String] = {
        val request = (neoSvc / "batch")
            .POST
            .setContentType("application/json", "UTF-8") <<
            s"""
                  | [
                  | ${ batchBuffer.foldLeft("") { (accu, item) =>
                if(accu.length == 0){
                    item
                } else {
                    accu + ", " + item
                }
            }}
                  | ]
                """.stripMargin

        batchBuffer.clear()

        sendRequest(request, done)
    }

    /*                        */
    /* ---------------------- */

}
