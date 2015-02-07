package org.neold.adapters

import play.api.libs.json._

/**
 * Adapters superclass.
 */
abstract class Adapter {
    import Adapters._

    /**
     * Parses a json string from Neo4j and returns an Option containing the results, or None if there were errors.
     */
    def toOption(str: String) : Option[Results]

    /**
     * Parses a json string from Neo4j and returns either a Left object containing the results, or a Right object containing the errors.
     */
    def toEither(str: String) : Either[Errors, Results]
}

/**
 * Adapters from json string representation (raw Neo4j reply) to scala collections.
 */
object Adapters{

    type ResultRow = Map[String, String]
    type Results = Array[Array[ResultRow]]
    type Error = (String, String)
    type Errors = List[Error]

    /**
     * Retrieves a row at a given position in the result set and applies an action function.
     *
     * @param resultsOpt ResultSet as an Option object.
     * @param rowIndex Result position (default 0)
     * @param resultIndex Result number (default 0)
     * @tparam T Return type of the action
     * @return A mapped result of type Option[T].
     */
    def mapResultRow[T](resultsOpt: Option[Results], rowIndex: Int = 0, resultIndex: Int = 0) = {
        action : (ResultRow => T) => resultsOpt.map{
            results => action(results(resultIndex)(rowIndex))
        }
    }

    /**
     * Adapter for the Trasactional Neo4j endpoint.
     */
    object TransactionalAdapter extends Adapter{

        private def getErrors(json: JsValue) = {
            val errorsOpt = (json \ "errors").asOpt[List[JsObject]]
            for{ errors <- errorsOpt } yield {
                errors.foldLeft(List[(String, String)]()){
                    (listAcc : List[(String, String)], error: JsObject) =>
                        ((error \ "code").toString(), (error \ "error").toString()) :: listAcc
                }
            }
        }

        private def getResults(json: JsValue) = {
            val resultsOpt = (json \ "results").asOpt[List[JsObject]]

            for{ results <- resultsOpt } yield { results.flatMap{
                result =>
                    val columnsOpt = (result \ "columns").asOpt[List[String]]
                    val rowsOpt = Some{(result \ "data" \\ "row").map{
                        row => row.as[JsArray].value
                    }}

                    for{
                        columns <- columnsOpt
                        rows <- rowsOpt
                    } yield {
                        rows.map{ row: Seq[JsValue] =>
                            {
                                for{
                                    c <- columns
                                    item <- row
                                } yield (c -> item.toString())
                            }.toMap
                        }
                    }.toArray
            }}.toArray
        }

        override def toOption(str: String) : Option[Results] = {
            val json = Json.parse(str)
            val errors = getErrors(json)
            if(errors.map(m => m.size).getOrElse(0) > 0)
                None
            else
                getResults(json)
        }
        override def toEither(str: String) : Either[Errors, Results] = {
            val json = Json.parse(str)
            val errors = getErrors(json)
            if(errors.map(m => m.size).getOrElse(0) > 0)
                Left(errors.get)
            else
                Right(getResults(json).getOrElse(Array()))
        }
    }

    /**
     * Adapter for the Batch Neo4j endpoint using the Legacy cypher endpoint format.
     */
    object BatchAdapter extends Adapter{

        private def getResults(json: JsValue) = {
            val resultsOpt = json.asOpt[List[JsObject]]

            for{ results <- resultsOpt } yield { results.flatMap{
                result =>
                    val columnsOpt = (result \ "body" \ "columns").asOpt[List[String]]
                    val rowsOpt = (result \ "body" \ "data").asOpt[List[JsArray]].map{
                        rows => rows.flatMap{ row => row.asOpt[List[JsValue]] }
                    }

                    for{
                        columns <- columnsOpt
                        rows <- rowsOpt
                    } yield {
                        rows.map{ row: List[JsValue] =>
                            {
                                for{
                                    c <- columns
                                    item <- row
                                } yield (c -> item.toString())
                            }.toMap
                        }
                    }.toArray
            }}.toArray
        }


        override def toOption(str: String): Option[Results] = {
            val json = Json.parse(str)
            getResults(json)
        }

        override def toEither(str: String): Either[Errors, Results] = {
            val json = Json.parse(str)
            getResults(json) match {
                case Some(results) => Right(results)
                case None => Left(List[Error]("Neold.serialization.failed" -> "Json serialization failed."))
            }
        }
    }


}
