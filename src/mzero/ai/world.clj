(ns mzero.ai.world
  "Module responsible for running the world, by:
  - listening to movement requests (on `requested-movements`);
  - updating the `world-state` according to the movement requests;
  - updating the world's timestamp every time its state changes
    (ATTOW only via requested-movements).

  Regarding the first element, movement requests can be made by the
  player as well as by enemies. The last element is intended to allow
  a detailed execution history."
  (:require [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.utils.utils :as u]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.tools.logging :as log]
            [mzero.game.generation :as gg]))

;;; Full game state spec & helpers
;;;;;;;

(s/def ::game-step nat-int?)

;; timestamp in ms of current step start
(s/def ::step-timestamp nat-int?)

(s/def ::requested-movements (s/map-of ::ge/being ::ge/direction))

(defn- world-state-predicate-matcher
  "Generator helper to match ::world-state constraints"
  [world-state]
  (-> world-state
      (assoc ::requested-movements
             (u/filter-keys
              #(or (= % :player)
                   (< % (count (get-in world-state
                                       [::gs/game-state ::gs/enemy-positions]))))
              (world-state ::requested-movements)))))

(def world-state-keys-spec
  (s/keys :req [::gs/game-state
                ::game-step
                ::requested-movements
                ::step-timestamp]))

(s/def ::world-state
  (-> world-state-keys-spec
      (s/and (fn [{:keys [::requested-movements]
                   {:keys [::gs/enemy-positions]} ::gs/game-state}]
               (comment "for all movements,  enemy index < enemy count")
               (every? #(or (= % :player) (< % (count enemy-positions)))
                       (keys requested-movements))))
      (s/with-gen #(gen/fmap
                    world-state-predicate-matcher
                    (s/gen world-state-keys-spec)))))

(defn data->string
  "Converts game data to nice string"
  [{:keys [::gs/game-state ::game-step ::step-timestamp]}]
  (str (format "Step %d\nScore %d\nTimestamp (mod 1 000 000) %d"
               game-step
               (game-state ::gs/score)
               (mod step-timestamp 1000000))
       (gs/state->string game-state)))

(defn active?
  [world-state]
  (= :active (-> world-state ::gs/game-state ::gs/status)))

;;; Game initialization
;;;;;;

(defn new-world
  ([game-state initial-timestamp]
   {::gs/game-state (assoc game-state ::gs/status :active)
    ::game-step 0
    ::requested-movements {}
    ::step-timestamp initial-timestamp})
  ([game-state] (new-world game-state (System/currentTimeMillis))))

(def ^:deprecated get-initial-world-state new-world)
;;; Game execution
;;;;;;

(s/fdef compute-new-state
  :args (s/cat :world-state ::world-state)
  :ret ::world-state
  :fn (s/and
       (fn [{{:keys [world-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step world-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn compute-new-state
  "Computes the new state derived from running a step of the
  game. Executes movements until none is left or game is over."
  [{:as world-state, :keys [::requested-movements]}]
  (-> world-state
      (update ::gs/game-state
              (partial u/reduce-until #(not= (::gs/status %) :active) ge/move-being)
              requested-movements)
      (assoc ::requested-movements {})
      (update ::game-step inc)))

(defn run-step
  "Runs a step of the world."
  [world-state-atom logging-steps]
  (swap! world-state-atom
         (comp #(assoc % ::step-timestamp (System/currentTimeMillis))
               compute-new-state))

  ;; Log every logging-steps steps, or never if 0
  (when (and (pos? logging-steps)
             (zero? (mod (@world-state-atom ::game-step) logging-steps)))
    (log/warnf "Step %s, timestamp %d"
               (@world-state-atom ::game-step)
               (::step-timestamp @world-state-atom))
    (log/info (data->string @world-state-atom))))

(defn world
  "Get a world given board `size`, and `seed`"
  [size seed]
  (new-world
   (first (gg/generate-game-states 1 size seed))))
