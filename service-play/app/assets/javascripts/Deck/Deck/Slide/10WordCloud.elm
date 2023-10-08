module Deck.Slide.WordCloud exposing
  ( wordCloud, implementationSlides )

import Char
import Css exposing
  ( Color, Style, Vw
  -- Container
  , bottom, borderRadius, borderSpacing, boxShadow5, display, displayFlex
  , height, left, listStyle, margin2, maxWidth, overflow, padding2, paddingTop
  , position, right, textOverflow, top, width
  -- Content
  , alignItems, backgroundColor, backgroundImage, color, flexWrap, fontSize
  , justifyContent, lineHeight, opacity, textAlign, verticalAlign
  -- Units
  , em, num, pct, px, rgba, vw, zero
  -- Alignment & Positions
  , absolute, relative
  -- Transform
  -- Other values
  , block, center, ellipsis, hidden, linearGradient, none, noWrap, stop
  , whiteSpace, wrap
  )
import Css.Transitions exposing (easeInOut, transition)
import Deck.Slide.Common exposing (..)
import Deck.Slide.SyntaxHighlight exposing
  ( Language(Kotlin), syntaxHighlightedCodeBlock )
import Deck.Slide.Template exposing (standardSlideView)
import Dict exposing (Dict)
import Html.Styled exposing (Html, br, div, p, table, td, text, th, tr, ul)
import Html.Styled.Attributes exposing (css, title)
import Set
import WordCloud exposing (WordCounts)


-- Constants
heading : String
heading = "Audience Word Cloud"


maxWordDisplayCount : Int
maxWordDisplayCount = 11


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
            [ height (vw (if Dict.isEmpty model.wordCloud.countsByWord then 20 else 0))
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
            ( if Dict.isEmpty model.wordCloud.countsByWord then [ display none ]
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
                topWordsAndCounts : List (String, Int)
                topWordsAndCounts = WordCloud.topWords maxWordDisplayCount model.wordCloud

                maxCount : Float
                maxCount =
                  Maybe.withDefault 0.0
                  ( Maybe.map (toFloat << Tuple.second) (List.head topWordsAndCounts) )
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
                ( List.sort topWordsAndCounts )
              )
            )
          ]
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


type alias HorizontalPosition =
  { leftEm : Float
  , widthEm : Float
  }


type alias StepAdjustedHorizontalPosition =
  { base : HorizontalPosition
  , leftEmAdjustmentByStep : Dict Int Float
  }


type alias SlideLayout =
  { leftEm : Float
  , marginWidthEm : Float
  }


leftEmAfter : HorizontalPosition -> Float
leftEmAfter pos = pos.leftEm + pos.widthEm


horizontalPosition : StepAdjustedHorizontalPosition -> Int -> HorizontalPosition
horizontalPosition { base, leftEmAdjustmentByStep } step =
  { base
  | leftEm =
    base.leftEm + (Maybe.withDefault 0 (Dict.get step leftEmAdjustmentByStep))
  }


chatMessagesBasePos : HorizontalPosition
chatMessagesBasePos =
  { leftEm = 0
  , widthEm = 20
  }


mapNormalizeTextBasePos : HorizontalPosition
mapNormalizeTextBasePos =
  { leftEm = leftEmAfter chatMessagesBasePos
  , widthEm = 9.5
  }


normalizedTextBasePos : HorizontalPosition
normalizedTextBasePos =
  { leftEm = leftEmAfter mapNormalizeTextBasePos
  , widthEm = 18
  }


flatMapConcatSplitIntoWordsBasePos : HorizontalPosition
flatMapConcatSplitIntoWordsBasePos =
  { leftEm = leftEmAfter normalizedTextBasePos
  , widthEm = 9.5
  }


rawWordsBasePos : HorizontalPosition
rawWordsBasePos =
  { leftEm = leftEmAfter flatMapConcatSplitIntoWordsBasePos
  , widthEm = 12
  }


filterIsValidWordBasePos : HorizontalPosition
filterIsValidWordBasePos =
  { leftEm = leftEmAfter rawWordsBasePos
  , widthEm = 8.5
  }


validatedWordsBasePos : HorizontalPosition
validatedWordsBasePos =
  { leftEm = leftEmAfter filterIsValidWordBasePos
  , widthEm = 12
  }


