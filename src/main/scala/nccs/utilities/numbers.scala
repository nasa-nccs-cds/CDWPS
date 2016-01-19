package nccs.utilities.numbers

object GenericNumber {
  def apply( anum: Any = None ): GenericNumber = {
    anum match {
      case ix: Int =>     new IntNumber(ix)
      case fx: Float =>   new FloatNumber(fx)
      case dx: Double =>  new DoubleNumber(dx)
      case sx: Short =>   new ShortNumber(sx)
      case _ =>           new UndefinedNumber()
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




