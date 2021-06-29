(ns xps
  (:require [mzero.utils.xp :refer [experiment]]
            [mzero.ai.world :as aiw]
            [mzero.ai.main :as aim]
            [mzero.game.generation :as gg]))


(defn random-xp [player-type nb-xps]
  (let [board-size 27 seed 41
        worlds
        (map aiw/new-world (gg/generate-game-states nb-xps board-size seed true))
        game-args
        (map #(list (aim/parse-run-args "-t %s -o '{:seed %s :layer-dims [128]}'" player-type seed) %) worlds)]
    (experiment aim/run #(-> % :world ::aiw/game-step) game-args
                {:board-size board-size :seed seed :player-type player-type})))
