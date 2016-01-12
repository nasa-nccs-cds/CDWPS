package utilities

import scala.util.parsing.combinator._

class OperationNotationParser extends JavaTokenParsers {
  def expr: Parser[Any] = "\"" ~> flist <~ "\"" | flist
  def arglist: Parser[List[String]] = "(" ~> repsep(value, ",") <~ ")"
  def value: Parser[String] = """[a-zA-Z0-9_:.*]*""".r
  def name: Parser[String] = """[a-zA-Z0-9_.]*""".r
  def fname: Parser[(String, String)] = (
    name ~ ":" ~ name ^^ { case x ~ ":" ~ y => (y, x) }
    | name ^^ (y => (y, ""))
  )
  def function: Parser[((String, String), List[String])] = (
    fname ~ arglist ^^ { case x ~ y => (x, y) }
    | arglist ^^ { y => (("", ""), y) }
  )
  def flist: Parser[Any] = repsep(function, ",")
}

object wpsOperationParser extends OperationNotationParser {
  def parseOp(operation: String) = parseAll(expr, operation).get
}

class ObjectNotationParser extends JavaTokenParsers {
  def expr: Parser[Map[String, Any]] = "[" ~> repsep(decl, ",") <~ "]" ^^ (Map() ++ _)
  def decl: Parser[(String, Any)] = key ~ "=" ~ value ^^ {
    case arg0 ~ "=" ~ arg1 =>
      arg0.toLowerCase match {
        case "operation" => (arg0, wpsOperationParser.parseOp(arg1.toString))
        case _ => (arg0, arg1)
      }
  }
  def key: Parser[String] = """[a-zA-Z_]\w*""".r
  def value: Parser[Any] = (
    stringLiteral
    | obj
    | floatingPointNumber ^^ (_.toFloat)
    | "true" ^^ (x => true)
    | "false" ^^ (x => false)
  )
  def member: Parser[(String, Any)] = stringLiteral ~ ":" ~ value ^^ { case x ~ ":" ~ y => (x, y) }
  def obj: Parser[Map[String, Any]] = "{" ~> repsep(member, ",") <~ "}" ^^ (Map() ++ _)
}

object wpsObjectParser extends ObjectNotationParser {
  def parseDataInputs(data_input: String): Map[String, Any] = collection.immutable.Map(parseAll(expr, data_input).get.toList: _*)
}

object parseTest extends ObjectNotationParser {
  def main(input_args: Array[String]) = {
    val data_input = "[domain={\"id\":\"d0\",\"level\":{\"start\":0,\"end\":1,\"system\":\"indices\"}},variable={\"dset\":\"MERRA/mon/atmos\",\"id\":\"v0:hur\",\"domain\":\"d0\"},operation=\"(v0,axis:xy)\"]"
    println(parseAll(expr, data_input))
  }
}