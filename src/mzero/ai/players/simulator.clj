(ns mzero.ai.players.simulator
  "Simulate N plays of size M"
  (:require [mzero.ai.player :refer [Player]]
            [mzero.game.events :as ge]
            [clojure.data.generators :as g]
            [mzero.game.state :as gs]
            [clojure.spec.alpha :as s]
            [mzero.game.board :as gb]))

(def nb-simulations 600)

(s/def ::value (s/and double?))
(s/def ::path (s/every ::ge/direction))
(s/def ::simulation (s/keys :req [::path ::value]))

(defn- max-simulation-size [game-state]
  (count (-> game-state ::gb/game-board)))

(s/fdef simulation-step
  :args (s/cat :simulation ::simulation
               :game-state ::gs/game-state
               :remaining-steps int?))

(defn- simulation-step [simulation game-state remaining-steps]
  (let [last-direction
        (or (empty? (::path simulation))
            (-> simulation ::path (#(nth % (dec (count %))))))
        next-direction (g/rand-nth (remove #{last-direction} ge/directions))
        requested-movements {:player next-direction}
        next-simulation (update simulation ::path conj next-direction)
        next-state (ge/game-step game-state requested-movements)
        next-remaining-steps ;; negative remaining steps means score incresed
        (if (> (::gs/score next-state) (::gs/score game-state))
          -1.0
          (dec remaining-steps))]
    [next-simulation next-state next-remaining-steps]))

(defn- generate-simulation-recursively
  [simulation game-state remaining-steps]
  (cond
    (= :over (::gs/status game-state))
    (assoc simulation ::value ##Inf)

    (neg? remaining-steps) ;; negative remaining steps means score incresed
    (assoc simulation ::value (count (::path simulation)))
    
    (zero? remaining-steps)
    (assoc simulation ::value (* 2 (max-simulation-size game-state)))
    
    :else
    (let [[next-simulation next-state next-remaining-steps]
          (simulation-step simulation game-state remaining-steps)]
      (recur next-simulation next-state next-remaining-steps))))

(defn- generate-simulation
  "Generate a random simulation of the game"
  [game-state]
  (let [simulation
        {::path []
         ::value ##Inf}]
    (generate-simulation-recursively simulation
                                     game-state
                                     (max-simulation-size game-state))))

(defrecord SimulatorPlayer []
  Player
  (init-player [player _ _] player)
  (update-player [player {:as world :keys [::gs/game-state]}]
    (binding [g/*rnd* (:rng player)]
      (let [simulations
            (->> (pmap (fn [_]
                         (vec (repeatedly (quot nb-simulations 8) #(generate-simulation game-state)))) (range 8))
                (reduce into))
            #_(apply pcalls (repeat nb-simulations #(generate-simulation game-state)))
            #_(repeatedly nb-simulations #(generate-simulation game-state))
            simulations-starting-with
            (fn [simulations direction]
              (filter #(= direction (first (::path %))) simulations))
            min-value-of (fn [simulations] (apply min (map ::value simulations)))
            move-value
            (fn [direction]
              (min-value-of (simulations-starting-with simulations direction)))
            best-direction (apply min-key move-value ge/directions)]
        (assoc player :next-movement best-direction)))))