runningFoldUpdateWordsForPersonBasePos : HorizontalPosition
runningFoldUpdateWordsForPersonBasePos =
  { leftEm = leftEmAfter validatedWordsBasePos
  , widthEm = 13.5
  }


wordsByPersonsBasePos : HorizontalPosition
wordsByPersonsBasePos =
  { leftEm = leftEmAfter runningFoldUpdateWordsForPersonBasePos
  , widthEm = 20
  }


mapCountPersonsForWordBasePos : HorizontalPosition
mapCountPersonsForWordBasePos =
  { leftEm = leftEmAfter wordsByPersonsBasePos
  , widthEm = 13
  }


personCountsByWordBasePos : HorizontalPosition
personCountsByWordBasePos =
  { leftEm = leftEmAfter mapCountPersonsForWordBasePos
  , widthEm = 14.5
  }


magnifiedWidthEm : Float
magnifiedWidthEm = leftEmAfter normalizedTextBasePos


magnifiedMarginWidthEm : HorizontalPosition -> HorizontalPosition -> Float
magnifiedMarginWidthEm left right =
  (magnifiedWidthEm - leftEmAfter right + left.leftEm) / 2


slideLayoutForStreams : HorizontalPosition -> HorizontalPosition -> SlideLayout
slideLayoutForStreams leftPos rightPos =
  let
    marginWidthEm : Float
    marginWidthEm = magnifiedMarginWidthEm leftPos rightPos
  in
  { leftEm = leftPos.leftEm - marginWidthEm
  , marginWidthEm = marginWidthEm
  }


implementation1Layout : SlideLayout
implementation1Layout =
  slideLayoutForStreams chatMessagesBasePos chatMessagesBasePos


implementation2Layout : SlideLayout
implementation2Layout =
  slideLayoutForStreams chatMessagesBasePos normalizedTextBasePos


implementation3Layout : SlideLayout
implementation3Layout =
  slideLayoutForStreams normalizedTextBasePos rawWordsBasePos


implementation4Layout : SlideLayout
implementation4Layout =
  slideLayoutForStreams rawWordsBasePos validatedWordsBasePos


implementation5Layout : SlideLayout
implementation5Layout =
  slideLayoutForStreams validatedWordsBasePos wordsByPersonsBasePos


implementation6Layout : SlideLayout
implementation6Layout =
  slideLayoutForStreams wordsByPersonsBasePos personCountsByWordBasePos


chatMessagesPos : StepAdjustedHorizontalPosition
chatMessagesPos =
  { base = chatMessagesBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (2, -implementation3Layout.marginWidthEm)
    ]
  }


mapNormalizeTextPos : StepAdjustedHorizontalPosition
mapNormalizeTextPos =
  { base = mapNormalizeTextBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (0, implementation1Layout.marginWidthEm)
    , (2, -implementation3Layout.marginWidthEm)
    ]
  }


normalizedTextPos : StepAdjustedHorizontalPosition
normalizedTextPos =
  { base = normalizedTextBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (0, implementation1Layout.marginWidthEm)
    , (3, -implementation4Layout.marginWidthEm)
    ]
  }


flatMapConcatSplitIntoWordsPos : StepAdjustedHorizontalPosition
flatMapConcatSplitIntoWordsPos =
  { base = flatMapConcatSplitIntoWordsBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (3, -implementation4Layout.marginWidthEm)
    ]
  }


rawWordsPos : StepAdjustedHorizontalPosition
rawWordsPos =
  { base = rawWordsBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (4, -implementation5Layout.marginWidthEm)
    ]
  }


filterIsValidWordPos : StepAdjustedHorizontalPosition
filterIsValidWordPos =
  { base = filterIsValidWordBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (2, implementation3Layout.marginWidthEm)
    , (4, -implementation5Layout.marginWidthEm)
    ]
  }


validatedWordsPos : StepAdjustedHorizontalPosition
validatedWordsPos =
  { base = validatedWordsBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (2, implementation3Layout.marginWidthEm)
    ]
  }


