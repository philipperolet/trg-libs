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
            [mzero.game.board :as gb]
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

(s/def ::next-levels (s/every ::gs/game-state
                              :max-count gg/max-levels
                              :gen #(gen/vector (s/gen ::gs/game-state) 0 2)))

(s/def ::levels-data (s/every ::gg/level))
(s/def ::current-level-start-step nat-int?)

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
          :opt [::next-levels]))

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
               (double (game-state ::gs/score))
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
    ::step-timestamp initial-timestamp
    ::current-level-start-step 0})
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

(defn current-level [world]
  (- (count (or (::levels-data world) "1")) (inc (count (::next-levels world)))))

(defn level-finished-bonus [world]
  (* (current-level world) (count (-> world ::gs/game-state ::gb/game-board))))

(defn update-to-next-level
  [{:as world :keys [::gs/game-state ::next-levels ::game-step]}]
  (cond-> world
    (and (= (-> game-state ::gs/status) :won) (remaining-levels? world))
    (assoc ::gs/game-state (assoc (first next-levels)
                                  ::gs/status :active
                                  ::gs/score (-> game-state ::gs/score))
           ::next-levels (rest next-levels)
           ::current-level-start-step game-step)))

(defn update-score-given-status
  [world]
  (if (= (-> world ::gs/game-state ::gs/status) :won)
    (update-in world [::gs/game-state ::gs/score] + (level-finished-bonus world))
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


(def enemies-wait-delay 15)

(def enemy-move-interval {:drink 8 :mouse 4 :virus 2})

(def slower-enemy-move-interval
  (reduce #(update %1 %2 * 5) enemy-move-interval (keys enemy-move-interval)))

(defn- indices-of-enemies-to-move
  ([game-step level-start-step enemies]
   (let [enemies-waited-enough? ;; at start of level, wait for a bit before moving
        (>= game-step (+ enemies-wait-delay level-start-step))
        index-of-enemy-to-move
        (fn [enemy-index enemy-type]
          (when (and enemies-waited-enough?
                     (= 0 (mod game-step (enemy-move-interval enemy-type))))
            enemy-index))]
     (keep-indexed index-of-enemy-to-move enemies)))
  ([{:as world :keys [::game-step ::current-level-start-step]}]
   (let [enemies
         (-> world ::levels-data (nth (current-level world)) :enemies)]
     (indices-of-enemies-to-move game-step current-level-start-step enemies))))

(defn request-enemies-movements [{:as world :keys [::gs/game-state]}]
  (let [request-enemy-movement
        (fn [requested-movements index]
          (assoc requested-movements index
                 (ge/move-enemy-random game-state index)))]
    (update world ::requested-movements
            (partial reduce request-enemy-movement)
            (indices-of-enemies-to-move world))))

(defn request-enemies-movements! [world-atom]
  (let [enemies-present?
        (-> @world-atom ::gs/game-state ::gs/enemy-positions count (> 0))]
    (when enemies-present?
      (swap! world-atom request-enemies-movements))))

(defn world
  "Get a world given board `size`, and `seed`"
  [size seed & args]
  (new-world
   (first (apply gg/generate-game-states 1 size seed args))))

(defn multilevel-world
  [size seed [first-level & next-levels :as levels]]
  (let [seeds
        (binding [g/*rnd* (c/get-rng seed)]
          (vec (repeatedly (count levels) g/int)))
        generate-level
        (fn [s l] (first (gg/generate-game-states 1 size s true l)))]
    (-> (world size seed true first-level)
        (assoc ::next-levels (map generate-level seeds next-levels)
               ::levels-data  levels
               ::current-level-start-step 0))))
