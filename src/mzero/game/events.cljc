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

(def opposed-direction {:up :down :down :up :left :right :right :left})

(s/def ::direction (set directions))

(s/def ::being (s/or :player #{:player}
                     :enemy (s/int-in 0 10)))

(s/def ::movement (s/tuple ::being ::direction))

(s/def ::movements-map (s/map-of ::being ::direction))

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

(defonce move-being-args-generator
  (gen/bind (s/gen ::gs/game-state)
            #(gen/tuple (gen/return (assoc % ::gs/status :active))
                        (gen/one-of [(gen/return :player) (gen/choose 0 (-> % ::gs/enemy-positions count))])
                        (s/gen ::direction))))

(s/fdef move-being
  :args (-> (s/cat :state ::gs/transient-game-state
                   :being ::being
                   :direction ::direction)
            (s/and
             (fn [{:keys [state being]}]
               (comment "Enemy to move actually exists")
               (or (= (first being) :player)
                   (< (second being) (-> state ::gs/enemy-positions count))))
             #(= (-> % :state ::gs/status) :active))
            (s/with-gen (fn [] move-being-args-generator)))
  :ret ::gs/transient-game-state)

(defn move-being
  "Move `being` according to provided `direction` on given `state`. Return
  state with updated enemy position and board."
  [{:keys [::gb/game-board ::gs/player-position ::gs/enemy-positions] :as state}
   being direction]
  (let [being-update-index
        (if (= :player being) [::gs/player-position] [::gs/enemy-positions being])
        being-position
        (if (= :player being) player-position (enemy-positions being))
        new-position (move-position being-position direction (count game-board))]
    (cond 
      ;; move fails on wall
      (= :wall (get-in game-board new-position))
      state 

      :else ;; move occurs
      (assoc-in state being-update-index new-position))))

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
            (s/with-gen
              (fn [] (gen/fmap #(vector (first %) (let [val (second %)] (if (= val :player) 0 val)))
                               move-being-args-generator))))
  :ret ::direction)
               