runningFoldUpdateWordsForPersonPos : StepAdjustedHorizontalPosition
runningFoldUpdateWordsForPersonPos =
  { base = runningFoldUpdateWordsForPersonBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (3, implementation4Layout.marginWidthEm)
    , (5, -implementation6Layout.marginWidthEm)
    ]
  }


wordsByPersonsPos : StepAdjustedHorizontalPosition
wordsByPersonsPos =
  { base = wordsByPersonsBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (3, implementation4Layout.marginWidthEm)
    ]
  }


mapCountPersonsForWordPos : StepAdjustedHorizontalPosition
mapCountPersonsForWordPos =
  { base = mapCountPersonsForWordBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (4, implementation5Layout.marginWidthEm)
    ]
  }


personCountsByWordPos : StepAdjustedHorizontalPosition
personCountsByWordPos =
  { base = personCountsByWordBasePos
  , leftEmAdjustmentByStep =
    Dict.fromList
    [ (4, implementation5Layout.marginWidthEm)
    ]
  }


streamElementView : HorizontalPosition -> Color -> Float -> Bool -> List (Html msg) -> Html msg
streamElementView pos color opacityNum scaleChanged rows =
  div
  [ css
    [ position absolute, left (em pos.leftEm), borderRadius (em 0.75)
    , backgroundColor white, opacity (num 1)
    , transition
      ( if scaleChanged then []
        else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
      )
    ]
  ]
  [ table
    [ css
      [ width (em pos.widthEm)
      , borderSpacing zero, borderRadius (em 0.75)
      , backgroundColor color, opacity (num opacityNum)
      , boxShadow5 zero (em 0.5) (em 0.5) (em -0.25) (rgba 0 0 0 0.25)
      ]
    ]
    rows
  ]


streamLineView : HorizontalPosition -> Float -> Html msg
streamLineView pos heightEm =
  div
  [ css
    [ position absolute
    , left (em (pos.leftEm + pos.widthEm / 2))
    , width (px 1), height (em heightEm)
    , backgroundImage (linearGradient (stop darkGray) (stop lightGray) [])
    ]
  ]
  []


operationView : HorizontalPosition -> Bool -> List String -> Html msg
operationView pos scaleChanged codeLines =
  div
  [ css
    [ position absolute, left (em pos.leftEm)
    , width (em pos.widthEm), margin2 (em 0.5) (em 0.75)
    , transition
      ( if scaleChanged then []
        else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
      )
    ]
  ]
  ( codeLines |> List.map text |> List.intersperse (br [] []) )


firstName : String -> String
firstName fullName =
  fullName
  |> String.words
  |> List.head
  |> Maybe.withDefault ""


