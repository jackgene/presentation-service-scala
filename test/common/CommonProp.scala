package common

import org.scalactic.anyvals.PosInt
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

abstract class CommonProp extends AnyPropSpec with ScalaCheckPropertyChecks {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(
    minSuccessful = PosInt.ensuringValid(Integer.getInteger("test.prop.minSuccessful", 1_000)),
    sizeRange = PosInt.ensuringValid(Integer.getInteger("test.prop.sizeRange", 1_000)),
    workers = PosInt.ensuringValid(math.max(3, Runtime.getRuntime.availableProcessors * 3 / 4))
  )
}
