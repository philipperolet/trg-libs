(ns mzero.ai.players.java-dag
  (:require [mzero.game.events :as ge]
            [mzero.game.board :as gb]
            [mzero.ai.players.tree-exploration :as te]
            [clojure.data.generators :as g]))

(defn- get-node-to-aggregate [direction1 direction2 children]
  (let [opposed-directions {:up :down, :down :up, :right :left, :left :right}]
    (-> children
        direction1
        (te/get-descendant [(opposed-directions direction1) direction2]))))

(defn- compute-merged-node-in-direction [direction children]
  (let [compute-frequency
        (fn [child-dir]
          (reduce
           #(+ %1 (or (te/frequency (get-node-to-aggregate %2 child-dir children)) 0))
           0 ge/directions))
        compute-value
        (fn [child-dir]
          (reduce #(min %1 (or (te/value (get-node-to-aggregate %2 child-dir children)) ##Inf))
                  ##Inf
                  ge/directions))]
    (te/map->TreeExplorationNodeImpl
     {::te/frequency (compute-frequency direction)
      ::te/value (compute-value direction)})))

(defrecord JavaDagImpl [values frequencies position board-size]
  te/TreeExplorationNode

  (append-child [this direction]
    (assert false "Should not be called."))
  
  (-value [{:keys [values], [x y] :position}]
    (aget ^doubles (aget ^"[[D" values ^int x) ^int y))
  (-assoc-value [{:as this, :keys [values], [x y] :position} val_]
    (aset ^doubles (aget ^"[[D" values ^int x) ^int y ^double val_)
    this)
  (-frequency [{:keys [frequencies], [x y] :position}]
    (aget ^doubles (aget ^"[[D" frequencies ^int x) ^int y))
  (-assoc-frequency [{:as  this, :keys [frequencies], [x y] :position} freq]
    (aset ^doubles (aget ^"[[D" frequencies ^int x) ^int y ^double freq)
    this)
  (min-direction [{:as this, :keys [position board-size]} sort-key]
    (let [get-value-from-direction
          #(sort-key (te/get-child this %))]
      ;; random selection among multiple mins, better performance
      ;; see te-speed-impl xp in v0.2.2 (random-min flag)
      (apply min-key get-value-from-direction (g/shuffle ge/directions))))
  
  (-children [{:as this, :keys [position board-size]}]
    (let [add-child-from-direction
          #(let [child (te/get-child this %2)]
             (assoc %1 %2
                    (te/map->TreeExplorationNodeImpl
                     {::te/value (te/value child)
                      ::te/frequency (te/frequency child)})))]
      (add-child-from-direction
       (add-child-from-direction
        (add-child-from-direction
         (add-child-from-direction {} :up)
         :right)
        :down)
       :left)))

  (get-child [this direction]
    (update this :position
            #(ge/move-position % direction (-> this :board-size))))
  
  (update-children-in-direction [this direction f]
    (let [next-node
          (->JavaDagImpl
           (-> this :values)
           (-> this :frequencies)
           (ge/move-position (-> this :position) direction (-> this :board-size))
           (-> this :board-size))]
      (f next-node)
      this))

  (-create-root-node [this children]
    (assoc (te/->TreeExplorationNodeImpl)
           ::te/value 0
           ::te/frequency Integer/MAX_VALUE
           ::te/children
           (reduce #(assoc %1 %2 (compute-merged-node-in-direction %2 children))
                   {}
                   ge/directions))))


(defn java-dag-node
  "DagNodeImpl constructor"
  [game-state]
  (let [board-size (count (::gb/game-board game-state))]
    (->JavaDagImpl
     (let [arr (make-array Double/TYPE board-size board-size)]
       (doseq [i (range board-size)
               j (range board-size)]
         (aset ^doubles (aget ^"[[D" arr ^int i) ^int j ##Inf))
       arr)
     (make-array Double/TYPE board-size board-size)
     [0 0]
     board-size)))
