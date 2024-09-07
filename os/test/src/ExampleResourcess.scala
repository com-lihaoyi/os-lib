package test.os

object ExampleResourcess {

  object RemoteReadme {
    val url =
      "https://raw.githubusercontent.com/lihaoyi/os-lib/d6695db4e484afac2c0adf67016cd5d3df6b92ae/readme.md"
    val shortUrl = "https://git.io/fpfTs"
    // curl -L https://git.io/fpfTs | gzip -n | shasum -a 256
    val gzip6ShaSum256 = "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e"
    val size = 53814
  }
}
