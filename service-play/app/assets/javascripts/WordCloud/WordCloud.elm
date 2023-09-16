module WordCloud exposing (WordCounts, empty, wordCounts, topWords)

import Dict exposing (Dict)
import Json.Decode as Decode exposing (Decoder)
import WebSocket


webSocketUrl : String
webSocketUrl = "ws://localhost:9673?debug=true"


type alias ChatMessage =
  { sender : String
  , recipient : String
  , text : String
  }


type alias ExtractedWord =
  { word : String
  , isValid : Bool
  }


type alias ChatMessageAndWords =
  { chatMessage : ChatMessage
  , words : List ExtractedWord
  }


type alias WordCounts =
  { chatMessagesAndWords : List ChatMessageAndWords
  , wordsBySender : Dict String (List String)
  , countsByWord : Dict String Int
  }


chatMessageDecoder : Decoder ChatMessage
chatMessageDecoder =
  Decode.map3 ChatMessage
  ( Decode.field "s" Decode.string )
  ( Decode.field "r" Decode.string )
  ( Decode.field "t" Decode.string )


extractedWordDecoder : Decoder ExtractedWord
extractedWordDecoder =
  Decode.map2 ExtractedWord
  ( Decode.field "word" Decode.string )
  ( Decode.field "isValid" Decode.bool )


chatMessageAndTokensDecoder : Decoder ChatMessageAndWords
chatMessageAndTokensDecoder =
  Decode.map2 ChatMessageAndWords
  ( Decode.field "chatMessage" chatMessageDecoder )
  ( Decode.field "words" ( Decode.list extractedWordDecoder ) )


wordCountsDecoder : Decoder WordCounts
wordCountsDecoder =
  Decode.map3 WordCounts
  ( Decode.field "chatMessagesAndWords" ( Decode.list chatMessageAndTokensDecoder ) )
  ( Decode.field "wordsBySender" ( Decode.dict ( Decode.list Decode.string ) ) )
  ( Decode.field "countsByWord" ( Decode.dict Decode.int ) )


empty : WordCounts
empty =
  { chatMessagesAndWords = []
  , wordsBySender = Dict.empty
  , countsByWord = Dict.empty
  }


wordCounts : (Maybe WordCounts -> msg) -> Sub msg
wordCounts tagger =
  Sub.map
  ( tagger << Result.toMaybe )
  ( WebSocket.listen webSocketUrl ( Decode.decodeString wordCountsDecoder ) )


topWords : Int -> WordCounts -> List (String, Int)
topWords n wordCounts =
  Dict.toList wordCounts.countsByWord
  |> List.sortBy ( \(_, count) -> -count )
  |> List.take n
