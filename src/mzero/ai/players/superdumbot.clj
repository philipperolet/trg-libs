(ns mzero.ai.players.superdumbot
  "Like dumbot, with code to avoid enemies that are too close."
  (:require [mzero.ai.player :refer [Player]]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.ai.players.dumbot :refer [find-fastest-direction]]
            [clojure.data.generators :as g]))

(defn flee-enemies [enemy-positions player-position game-board fruit-direction]
  (let [move-to
        (fn [position direction]
          (ge/move-position position direction (count game-board))) 
        enemy-in-direction?
        (fn [position direction]
          (some #{(move-to position direction)} enemy-positions))
        obstacle-in-direction?
        (fn [position direction]
          (some #{(get-in game-board (move-to position direction))}
                [:wall :cheese]))
        enemy-next-to-direction?
        (fn [position direction]
          (let [new-position (move-to position direction)]
            (some #(enemy-in-direction? new-position %) ge/directions)))
        okay-directions
        (->> ge/directions
             (remove #(enemy-in-direction? player-position %)))
        good-directions
        (remove #(enemy-next-to-direction? player-position %) okay-directions)
        okay-directions-wo-obstacle
        (remove #(obstacle-in-direction? player-position %) okay-directions)
        good-directions-wo-obstacle
        (remove #(obstacle-in-direction? player-position %) good-directions)]
    (cond
      (= 4 (count good-directions)) ;; no enemy in sight
      fruit-direction

      (empty? okay-directions-wo-obstacle) ;; i am dead
      (g/rand-nth ge/directions)

      (empty? good-directions-wo-obstacle)
      (g/rand-nth okay-directions-wo-obstacle)

      :else
      (g/rand-nth good-directions-wo-obstacle))))

(defrecord SuperdumbotPlayer []
  Player
  (init-player [player _ _] player)

  (update-player
    [player
     {:as world
      {:keys [::gs/player-position ::gb/game-board ::gs/enemy-positions]}
      ::gs/game-state}]
    (let [fruit-direction
          (find-fastest-direction game-board player-position)]
      (assoc player :next-movement
             (flee-enemies enemy-positions player-position game-board fruit-direction)))))
