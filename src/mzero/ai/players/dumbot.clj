(ns mzero.ai.players.dumbot
  "Player moving directly towards fruits and avoiding cheeses using a
  simple bellman iteration."
  (:require [mzero.ai.player :refer [Player]]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [clojure.spec.alpha :as s]))

(defn- adjacent-cells [cell-position board-size]
  (zipmap ge/directions
          (map #(ge/move-position cell-position % board-size) ge/directions)))

(defn- direction-to-good-cell [game-board [direction position]]
  (let [cell (get-in game-board position)]
    (when (or (= :fruit cell) (some #{cell} ge/directions))
      direction)))

(defn- new-cell-value [game-board cell-position]
  (let [possible-directions
        (keep (partial direction-to-good-cell game-board)
              (adjacent-cells cell-position (count game-board)))]
    (if (empty? possible-directions)
      :empty
      (first possible-directions))))

(defn- directionize-board
  "Any empty spot next to a fruit is marked with the direction to this
  fruit. Any empty spot next to a direction is marked with the
  direction to this direction. That's it folks"
  [game-board]
  (letfn [(directionize-cell [row-index col-index cell]
            (if (= :empty cell)
              (new-cell-value game-board [row-index col-index])
              cell))
          (directionize-row [index row]
            (vec (map-indexed (partial directionize-cell index) row)))]
    (vec (map-indexed directionize-row game-board))))

(defn- find-fastest-direction [game-board player-position]
  (let [fastest-direction (get-in game-board player-position)]
    (if (s/valid? ::ge/direction fastest-direction)
      fastest-direction
      (recur (directionize-board game-board) player-position))))

(defrecord DumbotPlayer []
  Player
  (init-player [player _ _] player)

  (update-player [player
                  {:as world
                   {:keys [::gs/player-position ::gb/game-board]} ::gs/game-state}]
    (assoc player :next-movement
           (find-fastest-direction game-board player-position))))