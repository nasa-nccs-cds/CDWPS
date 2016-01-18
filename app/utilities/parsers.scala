package utilities.parsers
import scala.util.parsing.combinator._

class BadRequestException(message: String = null, cause: Throwable = null) extends RuntimeException(message, cause)

class ObjectNotationParser extends JavaTokenParsers {
  def normalize(sval: String): String = sval.stripPrefix("\"").stripSuffix("\"").toLowerCase
  def expr: Parser[Map[String, Seq[Map[String, Any]]]] = "[" ~> repsep(decl, ",") <~ "]" ^^ (Map() ++ _)
  def decl: Parser[(String, Seq[Map[String, Any]])] = key ~ "=" ~ objlist ^^ { case arg0 ~ "=" ~ arg1 => (normalize(arg0) -> arg1) }
  def key: Parser[String] = """[a-zA-Z_]\w*""".r
  def value: Parser[Any] = (
    stringLiteral
    | omap
    | floatingPointNumber ^^ (_.toFloat)
    | "true" ^^ (x => true)
    | "false" ^^ (x => false)
  )
  def member: Parser[(String, Any)] = stringLiteral ~ ":" ~ value ^^ { case x ~ ":" ~ y => (normalize(x), y) }
  def omap: Parser[Map[String, Any]] = "{" ~> repsep(member, ",") <~ "}" ^^ (Map() ++ _)
  def obj: Parser[Map[String, Any]] = omap | unparsed
  def unparsed: Parser[Map[String, Any]] = stringLiteral ^^ { case x: Any => Map[String, Any](("unparsed" -> x)) }
  def objlist: Parser[Seq[Map[String, Any]]] = "[" ~> repsep(obj, ",") <~ "]" | obj ^^ (List(_))
}

object wpsObjectParser extends ObjectNotationParser {

  def parseDataInputs(data_input: String): Map[String, Seq[Map[String, Any]]] = {
    try {
      parseAll(expr, data_input).get
    } catch {
      case e: Exception => throw new BadRequestException(e.getMessage, e)
    }
  }
}

object parseTest extends ObjectNotationParser {
  def main(input_args: Array[String]) = {
    val data_input = "[domain={\"id\":\"d0\",\"level\":{\"start\":0,\"end\":1,\"system\":\"indices\"}},variable={\"dset\":\"MERRA/mon/atmos\",\"id\":\"v0:hur\",\"domain\":\"d0\"},operation=\"(v0,axis:xy)\"]"
    println(parseAll(expr, data_input))
  }
}