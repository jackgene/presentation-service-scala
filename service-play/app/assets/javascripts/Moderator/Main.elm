module Moderator exposing (..)

import Css exposing
  -- Container
  ( display, left, width, top
  -- Content
  , color, textAlign, verticalAlign
  -- Sizes
  , em, pct
  -- Positions
  -- Other values
  , block, none, rgb
  )
import Http
import Html.Styled as Html exposing
  ( Attribute, Html
  , button, datalist, div, input, li, option, table, td, text, th, tr, ul
  )
import Html.Styled.Attributes exposing
  ( css, id, list, size, type_, value )
import Html.Styled.Events exposing (..)
import Json.Decode as Decode
import Navigation exposing (Location)
import Time exposing (Time, second)
import WebSocket


-- Constants
-- Sender name of the moderator
-- Empty string is selected because the Zoom client doesn't allow it
-- preventing attendees from impersonating the moderator
moderatorName : String
moderatorName = ""


languages : List String
languages =
  [ "C", "C++"
  , "C#"
  , "Elm"
  , "Go"
  , "Java"
  , "JavaScript"
  , "Kotlin"
  , "Lisp"
  , "ML"
  , "Perl"
  , "PHP"
  , "Python"
  , "Ruby"
  , "Rust"
  , "Scala"
  , "Swift"
  , "TypeScript"
  ]


-- Types
type alias ChatMessage =
  { sender : String
  , recipient : String
  , text : String
  }


type Msg
  = NewMessageText String
  | NewOverrideMessageText Int String
  | SendMessageRequest
  | PostChatResponse (Result Http.Error ())
  | RemoveMessage Int (Maybe ChatMessage)
  | Event String
  | KeepAlive Time
  | NoOp


type alias Model =
  { eventsWsUrl : String
  , messageText : String
  , chatMessages : List ChatMessage
  , errors : List String
  }


-- Init
webSocketBaseUrl : Location -> String
webSocketBaseUrl location =
  "ws" ++ (String.dropLeft 4 location.protocol) ++ "//" ++ location.host


init : Location -> (Model, Cmd Msg)
init location =
  ( { eventsWsUrl = webSocketBaseUrl location ++ "/moderator/event"
    , messageText = ""
    , chatMessages = []
    , errors = []
    }
  , Cmd.none
  )


-- Update
chatMessageDecoder : Decode.Decoder ChatMessage
chatMessageDecoder =
  Decode.map3 ChatMessage
  ( Decode.field "s" Decode.string )
  ( Decode.field "r" Decode.string )
  ( Decode.field "t" Decode.string )


postChat : ChatMessage -> Cmd Msg
postChat chatMessage =
    Http.send
    PostChatResponse
    ( Http.request
      { method = "POST"
      , headers = []
      , url =
        ( "/chat?route="
        ++(Http.encodeUri (chatMessage.sender ++ " to " ++ chatMessage.recipient))
        ++"&text=" ++ (Http.encodeUri chatMessage.text)
        )
      , body = Http.emptyBody
      , expect = Http.expectStringResponse (always (Ok ()))
      , timeout = Nothing
      , withCredentials = False
      }
    )


update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    NewMessageText text ->
      ( { model | messageText = text}
      , Cmd.none
      )

    NewOverrideMessageText index text ->
      ( { model
        | chatMessages =
          List.indexedMap
          ( \idx chatMsg ->
            if idx /= index then chatMsg
            else { chatMsg | text = text }
          )
          model.chatMessages
        }
      , Cmd.none
      )

    SendMessageRequest ->
      ( model
      , postChat (ChatMessage moderatorName "Everyone" model.messageText)
      )

    RemoveMessage index maybeChatMsg ->
      ( { model
        | chatMessages =
          (List.take index model.chatMessages) ++ (List.drop (index+1) model.chatMessages)
        }
      , case maybeChatMsg of
          Just chatMsg -> postChat chatMsg
          Nothing -> Cmd.none
      )

    PostChatResponse (Ok _) ->
      ( { model | messageText = "" }
      , Cmd.none
      )

    PostChatResponse (Err httpConnError) ->
      ( let
          error : String
          error =
            case httpConnError of
              Http.BadUrl url ->
                "The URL " ++ url ++ " was invalid"
              Http.Timeout ->
                "Timed out attempting to reach the server, try again"
              Http.NetworkError ->
                "Unable to reach the server, check your network connection"
              Http.BadStatus resp ->
                "Bad HTTP status " ++
                toString resp.status.code ++ " (" ++ resp.status.message ++
                ") for: " ++ resp.url
              Http.BadPayload errorMessage _ ->
                errorMessage
        in
        { model | errors = error :: model.errors }
      , Cmd.none
      )

    Event json ->
      ( case Decode.decodeString chatMessageDecoder json of
        Ok chatMessage ->
          { model | chatMessages = chatMessage :: model.chatMessages }

        Err error ->
          { model | errors = error :: model.errors }
      , Cmd.none
      )

    KeepAlive _ ->
      ( model, WebSocket.send model.eventsWsUrl "40" )

    NoOp -> ( model, Cmd.none )


-- View
onKeyDown : (Int -> msg) -> Attribute msg
onKeyDown tagger =
  on "keydown" (Decode.map tagger keyCode)


view : Model -> Html Msg
view model =
  div []
  [ datalist [ id "languages" ]
    ( List.map ( \lang -> option [ value lang ] [] ) languages )
  , div
    [ css
      [ if model.errors == [] then display none else display block
      , color (rgb 255 0 0)
      ]
    ]
    [ ul []
      ( List.map
        ( \error -> li [] [ text error ] )
        model.errors
      )
    ]
  , div []
    [ input
      [ type_ "text", value model.messageText, list "languages", size 80
      , onInput NewMessageText
      , onKeyDown ( \key -> if key == 13 then SendMessageRequest else NoOp)
      ]
      []
    , button [ onClick SendMessageRequest ] [ text "Send" ]
    ]
  , table [ css [ width (em 60) ] ]
    ( tr [ css [ textAlign left ] ]
      [ th [ css [ width (pct 15) ] ] [ text "From" ]
      , th [ css [ width (pct 60) ] ] [ text "Text" ]
      , th [ css [ width (pct 25) ] ] []
      ]
    ::( List.reverse <| List.indexedMap
        ( \idx chatMsg ->
          tr []
          [ td [ css [ verticalAlign top ] ] [ text chatMsg.sender ]
          , td []
            [ input
              [ type_ "text", value chatMsg.text, list "languages", size 80
              , onInput (NewOverrideMessageText idx)
              ]
              []
            ]
          , td []
            [ button
              [ onClick (RemoveMessage idx (Just chatMsg)) ]
              [ text "Accept" ]
            , button
              [ onClick (RemoveMessage idx (Just { chatMsg | sender = moderatorName })) ]
              [ text "Accept (as Moderator)" ]
            , button
              [ onClick (RemoveMessage idx Nothing) ]
              [ text "Reject" ]
            ]
          ]
        )
        model.chatMessages
      )
    )
  ]


-- Subscriptions
subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.batch
  [ WebSocket.listen model.eventsWsUrl Event
  , Time.every (3 * second) KeepAlive
  ]


main : Program Never Model Msg
main =
  Navigation.program
  ( always NoOp )
  { init = init
  , update = update
  , view = Html.toUnstyled << view
  , subscriptions = subscriptions
  }
