package actors.tokenizing

object NoOpTokenizer extends Tokenizer {
  override def apply(text: String): Seq[String] = Seq()
}