implementationDiagramView : WordCounts -> Int -> Float -> Float -> Bool -> Html msg
implementationDiagramView counts step fromLeftEm scale scaleChanged =
  let
    diagramWidthEm : Float
    diagramWidthEm = leftEmAfter personCountsByWordBasePos

    visibleWidthEm : Float
    visibleWidthEm = diagramWidthEm / scale

    visibleHeightEm : Float
    visibleHeightEm = 56 / scale

    chatMessageHeightEm : Float
    chatMessageHeightEm = 5.5

    extractedWordHeightEm : Float
    extractedWordHeightEm = 3.85

    truncatedTextStyle : Style
    truncatedTextStyle =
      Css.batch
      [ overflow hidden, maxWidth zero -- Not really sure why this works
      , whiteSpace noWrap, textOverflow ellipsis
      ]
  in
  div
  [ css
    [ position relative
    , height (vw 32)
    , overflow hidden
    ]
  ]
  [ div -- diagram view box
    [ css
      [ position relative
      , height (em visibleHeightEm)
      , fontSize (em (scale * 38.75 / diagramWidthEm))
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
        [ position absolute
        , right (em (visibleWidthEm + fromLeftEm + 0.25))
        , transition
          ( if scaleChanged then []
            else [ Css.Transitions.right3 transitionDurationMs 0 easeInOut ]
          )
        ]
      ]
      [ div [] -- stream lines and operations
        [ streamLineView (horizontalPosition chatMessagesPos step) visibleHeightEm
        , operationView
          (horizontalPosition mapNormalizeTextPos step) scaleChanged
          [ "map(", "\xA0\xA0::normalizeText", ")" ]
        , streamLineView (horizontalPosition normalizedTextPos step) visibleHeightEm
        , operationView
          (horizontalPosition flatMapConcatSplitIntoWordsPos step) scaleChanged
          [ "flatMapConcat(", "\xA0\xA0::splitIntoWords", ")" ]
        , streamLineView (horizontalPosition rawWordsPos step) visibleHeightEm
        , operationView
          (horizontalPosition filterIsValidWordPos step) scaleChanged
          [ "filter(", "\xA0\xA0::isValidWord", ")" ]
        , streamLineView (horizontalPosition validatedWordsPos step) visibleHeightEm
        , operationView
          (horizontalPosition runningFoldUpdateWordsForPersonPos step) scaleChanged
          [ "runningFold(", "\xA0\xA0mapOf(),", "\xA0\xA0::updateWordsForPerson", ")" ]
        , streamLineView (horizontalPosition wordsByPersonsPos step) visibleHeightEm
        , operationView
          (horizontalPosition mapCountPersonsForWordPos step) scaleChanged
          [ "map(", "\xA0\xA0::countPersonsForWord", ")" ]
        , streamLineView (horizontalPosition personCountsByWordPos step) visibleHeightEm
        ]
      , div [] -- chat messages
        ( Tuple.first
          ( counts.history |> List.foldr
            ( \event (chatMessageDivs, topEm) ->
              if topEm > visibleHeightEm + 10 then
                ( ( div [ css [ display none ] ] [] ) :: chatMessageDivs
                , topEm
                )
              else
                let
                  extractedWordsHeightEm : Float
                  extractedWordsHeightEm =
                    toFloat (List.length event.words) * extractedWordHeightEm

                  bigSmallOffsetEm : Float
                  bigSmallOffsetEm = 0.875

                  chatMessageTopEm : Float
                  chatMessageTopEm =
                    if step < 2 then 0
                    else max 0 (extractedWordsHeightEm - chatMessageHeightEm + bigSmallOffsetEm)

                  chatMessageOpacityNum : Float
                  chatMessageOpacityNum = (max 0 ((16 - topEm) * 0.05)) + 0.2

                  partitionColor : Color
                  partitionColor =
                    case String.uncons event.chatMessage.sender of
                      Just (c, _) ->
                        case rem (Char.toCode c) 5 of
                          0 -> partition1Color
                          1 -> partition2Color
                          2 -> partition3Color
                          3 -> partition4Color
                          _ -> partition5Color
                      Nothing -> partition1Color
                in
                ( ( div -- per chat message
                    [ css
                      [ position absolute, top (em topEm), paddingTop (em 0.3)
                      , transition
                        ( if scaleChanged then []
                          else
                            [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
                            , Css.Transitions.top3 transitionDurationMs 0 easeInOut
                            ]
                        )
                      ]
                    ]
                    [ div
                      [ css
                        [ position absolute, top (em chatMessageTopEm)
                        , transition
                          ( if scaleChanged then []
                            else [ Css.Transitions.top3 transitionDurationMs 0 easeInOut ]
                          )
                        ]
                      ]
                      [ streamElementView -- per chat message - chat message
                        ( horizontalPosition chatMessagesPos step )
                        partitionColor chatMessageOpacityNum scaleChanged
                        [ tr []
                          [ th [ css [ width (em 5.4), textAlign right, verticalAlign top ] ] [ text "sender:" ]
                          , td [] [ text (firstName event.chatMessage.sender) ]
                          ]
                        , tr []
                          [ th [ css [ textAlign right, verticalAlign top ] ] [ text "recipient:" ]
                          , td [] [ text event.chatMessage.recipient ]
                          ]
                        , tr []
                          [ th [ css [ textAlign right, verticalAlign top ] ] [ text "text:" ]
                          , td [ css [ truncatedTextStyle ] ] [ text event.chatMessage.text ]
                          ]
                        ]
                      , div [ css [ position absolute, top (em bigSmallOffsetEm) ] ]
                        [ streamElementView -- per chat message - person and normalized text
                          ( horizontalPosition normalizedTextPos step )
                          partitionColor chatMessageOpacityNum scaleChanged
                          [ tr []
                            [ th [ css [ width (em 4.5), textAlign right, verticalAlign top ] ] [ text "person:" ]
                            , td [] [ text (firstName event.chatMessage.sender) ]
                            ]
                          , tr []
                            [ th [ css [ textAlign right, verticalAlign top ] ] [ text "text:" ]
                            , td [ css [ truncatedTextStyle ] ] [ text event.normalizedText ]
                            ]
                          ]
                        ]
                      ]
                    , div -- per chat message - extracted words
                      [ css
                        [ position absolute
                        , top (em (if List.length event.words > 1 then 0 else bigSmallOffsetEm))
                        , left (em rawWordsPos.base.leftEm)
                        , transition
                          ( if scaleChanged then []
                            else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
                          )
                        ]
                      ]
                      ( event.words |> List.indexedMap
                        ( \idx extractedWord ->
                          let
                            wordOpacityNum : Float
                            wordOpacityNum =
                              chatMessageOpacityNum * if idx == 0 then 1.0 else 0.5

                            shiftPos : HorizontalPosition -> HorizontalPosition
                            shiftPos pos =
                              { pos | leftEm = pos.leftEm - rawWordsBasePos.leftEm }

                            wordRows : List (Html msg)
                            wordRows =
                              [ tr []
                                [ th [ css [ width (em 4.5), textAlign right, verticalAlign top ] ] [ text "person:" ]
                                , td [] [ text (firstName event.chatMessage.sender) ]
                                ]
                              , tr []
                                [ th [ css [ textAlign right, verticalAlign top ] ] [ text "word:" ]
                                , td [ css [ truncatedTextStyle ] ] [ text extractedWord.word ]
                                ]
                              ]
                          in
                          div -- per extracted word
                          [ css
                            [ position absolute, top (em (toFloat idx * 3.75))
                            ]
                          ]
                          [ streamElementView -- per extracted word - raw word
                            ( shiftPos ( horizontalPosition rawWordsPos step ) )
                            partitionColor wordOpacityNum scaleChanged wordRows
                          , div []
                            ( if not extractedWord.isValid then []
                              else
                                [ streamElementView -- per extracted word - valid word
                                  ( shiftPos ( horizontalPosition validatedWordsPos step ) )
                                  partitionColor wordOpacityNum scaleChanged wordRows
                                , streamElementView -- aggregates - words by person
                                  ( shiftPos ( horizontalPosition wordsByPersonsPos step ) )
                                  partitionColor wordOpacityNum scaleChanged
                                  ( ( tr []
                                      [ th [ css [ width (em 7) ] ] [ text "person" ]
                                      , th [] [ text "words" ]
                                      ]
                                    )
                                  ::( List.reverse
                                      ( Tuple.first
                                        ( counts.history |> List.foldr
                                          ( \{ chatMessage } (wordsBySenderTrs, senders) ->
                                            let
                                              sender : String
                                              sender = chatMessage.sender
                                            in
                                            if Set.member sender senders then (wordsBySenderTrs, senders)
                                            else
                                              ( ( tr []
                                                  [ td [ css [ textAlign center, verticalAlign top ] ] [ text (firstName sender) ]
                                                  , td [ css [ textAlign center, verticalAlign top ] ]
                                                    [ text
                                                      ( String.join ", "
                                                        ( Maybe.withDefault []
                                                          ( Dict.get sender event.wordsBySender )
                                                        )
                                                      )
                                                    ]
                                                  ]
                                                ) :: wordsBySenderTrs
                                              , Set.insert sender senders
                                              )
                                          )
                                          ( [], Set.empty )
                                        )
                                      )
                                    )
                                  )
                                , streamElementView -- aggregates - person counts by word
                                  ( shiftPos ( horizontalPosition personCountsByWordPos step ) )
                                  partitionColor wordOpacityNum scaleChanged
                                  ( ( tr []
                                      [ th [] [ text "word" ]
                                      , th [ css [ width (em 6) ] ] [ text "persons" ]
                                      ]
                                    )
                                  ::( let
                                        words : List String
                                        words =
                                          counts.history |> List.concatMap .words |> List.map .word
                                      in
                                      List.reverse
                                      ( Tuple.first
                                        ( List.foldr
                                          ( \word (nodes, displayedWords) ->
                                            let
                                              count : Int
                                              count = Maybe.withDefault 0 (Dict.get word event.countsByWord)
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
                        )
                        |> List.reverse
                      )
                    ]
                  ) :: chatMessageDivs
                , topEm + chatMessageTopEm + chatMessageHeightEm
                )
            )
            -- Initial value
            ( [ div
                [ css
                  [ position absolute, top (em -chatMessageHeightEm)
                  , transition
                    [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
                    , Css.Transitions.top3 transitionDurationMs 0 easeInOut
                    ]
                  ]
                ] []
              ]
            , 0.0
            )
          )
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


implementationDiagramSlide : Int -> String -> String -> Bool -> Float -> Float -> Bool -> UnindexedSlideModel
implementationDiagramSlide step introText code showCode fromLeft scale scaleChanged =
  { baseSlideModel
  | animationFrames = if scaleChanged then always 30 else always 0
  , view =
    ( \page model ->
      standardSlideView page heading
      "Word Clouds as a Functional Reactive Application"
      ( div []
        [ p [ css [ margin2 (em 0.5) zero ] ] [ text introText ]
        , implementationDiagramView
          model.wordCloud step fromLeft scale
          (scaleChanged && model.animationFramesRemaining > 0)
        , div
          [ css
            ( [ position relative
              , transition
                [ Css.Transitions.opacity3 transitionDurationMs 0 easeInOut
                , Css.Transitions.top3 transitionDurationMs 0 easeInOut
                ]
              ]
            ++( if showCode then [ top (vw -31), opacity (num 0.875) ]
                else [ top zero, opacity zero ]
              )
            )
          ]
          [ syntaxHighlightedCodeBlock Kotlin Dict.empty Dict.empty [] code ]
        ]
      )
    )
  , eventsWsPath = Just "word-cloud"
  }


leftEmCentering : HorizontalPosition -> HorizontalPosition -> Float
leftEmCentering left right =
  (left.leftEm - (magnifiedWidthEm - leftEmAfter right + left.leftEm) / 2)


detailedMagnification : Float
detailedMagnification = leftEmAfter personCountsByWordBasePos / magnifiedWidthEm


implementation1ChatMessages : Bool -> UnindexedSlideModel
implementation1ChatMessages showCode =
  implementationDiagramSlide 0
  "We start the events - Zoom chat messages:"
  """
val chatMessages: Flow<ChatMessage> =
    ReceiverSettings<String, ChatMessage>(
        bootstrapServers = "localhost:9092",
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer()
            .map(Json::decodeFromString),
        groupId = "word-cloud-app", autoOffsetReset = Earliest
    ).let { settings ->
        KafkaReceiver(settings).receive("word-cloud.chat-message")
            .map { it.value() }
            .shareIn(CoroutineScope(Default), Lazily)
    }
""" showCode
  implementation1Layout.leftEm detailedMagnification False


implementation2MapNormalizeWords : Bool -> UnindexedSlideModel
implementation2MapNormalizeWords showCode =
  implementationDiagramSlide 1
  "The message text is normalized, retaining the sender as the person:"
  """
val NON_LETTER_PATTERN = Regex(\"""[^\\p{L}]+\""")
fun normalizeText(msg: ChatMessage): PersonAndText =
    PersonAndText(
        msg.sender,
        msg.text
            .replace(NON_LETTER_PATTERN, " ")
            .trim()
            .lowercase()
    )
""" showCode
  implementation2Layout.leftEm detailedMagnification False


implementation3FlatMapConcatSplitIntoWords : Bool -> UnindexedSlideModel
implementation3FlatMapConcatSplitIntoWords showCode =
  implementationDiagramSlide 2
  "The normalized text is split into words:"
  """
fun splitIntoWords(
    personText: PersonAndText
): Flow<PersonAndWord> = personText.text
    .split(" ")
    .map { PersonAndWord(personText.person, it) }
    .reversed()
    .asFlow()
""" showCode
  implementation3Layout.leftEm
  detailedMagnification False


implementation4FilterIsValidWord : Bool -> UnindexedSlideModel
implementation4FilterIsValidWord showCode =
  implementationDiagramSlide 3
  "Invalid words are filtered out:"
  """
fun isValidWord(personWord: PersonAndWord): Boolean =
    personWord.word.length in minWordLength..maxWordLength
        && !stopWords.contains(personWord.word)
""" showCode
  (leftEmCentering rawWordsPos.base validatedWordsPos.base)
  detailedMagnification False


implementation5RunningFoldUpdateWordsForPerson : Bool -> UnindexedSlideModel
implementation5RunningFoldUpdateWordsForPerson showCode =
  implementationDiagramSlide 4
  "For each person, retain their most recent three words:"
  """
fun updateWordsForPerson(
    wordsByPerson: Map<String, List<String>>,
    personWord: PersonAndWord
): Map<String, List<String>> {
    val oldWords: List<String> =
        wordsByPerson[personWord.person] ?: listOf()
    val newWords: List<String> =
        (listOf(personWord.word) + oldWords).distinct()
            .take(maxWordsPerPerson)
    return wordsByPerson + (personWord.person to newWords)
}
""" showCode
  (leftEmCentering validatedWordsPos.base wordsByPersonsPos.base)
  detailedMagnification False


implementation6MapCountPersonsForWord : Bool -> UnindexedSlideModel
implementation6MapCountPersonsForWord showCode =
  implementationDiagramSlide 5
  "For each word, count the number of persons, using those counts as weights:"
  """
fun countWords(
    wordsByPerson: Map<String, List<String>>
): Map<String, Int> = wordsByPerson
    .flatMap { it.value.map { word -> word to it.key } }
    .groupBy({ it.first }, { it.second })
    .mapValues { it.value.size }
""" showCode
  (leftEmCentering wordsByPersonsPos.base personCountsByWordPos.base)
  detailedMagnification False


implementation7Complete : Bool -> UnindexedSlideModel
implementation7Complete showCode =
  implementationDiagramSlide 6
  "Tying it all together:"
  """
val wordCounts: Flow<Counts> = chatMessages
    .map(::normalizeText)
    .flatMapConcat(::splitIntoWords)
    .filter(::isValidWord)
    .runningFold(mapOf(), ::updateWordsForPerson)
    .map(::countWords).map(::Counts)
    .shareIn(CoroutineScope(Default), Eagerly, 1)
""" showCode
  0.0 1.0 True


implementation8Complete : Bool -> UnindexedSlideModel
implementation8Complete showCode =
  implementationDiagramSlide 6
  "Observe that some events can be re-ordered without changing the final outcome:"
  """
val wordCounts: Flow<Counts> = chatMessages
    .map(::normalizeText)
    .flatMapConcat(::splitIntoWords)
    .filter(::isValidWord)
    .runningFold(mapOf(), ::updateWordsForPerson)
    .map(::countWords).map(::Counts)
    .shareIn(CoroutineScope(Default), Eagerly, 1)
""" showCode
  0.0 1.0 True


implementation9Complete : Bool -> UnindexedSlideModel
implementation9Complete showCode =
  implementationDiagramSlide 6
  "Furthermore, notice that information is lost as it flows through the system:"
  """
val wordCounts: Flow<Counts> = chatMessages
    .map(::normalizeText)
    .flatMapConcat(::splitIntoWords)
    .filter(::isValidWord)
    .runningFold(mapOf(), ::updateWordsForPerson)
    .map(::countWords).map(::Counts)
    .shareIn(CoroutineScope(Default), Eagerly, 1)
""" showCode
  0.0 1.0 True


implementationSlides : List UnindexedSlideModel
implementationSlides =
  [ implementation1ChatMessages False
  , implementation1ChatMessages True
  , implementation2MapNormalizeWords False
  , implementation2MapNormalizeWords True
  , implementation3FlatMapConcatSplitIntoWords False
  , implementation3FlatMapConcatSplitIntoWords True
  , implementation4FilterIsValidWord False
  , implementation4FilterIsValidWord True
  , implementation5RunningFoldUpdateWordsForPerson False
  , implementation5RunningFoldUpdateWordsForPerson True
  , implementation6MapCountPersonsForWord False
  , implementation6MapCountPersonsForWord True
  , implementation7Complete False
  , implementation7Complete True
  , wordCloud
  , implementation8Complete False
  , implementation9Complete False
  ]
