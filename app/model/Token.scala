package model

import scala.util.matching.Regex

object Token {
  private val ValidWordPattern: Regex = """([A-Za-z]{3,}(?:-[A-Za-z]{3,})*)""".r
  private val StopWords: Set[String] = Set(
    "about",
    "above",
    "after",
    "again",
    "against",
    "all",
    "and",
    "any",
    "are",
    "because",
    "been",
    "before",
    "being",
    "below",
    "between",
    "both",
    "but",
    "can",
    "did",
    "does",
    "doing",
    "down",
    "during",
    "each",
    "few",
    "for",
    "from",
    "further",
    "had",
    "has",
    "have",
    "having",
    "her",
    "here",
    "hers",
    "herself",
    "him",
    "himself",
    "his",
    "how",
    "into",
    "its",
    "itself",
    "just",
    "me",
    "more",
    "most",
    "myself",
    "nor",
    "not",
    "now",
    "off",
    "once",
    "only",
    "other",
    "our",
    "ours",
    "ourselves",
    "out",
    "over",
    "own",
    "same",
    "she",
    "should",
    "some",
    "such",
    "than",
    "that",
    "the",
    "their",
    "theirs",
    "them",
    "themselves",
    "then",
    "there",
    "these",
    "they",
    "this",
    "those",
    "through",
    "too",
    "under",
    "until",
    "very",
    "was",
    "were",
    "what",
    "when",
    "where",
    "which",
    "while",
    "who",
    "whom",
    "why",
    "will",
    "with",
    "you",
    "your",
    "yours",
    "yourself",
    "yourselves",
  )

  def words(text: String): Seq[String] = text.trim.
    split("""[^\w\-]""").toSeq.
    map { _.toLowerCase }.
    collect {
      case ValidWordPattern(word: String) if !StopWords.contains(word) => word
    }

  private val TokensByString: Map[String,String] = Map(
    // C/C++
    "c" -> "C",
    "c++" -> "C",
    // C#
    "c#" -> "C#",
    "csharp" -> "C#",
    // Go
    "go" -> "Go",
    "golang" -> "Go",
    // Java
    "java" -> "Java",
    // Javascript
    "js" -> "JavaScript",
    "ecmascript" -> "JavaScript",
    "javascript" -> "JavaScript",
    // Kotlin
    "kotlin" -> "Kotlin",
    "kt" -> "Kotlin",
    // Lisp
    "lisp" -> "Lisp",
    "clojure" -> "Lisp",
    "racket" -> "Lisp",
    "scheme" -> "Lisp",
    // ML
    "ml" -> "ML",
    "haskell" -> "ML",
    "caml" -> "ML",
    "elm" -> "ML",
    "f#" -> "ML",
    "ocaml" -> "ML",
    "purescript" -> "ML",
    // Perl
    "perl" -> "Perl",
    // PHP
    "php" -> "PHP",
    // Python
    "py" -> "Python",
    "python" -> "Python",
    // Ruby
    "ruby" -> "Ruby",
    "rb" -> "Ruby",
    // Rust
    "rust" -> "Rust",
    // Scala
    "scala" -> "Scala",
    // Swift
    "swift" -> "Swift",
    // TypeScript
    "ts" -> "TypeScript",
    "typescript" -> "TypeScript",
  )

  def languageFromFirstWord(text: String): Seq[String] = text.trim.
    split("""[^\w#]""").headOption.
    map { _.toLowerCase }.
    flatMap(TokensByString.get).toSeq

  def languagesFromWords(text: String): Seq[String] = text.trim.
    split("""[^\w#]""").toSeq.
    map { _.toLowerCase }.
    flatMap(TokensByString.get)

  private val TokensByLetter: Map[Char,String] = Map(
    'e' -> "Python",
    't' -> "Swift",
    'a' -> "Kotlin",
    'o' -> "JavaScript",
    'i' -> "TypeScript",
    'n' -> "Go",
    's' -> "C",
    'h' -> "C#",
    'r' -> "Java",
    'd' -> "Lisp",
    'l' -> "ML",
    'c' -> "Perl",
    'u' -> "PHP",
    'm' -> "Ruby",
    'w' -> "Scala",
    'f' -> "Python",
    'g' -> "Swift",
    'y' -> "Kotlin",
    'p' -> "JavaScript",
    'b' -> "TypeScript",
    'v' -> "Go",
    'k' -> "C",
    'j' -> "C#",
    'x' -> "Java",
    'q' -> "Lisp",
    'z' -> "Rust",
  )

  def languageFromFirstLetter(text: String): Seq[String] = {
    val normalizedFirstLetterOpt: Option[Char] = text.trim.toLowerCase.headOption
    normalizedFirstLetterOpt.
      flatMap { TokensByLetter.get }.
      orElse(Some("Swift")).
      toSeq
  }
}
