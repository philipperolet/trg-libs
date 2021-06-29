(ns experiments
  "Experiments.

  WARNING: xp code written here SHOULD ONLY BE EXPECTED TO WORK WHEN
  IT WAS COMMITTED. They MAY work on the whole xp branch to which they
  belong, and probably won't work elsewhere. This is by design, this
  code is not meant to be maintained."
  (:require [mzero.ai.main :as aim]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]
            [mzero.utils.utils :as u]
            [mzero.utils.xp :refer [measure measures-string]]))

(defn te-impl-speed-single-run
  [board-size nb-xps node-type & tunings]
  (let [board-size (read-string board-size)
            nb-xps (read-string nb-xps)
            player-opts
            (reduce #(assoc-in %1 [:tuning (keyword %2)] true)
                    {:node-constructor node-type
                     :seed 42
                     :tuning {}}
                    tunings)
            option-string
            (format "-v WARNING -t tree-exploration -o '%s' -n 50000" player-opts)
            measure-fn 
            #(vector (-> % second :world ::aiw/game-step))
            timed-go
            (fn [world]
              (-> (u/timed (aim/go option-string world))
                  (#(or (prn (measure-fn %)) %))))
            random-worlds
            (map (comp list aiw/get-initial-world-state)
                 (gg/generate-game-states nb-xps board-size 41 true))
            measures
            (u/timed (measure timed-go measure-fn random-worlds map))
            timing
            (first measures)]
    (print (measures-string (second measures)
                            (str node-type " " tunings)
                            "Steps"))
    (println "Time: " timing)
    (println "Time (Average per game): " (/ timing nb-xps) "\n\n")))

(defn te-impl-speed
  "Experiment with 3 node types for tree-exploration: te-node, dag-node,
  java-dag-node. For each, tries no tuning, random-min tuning,
  wall-fix, and both.

  For dag-node either wall-fix or random-min is kept on since
  otherwise it may block."
  [board-size nb-xps]
  (println "Te-impl-speed Experiment - " (java.util.Date.))
  (println "Board size " board-size)
  (println "Nb xps per param " nb-xps "\n\n")
  (doseq [node-type ["tree-exploration/te-node"
                     "java-dag/java-dag-node"
                     "dag-node/dag-node"]
          tunings [[] ["wall-fix"] ["random-min"] ["wall-fix" "random-min"]]]
    (when (not (and (= node-type "dag-node/dag-node") (empty? tunings)))
      (apply te-impl-speed-single-run board-size nb-xps node-type tunings))))
