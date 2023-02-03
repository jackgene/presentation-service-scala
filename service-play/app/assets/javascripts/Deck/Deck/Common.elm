module Deck.Common exposing (..)

import Array exposing (Array)
import Dict exposing (Dict)
import Html.Styled exposing (Html)
import Navigation exposing (Location)
import Time exposing (Time)


-- Constants
typingSpeedMultiplier : Int
typingSpeedMultiplier = 3


-- Messages
type Msg
  = Next
  | Last
  | NewLocation Location
  | Event String
  | TranscriptionText String
  | TranscriptionUpdated Time
  | TranscriptionClearingTick Time
  | AnimationTick
  | NoOp


-- Model
type alias SlideModel =
  { active : Model -> Bool
  , update : Msg -> Model -> (Model, Cmd Msg)
  , view : Model -> Html Msg
  , index : Int
  , eventsWsPath : Maybe String
  , animationFrames : Model -> Int
  }


type Slide = Slide SlideModel


type alias Navigation =
  { nextSlideIndex : Int
  , lastSlideIndex : Int
  }


type alias ChatMessage =
  { sender : String
  , recipient : String
  , text : String
  }


type alias ChatMessageAndTokens =
  { chatMessage : ChatMessage
  , tokens : List String
  }


type alias TokenCounts =
  { tokensAndCounts : List (String, Int)
  , tokensBySender : Dict String (List String)
  , chatMessagesAndTokens : List ChatMessageAndTokens
  }


type alias Model =
  { eventsWsUrl : Maybe String
  , activeNavigation : Array Navigation
  , currentSlide : Slide
  , animationFramesRemaining : Int
  , wordCloud : TokenCounts
  , questions : Array String
  , transcription : { text : String, updated : Time }
  }
