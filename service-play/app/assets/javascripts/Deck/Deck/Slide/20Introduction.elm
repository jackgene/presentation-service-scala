module Deck.Slide.Introduction exposing ( introduction )

import Deck.Slide.Common exposing (..)
import Deck.Slide.Template exposing (standardSlideView)
import Html.Styled exposing (Html, div, p, text)


-- Constants
heading : String
heading = "Introduction to “Topic”"


-- Slides
introduction : UnindexedSlideModel
introduction =
  { baseSlideModel
  | view =
    ( \page _ ->
      standardSlideView page heading
      "Template Introduction"
      ( div []
        [ p []
          [ text "Lorem ipsum dolor sit amet"
          , blockquote []
            [ p []
              [ text "Lorem ipsum dolor sit amet "
              , text "Lorem ipsum dolor sit amet"
              ]
            , p [] [ text "..." ]
            , p []
              [ text "Lorem ipsum dolor sit amet"
              ]
            ]
          ]
        ]
      )
    )
  }
