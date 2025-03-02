module Deck.Slide.SectionCover exposing (..)

import Deck.Slide.Common exposing (UnindexedSlideModel, baseSlideModel)
import Deck.Slide.Template exposing (sectionCoverSlideView)


introduction : UnindexedSlideModel
introduction =
  { baseSlideModel
  | view = ( \_ _ -> sectionCoverSlideView 1 "Introduction" )
  }


conclusion : UnindexedSlideModel
conclusion =
  { baseSlideModel
  | view = ( \_ _ -> sectionCoverSlideView 3 "Conclusion" )
  }


questions : UnindexedSlideModel
questions =
  { baseSlideModel
  | view = ( \_ _ -> sectionCoverSlideView 4 "Audience Questions" )
  , eventsWsPath = Just "question"
  }


thankYou : UnindexedSlideModel
thankYou =
  { baseSlideModel
  | view = ( \_ _ -> sectionCoverSlideView 5 "Thank You" )
  }
