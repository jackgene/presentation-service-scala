module Transcriber exposing (..)

import Css exposing (..)
import Html
import Html.Styled exposing (..)
import Html.Styled.Attributes exposing (..)
import Html.Styled.Events exposing (onInput)
import Http
import Regex
import Task
import Time


-- Types
type Msg
  = NewTranscription String
  | SendFullTranscription
  | ScheduleNextSend
  | TranscriptionReceived


type alias Model =
  { transcription : String
  , textLastSent : String
  , scheduleNextSend : Bool
  }


-- Constants
numWords : Int
numWords = 20


-- Init
init : (Model, Cmd Msg)
init =
  ( { transcription = ""
    , textLastSent = ""
    , scheduleNextSend = False
    }
  , Cmd.none
  )


-- Update
sendTranscription : String -> Cmd Msg
sendTranscription transcription =
  Http.send
  ( always TranscriptionReceived )
  ( Http.request
    { method = "POST"
    , headers = []
    , url =
      ( "/transcription?text=" ++ (Http.encodeUri transcription) )
    , body = Http.emptyBody
    , expect = Http.expectStringResponse (always (Ok ()))
    , timeout = Nothing
    , withCredentials = False
    }
  )


update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    NewTranscription transcription ->
      let
        adjustedTranscription : String
        adjustedTranscription =
          Regex.replace Regex.All
          ( Regex.regex "[a-z][A-Z]" )
          ( \{match} ->
            (String.left 1 match) ++ ". " ++ (String.right 1 match)
          )
          ( String.right (numWords * 10) transcription )

        -- Last word is possibly incomplete, hence separating it
        (completeWords, lastWord) =
          case List.reverse (String.indices " " adjustedTranscription) of
            [] -> ("", adjustedTranscription)

            lastSpaceIndex :: restSpaceIndices ->
              ( String.slice
                -- start index
                ( if List.length restSpaceIndices < numWords - 2 then 0
                  else
                    Maybe.withDefault 0
                    ( List.head
                      ( List.reverse
                        ( List.take (numWords - 1) restSpaceIndices )
                      )
                    )
                )
                lastSpaceIndex
                adjustedTranscription
              , String.dropLeft lastSpaceIndex adjustedTranscription
              )

        newTranscription : String
        newTranscription = completeWords ++ lastWord
      in
        if newTranscription == model.textLastSent then
          -- Text-to-speech sometimes result in duplicates
          ( model, Cmd.none )
        else
          ( { model
            | transcription = newTranscription
            , textLastSent = completeWords
            , scheduleNextSend = False
            }
          , Cmd.batch
            [ Task.perform
              ( always ScheduleNextSend )
              ( Task.succeed () )
            , ( if completeWords == model.textLastSent then Cmd.none
                else sendTranscription completeWords
              )
            ]
          )

    ScheduleNextSend ->
      ( { model | scheduleNextSend = model.transcription /= model.textLastSent }
      , Cmd.none
      )

    SendFullTranscription ->
      ( { model
        | textLastSent = model.transcription
        , scheduleNextSend = False
        }
      , sendTranscription model.transcription
      )

    TranscriptionReceived -> ( model, Cmd.none )


-- View
view : Model -> Html Msg
view model =
  div []
  [ textarea
    [ onInput NewTranscription, cols 120
    , css [ position absolute, top zero, right zero, bottom zero, left zero ]
    ]
    []
  , div [] [ text model.textLastSent ]
  ]


-- Subscriptions
subscriptions : Model -> Sub Msg
subscriptions model =
  if not model.scheduleNextSend then Sub.none
  else Time.every (Time.millisecond * 200) (always SendFullTranscription)


main : Program Never Model Msg
main =
  Html.program
  { init = init
  , update = update
  , subscriptions = subscriptions
  , view = toUnstyled << view
  }
