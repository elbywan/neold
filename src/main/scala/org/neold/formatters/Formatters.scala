package org.neold.formatters

/**
 * Userful formatting tools
 */
object FormatterTools{

    /**
     * Escape Neo4J special characters.
     * @param str Un-escaped String
     * @return Escaped String.
     */
    def escapeSpecialChars(str: String) : String = {
        str.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    /**
     * Escape Neo4J & Json special characters.
     * @param parameters Un-escaped parameter list.
     * @return Escaped parameter list.
     */
    def escapeParameters(parameters: Map[String, Any]) : Map[String, Any] = {
        parameters.map { kv: (String, Any) =>
            val fullyTrimmed = kv._2.toString.replaceAll("\\A\\s+", "").replaceAll("\\s+\\z", "")
            val firstChar = fullyTrimmed.charAt(0)
            if(firstChar != '{' && firstChar != '[')
                (kv._1, "\""+escapeSpecialChars(kv._2.toString)+"\"")
            else
                (kv._1, fullyTrimmed)
        }
    }

}

import FormatterTools._

/**
 * Formatter for the Transactional API.
 */
object TransactionalFormatter {

    /**
      * Format a single statement.
      * @param query Cypher query
      * @param parameters Parameter list
      * @return Formatted statement in Json format.
      */
    def formatSingleStatement(query: String, parameters: Map[String, Any]) : String = {
        s"""
            |{
            |  "statements" : [ ${formatStatement(query, parameters)} ]
            |}
        """.stripMargin
    }

    /**
     * Format multiple statements.
     * @param statements Statement list, which are pairs of cypher queries + parameter list.
     * @return Formatted statements in Json format.
     */
    def formatMultipleStatements(statements : (String, Map[String, Any])*): String = {
        s"""
            |{
            |  "statements" : [${
            statements.foldLeft(""){ (acc, statement) =>
                if(acc.length == 0){
                    acc + formatStatement(statement._1, statement._2)
                } else {
                    acc + ", "+ formatStatement(statement._1, statement._2)
                }

            }
        }]
            |}
        """.stripMargin
    }

    //Internal statement formatting.
    private def formatStatement(query: String, parameters: Map[String, Any]) : String = {
        s"""
            |{
            |   "statement" : "$query",
            |   "parameters" : {
            |       ${
                        escapeParameters(parameters).foldLeft("") { (acc, prop) =>
                            if(acc.length == 0)
                                acc + s""""${prop._1}": ${prop._2}"""
                            else
                                acc + s""", "${prop._1}": ${prop._2}"""
                        }
                    }
            |   }
            |}
        """.stripMargin
    }

}

/**
 * Formatter for the Legacy Cypher API.
 */
object LegacyCypherFormatter {

    /**
     * Format a Cypher query.
     * @param query Cypher query
     * @param parameters Parameter list
     * @return Formatted cypher query in Json format.
     */
    def formatQuery(query: String, parameters: Map[String, Any]) : String = {
        s"""
            |{
            |   "query" : "$query",
            |   "params" : {
            |       ${
                        escapeParameters(parameters).foldLeft("") { (acc, prop) =>
                            if(acc.length == 0)
                                acc + s""""${prop._1}": ${prop._2}"""
                            else
                                acc + s""", "${prop._1}": ${prop._2}"""
                        }
                    }
            |   }
            |}
        """.stripMargin
    }

}
