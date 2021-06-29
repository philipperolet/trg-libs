(ns xp1000
  "Legacy. Run 1000 times games with 2 types of player and compares the results,
  giving the good stats."
  (:require [mzero.ai.main :as aim]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]
            [mzero.utils.utils :as u]
            [mzero.utils.testing :refer [count-calls]]
            [mzero.ai.players.tree-exploration :as te]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.ai.player :as aip]
            [mzero.game.board :as gb]
            [clojure.string :as str]
            [mzero.utils.xp :refer [measure measures-string]]))


(defn -step-avg-xp [nb-xps player-type]
  (let [game-args (str  "-s 20 -v WARNING -t " player-type)]
    (print (measures-string
            (measure #(aim/go game-args)
                    (comp ::aiw/game-step :world)
                    (repeat (Integer/parseInt nb-xps) []))
            game-args))))

(defn -fastest-te-impl
  "A subsim is ~ an atomic operation in the tree exploration,
  somewhat similar to one random move. The speed we wish to measure is
  subsim/sec"
  [board-size constr nb-xps]
  (let [game-args
        (format "-n 10 -o '{:node-constructor %s :nb-sims 100}'" constr)
        timed-go
        (fn [world]
          (with-redefs [ge/move-player (count-calls ge/move-player)]
            (-> (aim/go (str "-v WARNING -t tree-exploration " game-args) world)
                u/timed
                (conj ((:call-count (meta ge/move-player)))))))
        measure-fn
        #(vector (/ (last %) (/ (first %) 1000))
                 (last %))        
        random-worlds ;; seeded generation of game states, always same list
        (map (comp list aiw/get-initial-world-state)
             (gg/generate-game-states nb-xps board-size 41))]
    
    (print (measures-string (measure timed-go measure-fn random-worlds map)
                            game-args
                            "Tree exploration op/s"))))

(defn -compare-sht
  "For random, an op is just the number of steps played"
  [board-size nb-xps]
  (doseq [player-type ["exhaustive" "random" "tree-exploration"]]
    (let [timed-go
          #(u/timed (aim/go (str "-v WARNING -l 200000 -t " player-type) %))
          measure-fn 
          #(vector (first %)
                   (-> % second :world ::aiw/game-step))
          random-worlds
          (map (comp list aiw/get-initial-world-state)
               (gg/generate-game-states nb-xps board-size 41 true))]
      (print (measures-string (measure timed-go measure-fn random-worlds map)
                              player-type
                              "1.Time 2.Steps /")))))

(defn -runcli [& args]
  (apply (resolve (symbol (str "xp1000/" (first args))))
         (map read-string (rest args))))

(def node-path (atom []))

(defn run-simulation []
  (reset! node-path [])
  (let [state (-> @aim/curr-game :world ::gs/game-state)
        copied-state (atom state)
        
        tree-sim te/tree-simulate]
    (with-redefs [gs/cell->char
                  (assoc gs/cell->char :path \+)
                  te/tree-simulate
                  (fn [tn gs ss]
                    (swap! copied-state
                           assoc-in
                           (into [::gb/game-board] (-> gs ::gs/player-position))
                           :path)
                    (swap! node-path conj
                           (let [updated (#'te/update-children tn)]
                             (-> updated
                                 te/node-path
                                 (assoc :d (te/min-direction updated te/frequency)))))
                    (tree-sim tn gs ss))]
      (swap! aim/curr-game
             update-in [:player :root-node]
             te/tree-simulate state (#'te/max-sim-size state))
      (println (gs/state->string @copied-state)))))
