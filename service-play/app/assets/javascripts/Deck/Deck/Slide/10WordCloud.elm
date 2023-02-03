module Deck.Slide.WordCloud exposing
  ( wordCloud
  , implementation1EventSource
  , implementation2ExtractWords
  , implementation3RetainLastNWords
  , implementation4CountSendersByWord
  , implementation5Complete
  , implementation6CountSendersByWord
  , implementation7RetainLastNWords
  , implementation8ExtractWords
  )

import Css exposing
  ( Style, Vw
  -- Container
  , bottom, border3, borderRadius, borderSpacing, borderTop3
  , display, displayFlex, height, left, listStyle, marginBottom, overflow
  , padding2, paddingTop, position, right, top, width
  -- Content
  , alignItems, backgroundColor, backgroundImage, color, flexWrap, fontSize
  , justifyContent, lineHeight, opacity, textAlign, verticalAlign
  -- Units
  , em, num, pct, rgba, vw, zero
  -- Alignment & Positions
  , absolute, relative
  -- Transform
  -- Other values
  , block, center, hidden, linearGradient, none, solid, stop, wrap
  )
import Css.Transitions exposing (easeInOut, transition)
import Deck.Common exposing (TokenCounts)
import Deck.Slide.Common exposing (..)
import Deck.Slide.Template exposing (standardSlideView)
import Dict exposing (Dict)
import Html.Styled exposing (Html, div, p, table, td, text, th, tr, ul)
import Html.Styled.Attributes exposing (css, title)
import Set


-- Constants
heading : String
heading = "Audience Word Cloud"


maxWordDisplayCount : Int
maxWordDisplayCount = 11


chatMessageEventHeightEm : Float
chatMessageEventHeightEm = 5.5


tokenEventHeightEm : Float
tokenEventHeightEm = 3.8


eventBlockStyle : Style
eventBlockStyle =
  Css.batch
  [ border3 (em 0.125) solid black
  , borderSpacing zero, borderRadius (em 0.75)
  , backgroundColor themeBackgroundColor
  ]