(defn move-enemy-random
  "Moves the enemy randomly, favoring directions towards the player"
  [{:as state, :keys [::gs/enemy-positions ::gs/player-position ::gb/game-board]}
   enemy-index]
  (let [distances
        (vec (map #(compute-distance %1 %2 (count game-board))
                  player-position
                  (enemy-positions enemy-index)))
        distance (reduce #(+ (abs %1) (abs %2)) distances)
        vertical-favor
        (if (pos? (distances 0))
          (vec (repeat 5 :down))
          (if (= 0 (distances 0))
            []
            (vec (repeat 5 :up))))
        horizontal-favor
        (if (pos? (distances 1))
          (vec (repeat 5 :right))
          (if (= 0 (distances 1))
            []
            (vec (repeat 5 :left))))
        total-favor (into vertical-favor horizontal-favor)
        final-favor (if (< distance 4) (into total-favor total-favor) total-favor)
        random-direction
        (rand-nth (into final-favor [:up :down :left :right]))]
    random-direction))
              
(defn move-player-path
  "Moves player repeatedly on the given collection of directions"
  [state directions]
  (reduce #(move-being %1 :player %2) state directions))

(defn- move-everybody
  [game-state requested-movements]
  (reduce #(move-being %1 (first %2) (second %2)) game-state requested-movements))

(defn- player-on?
  ([element player-position game-board]
   (= element (get-in game-board player-position)))
  ([element {:keys [::gs/player-position ::gb/game-board]}]
   (player-on? element player-position game-board)))

(defn- cljs-enemy-encountered-index?
  [{:as game-state :keys [::gs/player-position ::gs/enemy-positions]}]
  (first (keep-indexed #(when (= player-position %2) %1) enemy-positions)))

(defn- clj-enemy-encountered-index?
  "Return the index of the enemy encountered, or nil if no enemies encountered."
  [{:as game-state :keys [::gs/player-position ::gs/enemy-positions]}]
  (let [enemy-index (.indexOf enemy-positions player-position)]
    (if (= -1 enemy-index) nil enemy-index)))

(def enemy-encountered-index? #?(:clj clj-enemy-encountered-index?
                                 :cljs cljs-enemy-encountered-index?))

(defn- level-finished? [{:as game-state :keys [::gb/game-board]}]
  (and (player-on? :fruit game-state)
       (= 1 (gb/count-cells game-board :fruit)))) ;; last fruit to be eaten

(defn- momentum-impossible?
  "momentum is not possible if there is a cheese or a wall"
  [player-position last-move game-board]
  (let [momentum-position
        (-> player-position
            (move-position (opposed-direction last-move) (count game-board))
            (move-position (opposed-direction last-move) (count game-board)))
        momentum-cell (get-in game-board momentum-position)]
    (or (= momentum-cell :cheese) (= momentum-cell :wall))))

(defn player-can-eat?
  [{:as game-state :keys [momentum-rule ::gs/player-position ::gb/game-board]}]
  (let [{:keys [last-move before-last-move]} momentum-rule
        player-has-momentum?
        ;;  the player has momentum when it went at least twice in the same direction
        (= last-move before-last-move)]
    (or (not momentum-rule)
        (momentum-impossible? player-position last-move game-board) 
        player-has-momentum?)))

(defn- update-score
  [game-state]
  (update game-state ::gs/score
          #(cond-> %
             (and (player-on? :fruit game-state) (player-can-eat? game-state))
             inc)))

(defn- reset-enemy-position
  "After having struck the player, an enemy reappears at a large
  distance from the player"
  [enemy-position game-board]
  (let [board-size (count game-board)
        new-pos-on-axis
        (fn [pos]
          (-> (+ pos (/ board-size 2))
              int
              (mod board-size)))
        new-position (mapv new-pos-on-axis enemy-position)]
    (gb/find-in-board game-board #{:empty :cheese :fruit} new-position)))

(defn- update-status [{:as game-state :keys [::gs/score ::gb/game-board]}]
  (assoc game-state ::gs/status 
         (cond
           (level-finished? game-state) :won
           (enemy-encountered-index? game-state) :over
           (player-on? :cheese game-state) :over
           :else :active)))

(defn- clean-board
  [{:as game-state :keys [::gs/player-position ::gb/game-board]}]
  (let [enemy-encountered-index (enemy-encountered-index? game-state)]
    (cond-> game-state
      (or (and (player-on? :fruit game-state) (player-can-eat? game-state))
          (player-on? :cheese game-state))
      (assoc-in (into [::gb/game-board] player-position) :empty)

      enemy-encountered-index
      (update-in [::gs/enemy-positions enemy-encountered-index]
                 reset-enemy-position game-board))))

(defn- fit-game-state-and-movements
  [[game-state movements]]
  (let [max-index (dec (count (-> game-state ::gs/enemy-positions)))
        updated-movements
        (select-keys movements
                     (remove #(and (number? %) (> % max-index)) (keys movements)))]
    [game-state updated-movements]))

(s/fdef game-step
  :args (-> (s/cat :game-state ::gs/game-state
                   :requested-movements ::movements-map)
            (s/and (fn [{:keys [game-state requested-movements]}]
                     (comment "Enemies requesting movements exist on the board")
                     (let [enemies-nb (count (-> game-state ::gs/enemy-positions))]
                       (every? #(or (= :player %) (< % enemies-nb))
                               (keys requested-movements))))
                   (fn [{:keys [game-state]}]
                     (= :active (-> game-state ::gs/status))))
            (s/with-gen
              (fn []
                (gen/fmap fit-game-state-and-movements
                          (gen/tuple (s/gen ::gs/game-state) (s/gen ::movements-map))))))
  :ret ::gs/game-state)

(defn game-step
  "Execute a game step given `requested-movements`. Return the updated
  `game-state`"
  [game-state requested-movements]
  (-> game-state
      (move-everybody requested-movements)
      update-score
      update-status
      clean-board))
