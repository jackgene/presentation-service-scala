module Deck.Slide.MarbleDiagram exposing (..)

import Css exposing
  ( Color, Style
  -- Container
  , borderRadius, bottom, boxShadow5, displayFlex, height, left
  , margin, overflow, padding2, position, top, width
  -- Content
  , backgroundColor, backgroundImage, fontSize
  -- Units
  , em, pct, px, vw, zero
  -- Color
  , rgb, rgba
  -- Alignment & Positions
  , absolute
  -- Other values
  , auto, hidden, linearGradient, stop
  )
import Css.Transitions exposing (easeInOut, linear, transition)
import Deck.Slide.Common exposing (..)
import Html.Styled exposing (Html, br, div, text)
import Html.Styled.Attributes exposing (css)


type alias HorizontalPosition =
  { leftEm : Float
  , widthEm : Float
  }


type Shape
  = Disc
  | Square


type alias Element =
  { value : Int
  , shape : Shape
  , partition : Int
  , time : Float
  }


type OperandValue
  = Stream
    { terminal : Bool
    , elements : List Element
    }
  | Single Element


type alias Operand =
  { horizontalPosition : HorizontalPosition
  , value : OperandValue
  }


type alias Operation =
  { horizontalPosition : HorizontalPosition
  , operatorCode : List String
  }


-- From https://www.pastelcolorpalettes.com/primary-colors-in-pastels
partition0Color : Color
partition0Color = rgb 163 188 232


partition3Color : Color
partition3Color = rgb 250 248 132


partition4Color : Color
partition4Color = rgb 240 154 160


partition1Color : Color
partition1Color = rgb 182 232 142


partition2Color : Color
partition2Color = rgb 235 174 131


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
    , width (em pos.widthEm), padding2 (em 0.75) (em 0.25)
    , transition
      ( if scaleChanged then []
        else [ Css.Transitions.left3 transitionDurationMs 0 easeInOut ]
      )
    ]
  ]
  ( codeLines |> List.map text |> List.intersperse (br [] []) )


streamElementView : Shape -> Color -> Int -> Html msg
streamElementView shape color value =
  div
  [ css
    [ displayFlex, width (vw 5), height (vw 5), margin (vw 1)
    , backgroundColor color
    , borderRadius
      ( case shape of
          Disc -> pct 50
          Square -> pct 0
      )
    , boxShadow5 zero (em 0.25) (em 0.25) (em -0.125) (rgba 0 0 0 0.25)
    ]
  ]
  [ div
    [ css
      [ margin auto, fontSize (if value < 1000 then (em 1) else (em 0.8)) ]
    ]
    [ text (toString value) ]
  ]


partitionColor : Int -> Color
partitionColor partition =
  ( case rem (partition - 1) 3 of
      0 -> partition0Color
      1 -> partition1Color
      _ -> partition2Color
  )


operandView : Operand -> Float -> Bool -> Html msg
operandView operand lastElementTime animate =
  case operand.value of
    Stream { terminal, elements } ->
      div
      [ css
        [ position absolute
        , left (em operand.horizontalPosition.leftEm)
        , bottom
          ( em
            ( let
                shiftedTime : Float
                shiftedTime =
                  if not animate then 8000
                  else 5500 - lastElementTime

                bottomEm : Float
                bottomEm = 2 + 4 * shiftedTime / 2000
              in bottomEm
            )
          )
        , height (em (4 * lastElementTime / 2000))
        , ( if not animate then Css.batch []
            else
              transition
              [ Css.Transitions.bottom3 lastElementTime 0 linear ]
          )
        ]
      ]
      ( elements |> List.map
        ( \element ->
          div
          [ css
            [ position absolute
            , bottom (em (4 * element.time / 2000))
            ]
          ]
          [ streamElementView element.shape (partitionColor element.partition) element.value ]
        )
      )

    Single element ->
      div [] [ streamElementView element.shape (partitionColor element.partition) element.value ]


diagramView : Operand -> Operation -> Operand -> Bool -> Html msg
diagramView input operation output animate =
  let
    lastElementInputTime : Float
    lastElementInputTime =
      Maybe.withDefault 0
      ( case input.value of
          Stream { elements } ->
            elements |> List.map .time |> List.maximum
          _ -> Nothing
      )

    lastElementOutputTime : Float
    lastElementOutputTime =
      Maybe.withDefault 0
      ( case output.value of
          Stream { elements } ->
            elements |> List.map .time |> List.maximum
          _ -> Nothing
      )

    lastElementTime : Float
    lastElementTime =
      max lastElementInputTime lastElementOutputTime
  in
  div -- diagram frame
  [ css
    [ position absolute, width (vw 40), height (vw 36)
    , overflow hidden
    ]
  ]
  [ div -- static background
    [ css [ position absolute, top zero ] ]
    [ streamLineView input.horizontalPosition 18
    , operationView operation.horizontalPosition False operation.operatorCode
    , streamLineView output.horizontalPosition 18
    ]
  -- animated foreground
  , operandView input lastElementTime animate
  , operandView output lastElementTime animate
  ]