-- View
-- Slides
wordCloud : UnindexedSlideModel
wordCloud =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading
      "How Would You Describe Kafka?"
      ( div []
        [ div
          [ css
            [ height (vw (if List.isEmpty model.wordCloud.tokensAndCounts then 20 else 0))
            , overflow hidden
            , transition
              [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
              , Css.Transitions.height3 transitionDurationMs 0 easeInOut
              ]
            ]
          ]
          [ ul []
            [ li [] [ text "Think of up to three words you associate with Kafka" ]
            , li [] [ text "Submit it one word per message on Zoom chat" ]
            , li [] [ text "Hyphenate multi-word phrases" ]
            ]
          ]
        , div
          [ css
            ( if List.isEmpty model.wordCloud.tokensAndCounts then [ display none ]
              else [ display block, height (vw 32), overflow hidden ]
            )
          ]
          -- From https://alvaromontoro.com/blog/67945/create-a-tag-cloud-with-html-and-css
          [ ul
            [ css
              [ displayFlex, flexWrap wrap, position relative
              , listStyle none, alignItems center, justifyContent center, lineHeight (vw 2)
              ]
            ]
            ( let
                maxCount : Float
                maxCount =
                  Maybe.withDefault 0.0
                  ( Maybe.map (toFloat << Tuple.second) (List.head model.wordCloud.tokensAndCounts) )
              in
              ( List.map
                ( \(word, count) ->
                  let
                    percentage : Float
                    percentage = toFloat count / maxCount
                  in
                  li []
                  [ div
                    [ css
                      [ padding2 (em 0.125) (em 0.25)
                      , color themeForegroundColor, opacity (num (percentage * 0.75 + 0.25))
                      , fontSize (em (3.6 * (percentage * 0.75 + 0.25)))
                      , transition [ Css.Transitions.fontSize3 transitionDurationMs 0 easeInOut ]
                      ]
                    , title (toString count)
                    ]
                    [ text word ]
                  ]
                )
                ( List.sort (List.take maxWordDisplayCount model.wordCloud.tokensAndCounts) )
              )
            )
          ]
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementationDiagramView : TokenCounts -> Int -> Float -> Float -> Bool -> Html msg
implementationDiagramView counts step fromLeft scale scaleChanged =
  let
    diagramHeightEm : Float
    diagramHeightEm = 13.5 / scale
  in
  div
  [ css
    [ position relative
    , height (vw 30)
    , overflow hidden
    ]
  ]
  [ div -- diagram view box
    [ css
      [ position relative
      , height (em diagramHeightEm)
      , fontSize (em scale)
      , overflow hidden
      , transition
        [ Css.Transitions.fontSize3 transitionDurationMs 0 easeInOut
        , Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
        , Css.Transitions.top3 transitionDurationMs 0 easeInOut
        ]
      ]
    ]
    [ div
      [ css
        [ position absolute, right (em (52 + fromLeft * 50))
        , transition
          ( if scaleChanged then []
            else [ Css.Transitions.right3 transitionDurationMs 0 easeInOut ]
          )
        ]
      ]
      [ div [] -- events
        ( Tuple.first
          ( List.foldr
            ( \chatMessageAndTokens (nodes, topEm) ->
              if topEm > diagramHeightEm then
                ( ( div [ css [ display none ] ] [] ) :: nodes
                , topEm
                )
              else
                ( ( div -- flatMap
                    [ css
                      [ position absolute, top (em topEm), width (em 52.2)
                      , borderTop3 (em 0.075) solid (if step > 0 then darkGray else white), paddingTop (em 0.3)
                      , transition
                        ( if scaleChanged then []
                          else
                            [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
                            , Css.Transitions.top3 transitionDurationMs 0 easeInOut
                            ]
                        )
                      ]
                    ]
                    [ div -- chat messages
                      [ css [ position absolute, left zero ] ]
                      [ table
                        [ css [ width (em 24), marginBottom (em 0.625), eventBlockStyle ] ]
                        [ tr []
                          [ th [ css [ width (em 6), textAlign right, verticalAlign top ] ] [ text "sender:" ]
                          , td [] [ text chatMessageAndTokens.chatMessage.sender ]
                          ]
                        , tr []
                          [ th [ css [ textAlign right, verticalAlign top ] ] [ text "recipient:" ]
                          , td [] [ text chatMessageAndTokens.chatMessage.recipient ]
                          ]
                        , tr []
                          [ th [ css [ textAlign right, verticalAlign top ] ] [ text "text:" ]
                          , td [] [ text chatMessageAndTokens.chatMessage.text ]
                          ]
                        ]
                      ]
                    , div
                      [ css
                        [ position absolute, left (em (if step > 0 then 27 else 43))
                        , transition
                          ( if scaleChanged then []
                            else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
                          )
                        ]
                      ]
                      [ text "- extractTokens() →" ]
                    , div -- extracted tokens
                      [ css
                        [ position absolute, left (em (if step > 0 then 36 else 52))
                        , transition
                          ( if scaleChanged then []
                            else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
                          )
                        ]
                      ]
                      ( List.map
                        ( \token ->
                          table
                          [ css [ width (em 16), marginBottom (em 0.5), eventBlockStyle ] ]
                          [ tr []
                            [ th [ css [ width (em 6), textAlign right, verticalAlign top ] ] [ text "sender:" ]
                            , td [] [ text chatMessageAndTokens.chatMessage.sender ]
                            ]
                          , tr []
                            [ th [ css [ textAlign right, verticalAlign top ] ] [ text "word:" ]
                            , td [] [ text token ]
                            ]
                          ]
                        )
                        ( List.reverse chatMessageAndTokens.tokens )
                      )
                    ]
                  ) :: nodes
                , let
                    rowHeightEm : Float
                    rowHeightEm =
                      max
                      chatMessageEventHeightEm
                      (toFloat (List.length chatMessageAndTokens.tokens) * tokenEventHeightEm)
                  in
                  topEm + rowHeightEm
                )
            )
            -- Initial value
            ( [ div
                [ css
                  [ position absolute, top (em -chatMessageEventHeightEm)
                  , transition
                    [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
                    , Css.Transitions.top3 transitionDurationMs 0 easeInOut
                    ]
                  ]
                ] []
              ]
            , -0.3
            )
            counts.chatMessagesAndTokens
          )
        )
      , div -- tokens by author
        [ css [ position absolute, left (em 62) ] ]
        ( if List.isEmpty counts.chatMessagesAndTokens then []
          else
            [ table [ css [ width (em 26), eventBlockStyle ] ]
              ( ( tr []
                  [ th [ css [ width (em 10) ] ] [ text "sender" ]
                  , th [] [ text "words" ]
                  ]
                )
              ::( List.reverse
                  ( Tuple.first
                    ( List.foldr
                      ( \chatMessageAndTokens (nodes, senders) ->
                        let
                          sender : String
                          sender = chatMessageAndTokens.chatMessage.sender
                        in
                        if Set.member sender senders then (nodes, senders)
                        else
                          ( ( tr []
                              [ td [ css [ textAlign center, verticalAlign top ] ] [ text sender ]
                              , td [ css [ textAlign center, verticalAlign top ] ]
                                [ text
                                  ( String.join ", "
                                    ( Maybe.withDefault []
                                      ( Dict.get sender counts.tokensBySender )
                                    )
                                  )
                                ]
                              ]
                            ) :: nodes
                          , Set.insert sender senders
                          )
                      )
                      ( [], Set.empty )
                      counts.chatMessagesAndTokens
                    )
                  )
                )
              )
            ]
        )
      , div -- counts by token
        [ css [ position absolute, left (em 99) ] ]
        ( if List.isEmpty counts.chatMessagesAndTokens then []
          else
            [ table [ css [ width (em 15), eventBlockStyle ] ]
              ( ( tr []
                  [ th [ css [ width (em 8) ] ] [ text "word" ]
                  , th [] [ text "senders" ]
                  ]
                )
              ::( let
                    countsByWord : Dict String Int
                    countsByWord = Dict.fromList counts.tokensAndCounts

                    words : List String
                    words = List.concatMap .tokens counts.chatMessagesAndTokens
                  in
                  List.reverse
                  ( Tuple.first
                    ( List.foldr
                      ( \word (nodes, displayedWords) ->
                        let
                          count : Int
                          count = Maybe.withDefault 0 (Dict.get word countsByWord)
                        in
                        if count == 0 || Set.member word displayedWords then (nodes, displayedWords)
                        else
                          ( ( tr []
                              [ td [ css [ textAlign center, verticalAlign top ] ] [ text word ]
                              , td [ css [ textAlign center, verticalAlign top ] ] [ text (toString count) ]
                              ]
                            ) :: nodes
                          , Set.insert word displayedWords
                          )
                      )
                      ( [], Set.empty )
                      words
                    )
                  )
                )
              )
            ]
        )
      ]
    ]
  , div -- fade out
    [ css
      [ position absolute, bottom zero, height (vw 10), width (pct 100)
      , backgroundImage (linearGradient (stop (rgba 255 255 255 0)) (stop white) [])
      ]
    ]
    []
  ]


implementationSubHeading : String
implementationSubHeading = "Word Clouds as a Reactive Streaming Application"


implementation1EventSource : UnindexedSlideModel
implementation1EventSource =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "We start the events: Zoom chat messages:" ]
        , implementationDiagramView model.wordCloud 0 -0.3 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation2ExtractWords : UnindexedSlideModel
implementation2ExtractWords =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "Words are extracted from the message text, retaining the sender:" ]
        , implementationDiagramView model.wordCloud 1 0 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation3RetainLastNWords : UnindexedSlideModel
