(ns mzero.ai.players.superdumbot
  "Like dumbot, with code to avoid enemies that are too close."
  (:require [mzero.ai.player :refer [Player]]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.ai.players.dumbot :refer [find-fastest-direction]]))

(defn flee-enemies [enemy-positions player-position game-board]
  (let [board-size (count game-board)
        enemy-in-direction?
        (fn [position direction]
          (some #{(ge/move-position position direction board-size)}
                enemy-positions))
        wall-in-direction?
        (fn [position direction]
          (= :wall
             (get-in game-board (ge/move-position position direction board-size))))
        enemy-next-to-direction?
        (fn [position direction]
          (let [new-position
                (ge/move-position position direction board-size)]
            (some #(enemy-in-direction? new-position %) ge/directions)))
        enemy-next2-to-direction?
        (fn [position direction]
          (let [new-position
                (ge/move-position position direction board-size)]
            (some #(enemy-next-to-direction? new-position %) ge/directions)))
        okay-directions
        (->> ge/directions
             (remove #(enemy-in-direction? player-position %))
             (remove #(wall-in-direction? player-position %)))
        good-directions
        (remove #(enemy-next-to-direction? player-position %) okay-directions)
        very-good-directions good-directions]
    (if (empty? very-good-directions)
      (if (empty? good-directions)
        okay-directions
        good-directions)
      very-good-directions)))

(defrecord SuperdumbotPlayer []
  Player
  (init-player [player _ _] player)

  (update-player
    [player
     {:as world
      {:keys [::gs/player-position ::gb/game-board ::gs/enemy-positions]}
      ::gs/game-state}]
    (let [flee-directions
          (flee-enemies enemy-positions player-position game-board)
          fruit-direction
          (find-fastest-direction game-board player-position)]
      (assoc player :next-movement
             (if (some #{fruit-direction} flee-directions)
               fruit-direction
               (or (rand-nth flee-directions) fruit-direction))))))
