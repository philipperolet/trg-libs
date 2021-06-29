(ns mzero.ai.players.random
  (:require [mzero.ai.player :refer [Player]]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]))

(defrecord RandomPlayer []
  Player
  (init-player [player _ _] player)
  (update-player [player _]
    (binding [g/*rnd* (:rng player)]
      (assoc player :next-movement (g/rand-nth ge/directions)))))

