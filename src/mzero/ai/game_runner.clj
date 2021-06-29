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
            [mzero.ai.world :as aiw]))

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

(defrecord MonoThreadRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (loop [nb-steps (when-let [s (opts :number-of-steps)] (dec s))] 
      (aip/request-movement player-state world-state)
      (aiw/run-step world-state (opts :logging-steps))
      (when-let [game-status (game-should-continue @world-state nb-steps)]
        (case game-status
          :until-end (recur nil) ;; nil means never stop running
          :until-no-steps (recur (dec nb-steps)))))))

(defrecord WatcherRunner [world-state player-state opts]
  GameRunner
  (run-game [{:keys [world-state player-state opts]}]
    (let [remaining-steps (remaining-steps-fn @world-state opts)]

      ;; every time a movement is requested, run a world step
      ;; conversely, every time a movement is executed, request a new one
      (add-watch
       world-state :run-world
       (fn [_ _ _ new-st]
         (when (game-should-continue new-st (remaining-steps new-st))
           (if (-> new-st ::aiw/requested-movements :player)
             (aiw/run-step world-state (opts :logging-steps))
             (aip/request-movement player-state world-state)))))
      
      ;; if nothing moves during 1 ms, e.g. because the player
      ;; requested a nil movement, then request a new movement
      (while (game-should-continue @world-state (remaining-steps @world-state))
        (let [last-step (-> @world-state ::aiw/game-step)]
          (Thread/sleep 1)
          (when (= (-> @world-state ::aiw/game-step) last-step)
            (aip/request-movement player-state world-state)))))))
