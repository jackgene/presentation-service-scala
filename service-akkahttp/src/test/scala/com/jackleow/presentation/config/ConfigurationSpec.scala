package com.jackleow.presentation.config

import org.scalatest.wordspec.AnyWordSpec
import pureconfig.{ConfigReader, ConfigSource}

class ConfigurationSpec extends AnyWordSpec {
  "The configuration" must {
    "always load with just the built-in reference.conf" in {
      // Test
      val actualConfigResult: ConfigReader.Result[Configuration] =
        ConfigSource.default.load[Configuration]

      // Verify
      assert(actualConfigResult.isRight)
    }
  }
}