implementation3RetainLastNWords =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "For each sender, retain the most recent three words:" ]
        , implementationDiagramView model.wordCloud 2 0.72 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation4CountSendersByWord : UnindexedSlideModel
implementation4CountSendersByWord =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "For each word, count the number of senders, using those counts as weights:" ]
        , implementationDiagramView model.wordCloud 3 1.24 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation5Complete : UnindexedSlideModel
implementation5Complete =
  { baseSlideModel
  | animationFrames = always 30
  , view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "Observe that information is lost as it moves through the system:" ]
        , implementationDiagramView model.wordCloud 4 1.24 0.34 (model.animationFramesRemaining > 0)
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation6CountSendersByWord : UnindexedSlideModel
implementation6CountSendersByWord =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "Counting the number of senders for a word, we lose who submitted the word:" ]
        , implementationDiagramView model.wordCloud 3 1.24 0.75 True
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation7RetainLastNWords : UnindexedSlideModel
implementation7RetainLastNWords =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "Retaining only recent words, we lose the full history of a sender's submissions:" ]
        , implementationDiagramView model.wordCloud 2 0.72 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


implementation8ExtractWords : UnindexedSlideModel
implementation8ExtractWords =
  { baseSlideModel
  | view =
    ( \page model ->
      standardSlideView page heading implementationSubHeading
      ( div []
        [ p [] [ text "Extracting and filtering words, we lose the original chat text, and the recipient:" ]
        , implementationDiagramView model.wordCloud 1 0 0.75 False
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }
