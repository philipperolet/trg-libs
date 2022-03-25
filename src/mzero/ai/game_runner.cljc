(ns mzero.ai.game-runner
  "Game runner protocol, responsible for starting and managing the world
  and the player.

  Comes with 2 implementations of the protocol, a straightforward
  monothreaded one, `MonoThreadRunner`, and another one using watch
  functions to run the game, `WatcherRunner`--which breaks for board
  sizes > 10 (stack overflow).
    
  Implementations are encouraged to enforce 2 constraints:
  
  - **timeliness** of the game, meaning that executing requested
  movements should not take more than 1ms. The program will not halt
  at the first delay over 1ms, for stability. However, it will throw
  an exception if delays happen too much;

  - for multithreaded impls, **thread-safe consistency** between
  `game-state` and `requested-movements`, meaning if an external
  thread sees that `requested-movements` is empty, it means that game
  state has already been updated. Conversely, if `requested-movements`
  is not empty, `game-state` has *not* been updated with those
  movements."
  (:require [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.game.events :as ge]))

(defprotocol GameRunner
  (run-game [runner]))

(defn game-should-continue
  "Return `false` if game should stop, `:until-end` if it should go on
  until game over, `:until-no-steps` if it should go on for a given
  number of remaining steps."
  [world remaining-steps]
  (cond
    #?(:clj (Thread/interrupted)) #?(:clj false)
    (not (aiw/active? world)) false
    (nil? remaining-steps) :until-end
    (pos? remaining-steps) :until-no-steps
    :else false))

(defn remaining-steps-fn
  "Return a function to compute number of remaining steps until game
  should stop running."
  [world opts]
  #(when-let [steps-to-do (opts :number-of-steps)]
     (+ (-> world ::aiw/game-step) steps-to-do (- (-> % ::aiw/game-step)))))

(defn move-to-next-level-if-needed [world-atom]
  (swap! world-atom aiw/update-to-next-level))

(def default-view-size 5)
(defn add-fog-to-board
  ([game-board [player-row player-col] clear-view-size]
   (let [distance (fn [x y] (ge/abs (ge/compute-distance x y (count game-board))))
         add-fog-to-cell
         (fn [cell-row cell-col cell-val]
           (if (and (<= (distance player-row cell-row) clear-view-size)
                    (<= (distance player-col cell-col) clear-view-size))
             cell-val
             :hidden))
         add-fog-to-row
         (fn [idx row]
           (vec (map-indexed #(add-fog-to-cell idx %1 %2) row)))]
     (vec (map-indexed add-fog-to-row game-board))))
  ([game-board position] (add-fog-to-board game-board position default-view-size)))

(defn add-fog
  [{:as world {:keys [::gs/player-position ::gb/game-board]} ::gs/game-state}]
  (-> world
      (assoc :saved-board game-board)
      (update-in [::gs/game-state ::gb/game-board]
                 add-fog-to-board player-position)))

(defn remove-fog [{:as world :keys [saved-board]}]
  (-> world
      (assoc-in [::gs/game-state ::gb/game-board] saved-board)
      (dissoc :saved-board)))

(defn switch-controls [world]
  (cond-> world
    (contains? (-> world ::aiw/requested-movements) :player)
    (update-in [::aiw/requested-movements :player] ge/opposed-direction)))

(defn update-with-momentum-rule
  [world]
  (let [player-movement
        (get-in world [::aiw/requested-movements :player])
        momentum-rule-update
        (fn [{:as momentum-rule :keys [last-move]}]
          (if player-movement
            {:last-move player-movement
             ;; moves in momentum-rule should not be nil ; if never moved
             ;; yet, initialize
             :before-last-move (or last-move :up)} 
            momentum-rule))]
    (update-in world [::gs/game-state :momentum-rule] momentum-rule-update)))

(defrecord MonoThreadRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (loop [nb-steps (when-let [s (opts :number-of-steps)] (dec s))]
      (when (aiw/level-rules @world-state :fog-of-war)
        (swap! world-state add-fog))
      (aip/request-movement player-state world-state)
      (when (aiw/level-rules @world-state :fog-of-war)
        (swap! world-state remove-fog))
      (when (not (opts :no-enemy-movement))
        (aiw/request-enemies-movements! world-state))
      (when (aiw/level-rules @world-state :controls-switch)
        (swap! world-state switch-controls))
      (when (aiw/level-rules @world-state :momentum-rule)
        (swap! world-state update-with-momentum-rule))
      (aiw/run-step world-state (opts :logging-steps))
      (move-to-next-level-if-needed world-state)
      (when-let [game-status (game-should-continue @world-state nb-steps)]
        (case game-status
          :until-end (recur nil) ;; nil means never stop running
          :until-no-steps (recur (dec nb-steps)))))))

