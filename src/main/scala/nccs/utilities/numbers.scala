package nccs.utilities.numbers

class IllegalNumberException( value: Any ) extends RuntimeException("Error, " + value.toString + " is not a valid Number")

object GenericNumber {
  val floatingPointNumber = """[-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?""".r
  val integerNumber = """[-+]?[0-9]*""".r
  def parseString( sx: String ): GenericNumber = {
    sx match {
      case  integerNumber(sx) =>        new IntNumber(sx.toInt)
      case  floatingPointNumber(sx) =>  new FloatNumber(sx.toFloat)
      case x =>                         throw new IllegalNumberException(x)
    }
  }
  def apply( anum: Any = None ): GenericNumber = {
    anum match {
      case ix: Int =>     new IntNumber(ix)
      case fx: Float =>   new FloatNumber(fx)
      case dx: Double =>  new DoubleNumber(dx)
      case sx: Short =>   new ShortNumber(sx)
      case None =>        new UndefinedNumber()
      case sx: String =>  parseString( sx )
      case x =>           throw new IllegalNumberException(x)
    }
  }
}

abstract class GenericNumber {
  type NumericType
  def value(): NumericType
  override def toString() = { value().toString }
}

class IntNumber( val numvalue: Int ) extends GenericNumber {
  type NumericType = Int
  override def value(): NumericType = { numvalue }
}

class FloatNumber( val numvalue: Float ) extends GenericNumber {
  type NumericType = Float
  override def value(): NumericType = { numvalue }
}

class DoubleNumber( val numvalue: Double ) extends GenericNumber {
  type NumericType = Double
  override def value(): NumericType = { numvalue }
}

class ShortNumber( val numvalue: Short ) extends GenericNumber {
  type NumericType = Short
  override def value(): NumericType = { numvalue }
}

class UndefinedNumber extends GenericNumber {
  type NumericType = Option[Any]
  override def value(): NumericType = { None }
}

object testNumbers extends App {
  val x = GenericNumber("40")
  println( x.toString )
}




