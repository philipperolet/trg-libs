(ns mzero.game.events
  "Defines game events such as player movement.

  A being represents either the player or an enemy (10 enemies max).

  A movement is then a being choosing a direction to move."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]))

;;; Movement on board
;;;;;;;
(def directions [:up :right :down :left])

(s/def ::direction (set directions))

(s/def ::being (s/or :player #{:player}
                     :enemy (s/int-in 0 10)))

(s/def ::movement (s/tuple ::being ::direction))

(s/fdef move-position
  :args (s/cat :position ::gb/position
               :direction ::direction
               :board-size ::gb/board-size)
  :ret ::gb/position)

(defn move-position
  "Given a board position, returns the new position when moving in
  provided direction, by adding direction to position modulo size of board"
  [[x y] direction board-size]
  (let [move-inc #(mod (unchecked-inc %) board-size)
        move-dec #(mod (unchecked-dec %) board-size)]
    (->> (case direction
           :up [(move-dec x) y]
           :right [x (move-inc y)]
           :down [(move-inc x) y]
           :left [x (move-dec y)]))))

(s/fdef move-player
  :args (-> (s/cat :state ::gs/game-state :direction ::direction)
            (s/and #(= (-> % :state ::gs/status) :active)))
  :ret ::gs/game-state)

(defn- game-over [state new-position]
  (-> state 
      (assoc ::gs/player-position new-position) 
      (assoc-in (into [::gb/game-board] new-position) :empty)
      (assoc ::gs/status :over)))

(defn move-player
  "Moves player according to provided direction on given state: returns
  state with updated player-position and board."
  [{:as state, :keys [::gb/game-board ::gs/player-position ::gs/enemy-positions]}
   direction]
  (let [new-position (move-position player-position direction (count game-board))]
    (if (some #{new-position} enemy-positions) ;; game over if moves on enemy
      (game-over state new-position)

      ;; depending on where player wants to move
      (case (get-in game-board new-position) 
        :wall state ;; move fails
      
        :empty (assoc state ::gs/player-position new-position) ;; move occurs
      
        :fruit (-> state ;; move occurs and score increases, possibly winning
                   (assoc ::gs/player-position new-position)
                   (update ::gs/score inc)
                   (assoc-in (into [::gb/game-board] new-position) :empty)
                   (#(if (= (gb/count-cells (% ::gb/game-board) :fruit) 0)
                       (assoc % ::gs/status :won) %)))
      
        :cheese (game-over state new-position))))) ;; game over

(defonce move-enemy-args-generator
  (gen/bind (s/gen ::gs/game-state)
            #(gen/tuple (gen/return (assoc % ::gs/status :active))
                        (s/gen ::direction)
                        (gen/choose 0 (-> % ::gs/enemy-positions count)))))

(s/fdef move-enemy
  :args (-> (s/cat :state ::gs/game-state
                   :direction ::direction
                   :enemy-index ::gs/enemy-nb)
            (s/and #(< (:enemy-index %) (-> % :state ::gs/enemy-positions count))
                   #(= (-> % :state ::gs/status) :active))
            (s/with-gen (fn [] move-enemy-args-generator)))
  :ret ::gs/game-state)

(defn move-enemy
  "Moves player according to provided direction on given state: returns
  state with updated enemy position and board."
  [{:as state, :keys [::gb/game-board ::gs/player-position ::gs/enemy-positions]}
   direction
   enemy-index]
  (let [enemy-position (enemy-positions enemy-index)
        new-position (move-position enemy-position direction (count game-board))]
    (cond ;; depending on where enemy wants to move    
      ;; game over if moves on player
      (= new-position player-position) 
      (do (assoc-in state [::gs/enemy-positions enemy-index] new-position)
          (game-over state new-position))
      
      ;; move fails on wall
      (= :wall (get-in game-board new-position))
      state 

      (= 1 1) ;; move occurs
      (assoc-in state [::gs/enemy-positions enemy-index] new-position))))

;; no spec for move-being since specs of move-player / move-enemy are already there
(defn move-being
  "Moves being according to provided movement (being type + direction)
  on given state: returns updated game state"
  [{:keys [::gb/game-board ::gs/player-position ::gs/enemy-positions], :as state}
   [being direction, :as movement]]
  (if (= being :player)
    (move-player state direction)
    (move-enemy state direction being)))
  

(defn compute-distance [x y size]
  (let [diff (- x y)]
    (cond
      (< (- (/ size 2)) diff (/ size 2)) diff
      (>= diff (/ size 2)) (- diff size)
      (<= diff (- (/ size 2))) (+ diff size))))

(defn abs [x]
  (if (pos? x) x (- x)))

(s/fdef move-enemy-random
  :args (-> (s/cat :state ::gs/game-state
                   :enemy-index ::gs/enemy-nb)
            (s/and #(< (:enemy-index %) (-> % :state ::gs/enemy-positions count))
                   #(= (-> % :state ::gs/status) :active))
            (s/with-gen (fn [] (gen/fmap #(vector (% 0) (% 2))
                                         move-enemy-args-generator))))
  :ret ::gs/game-state)
               
(defn move-enemy-random
  [{:as state, :keys [::gs/enemy-positions ::gs/player-position ::gb/game-board]}
   enemy-index]
  "Moves the enemy randomly, favoring directions towards the player"
  (let [distances
        (vec (map #(compute-distance %1 %2 (count game-board))
                  player-position
                  (enemy-positions enemy-index)))
        distance (reduce #(+ (abs %1) (abs %2)) distances)
        vertical-favor (if (pos? (distances 0)) [:down :down] (if (= 0 (distances 0)) [] [:up :up]))
        horizontal-favor (if (pos? (distances 1)) [:right :right] (if (= 0 (distances 1)) [] [:left :left]))
        total-favor (into vertical-favor horizontal-favor)
        final-favor (if (< distance 4) (into total-favor total-favor) total-favor)
        random-direction
        (rand-nth (into total-favor [:up :down :left :right]))]
    (move-enemy state random-direction enemy-index)))
              
(defn move-player-path
  [state directions]
  "Moves player repeatedly on the given collection of directions"
  (reduce move-player state directions))

