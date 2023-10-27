module Deck.Slide.AdditionalConsiderations exposing
  ( distributedDeployment, eventSourcing )

import Deck.Slide.Common exposing (..)
import Deck.Slide.SyntaxHighlight exposing (..)
import Deck.Slide.Template exposing (standardSlideView)
import Html.Styled exposing (Html, div, p, text, ul)


heading : String
heading = "Additional Considerations"


distributedDeployment : UnindexedSlideModel
distributedDeployment =
  { baseSlideModel
  | view =
    ( \page _ -> standardSlideView page heading
      "Considerations for Distributed Deployment"
      ( div []
        [ p []
          [ text "A common way to scale an application is to run it on several nodes, allowing us to speed it up through parallelism. "
          , text "The caveat being ordering cannot be guaranteed across nodes."
          ]
        , p []
          [ text "This means that for a given stream of elements, when ordering does not matter it is to trivial to distribute the application; "
          , text "When ordering matters across all events, is it impossible to. "
          , text "Most applications lies somewhere in between, where order matters, but only within a subset of events. "
          ]
        , p []
          [ text "In functional reactive streaming applications, event ordering matters when they matter for an aggregate (e.g., "
          , syntaxHighlightedCodeSnippet Kotlin "runningFold(R, (R, T) -> R)"
          , text "), or terminal (e.g., "
          , syntaxHighlightedCodeSnippet Kotlin "fold(R, (R, T) -> R)"
          , text ", "
          , syntaxHighlightedCodeSnippet Kotlin "collect()"
          , text ") operation."
          ]
        ]
      )
    )
  }


eventSourcing : UnindexedSlideModel
eventSourcing =
  { baseSlideModel
  | view =
    ( \page _ -> standardSlideView page heading
      "Application Source of Truth Considerations"
      ( div []
        [ p []
          [ text "Information is often lost as it flows through the system." ]
        , p []
          [ text "At first glance, one may be tempted to use the sender-words pairs as the source of truth for the word cloud application state. "
          , text "But consider if we need to make the following word cloud changes:"
          , ul []
            [ li [] [ text "Accept each sender’s last 7 words" ]
            , li [] [ text "Add or remove stop words" ]
            ]
          ]
        , p []
          [ text "We would not have the information to update the application state. "
          , text "If instead, we use the original events as the source of truth, "
          , text "we would be able to rebuild the application state by replay the events with the new configurations."
          ]
        ]
      )
    )
  }
