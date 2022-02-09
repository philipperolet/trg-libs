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
            [mzero.game.state :as gs]))

(defprotocol GameRunner
  (run-game [runner]))

(defn game-should-continue
  "Return `false` if game should stop, `:until-end` if it should go on
  until game over, `:until-no-steps` if it should go on for a given
  number of remaining steps."
  [world remaining-steps]
  (cond
    (Thread/interrupted) false
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

(defrecord MonoThreadRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (loop [nb-steps (when-let [s (opts :number-of-steps)] (dec s))] 
      (aip/request-movement player-state world-state)
      (aiw/request-enemies-movements! world-state)
      (aiw/run-step world-state (opts :logging-steps))
      (move-to-next-level-if-needed world-state)
      (when-let [game-status (game-should-continue @world-state nb-steps)]
        (case game-status
          :until-end (recur nil) ;; nil means never stop running
          :until-no-steps (recur (dec nb-steps)))))))

