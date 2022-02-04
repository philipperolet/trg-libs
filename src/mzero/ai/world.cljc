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
            [mzero.utils.commons :as c]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            #?(:clj [clojure.tools.logging :as log])
            #?(:clj [clojure.data.generators :as g]
                :cljs [mzero.utils.cdg :as g])
            [mzero.game.generation :as gg]))

;;; Full game state spec & helpers
;;;;;;;

(s/def ::game-step nat-int?)

;; timestamp in ms of current step start
(s/def ::step-timestamp nat-int?)

(s/def ::requested-movements ::ge/movements-map)

(s/def ::recorded-score ::gs/score)

(s/def ::next-levels (s/every ::gs/game-state
                              :max-count gg/max-levels
                              :gen #(gen/vector (s/gen ::gs/game-state) 0 2)))

(defn- world-state-predicate-matcher
  "Generator helper to match ::world-state constraints"
  [world-state]
  (-> world-state
      (assoc ::requested-movements
             (c/filter-keys
              #(or (= % :player)
                   (< % (count (get-in world-state
                                       [::gs/game-state ::gs/enemy-positions]))))
              (world-state ::requested-movements)))))

(def world-state-keys-spec
  (s/keys :req [::gs/game-state
                ::game-step
                ::requested-movements
                ::step-timestamp]          
          :opt [::next-levels
                ::recorded-score]))

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
  (str (format "Step %d\nScore %.2f\nTimestamp (mod 1 000 000) %d"
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
    ::recorded-score 0
    ::requested-movements {}
    ::step-timestamp initial-timestamp})
  ([game-state] (new-world game-state (c/currTimeMillis))))

(def ^:deprecated get-initial-world-state new-world)
;;; Game execution
;;;;;;

(s/fdef compute-new-state
  :args (-> (s/cat :world-state ::world-state)
            (s/and (fn [{:keys [world-state]}] (active? world-state))))
  :ret ::world-state
  :fn (s/and
       (fn [{{:keys [world-state]} :args, :keys [ret]}]
         (comment "Step increases")
         (= (::game-step ret) (inc (::game-step world-state))))
       (fn [{:keys [:ret]}]
         (comment "Movements cleared")
         (empty? (::requested-movements ret)))))

(defn remaining-levels? [world] (seq (-> world ::next-levels)))

(defn update-to-next-level
  [{:as world :keys [::gs/game-state ::next-levels]}]
  (cond-> world
    (and (= (-> game-state ::gs/status) :won) (remaining-levels? world))
    (assoc ::gs/game-state (assoc (first next-levels)
                                  ::gs/status :active
                                  ::gs/score (-> game-state ::gs/score))
           ::next-levels (rest next-levels))))

(defn update-score-given-status
  [{:as world :keys [::gs/game-state ::recorded-score]}]
  (case (-> world ::gs/game-state ::gs/status)
    :won (assoc world ::recorded-score (-> game-state ::gs/score))
    :over (assoc-in world [::gs/game-state ::gs/score] recorded-score)
    world))

(defn compute-new-state
  "Compute the new state derived from running a step of the
  game."
  [{:as world-state, :keys [::requested-movements]}]
  (-> world-state
      (update ::gs/game-state ge/game-step requested-movements)
      (assoc ::requested-movements {})
      (update ::game-step inc)
      update-score-given-status))

(defn run-step
  "Runs a step of the world."
  [world-state-atom logging-steps]
  (swap! world-state-atom
         (comp #(assoc % ::step-timestamp (c/currTimeMillis))
               compute-new-state))

  ;; Log every logging-steps steps, or never if 0
  (when (and (pos? logging-steps)
             (zero? (mod (@world-state-atom ::game-step) logging-steps)))
    (#?(:clj log/warnf :cljs (constantly nil))
     "Step %s, timestamp %d"
     (@world-state-atom ::game-step)
     (::step-timestamp @world-state-atom))
    (#?(:clj log/warnf :cljs (constantly nil))
     (data->string @world-state-atom))))

(defn world
  "Get a world given board `size`, and `seed`"
  [size seed & args]
  (new-world
   (first (apply gg/generate-game-states 1 size seed args))))

(defn multilevel-world
  [size seed [level & levels]]
  (let [seeds
        (binding [g/*rnd* (c/get-rng seed)]
          (vec (repeatedly (count levels) g/int)))
        generate-level
        (fn [s l] (first (gg/generate-game-states 1 size s true l)))]
    (-> (world size seed true level)
        (assoc ::next-levels (map generate-level seeds levels)))))
