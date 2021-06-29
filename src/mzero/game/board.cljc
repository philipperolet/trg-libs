(ns mzero.game.board
  "Defines game board as a matrix of game cells, and provides utilities
  to work on it"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test.check.generators]))

(defonce min-board-size 5)
(defonce max-board-size 100)
(defonce max-test-board-size 20)

(s/def ::board-size (s/int-in min-board-size max-board-size))

(defonce game-cell-values #{:empty :wall :fruit :cheese})

(s/def ::game-cell game-cell-values)

(s/def ::game-line (s/every ::game-cell
                            :kind vector?
                            :min-count min-board-size
                            :max-count max-board-size
                            :gen-max max-test-board-size))

(def test-board-size-generator (gen/choose min-board-size max-test-board-size))

(defn game-board-generator
  "Returns a board generator favoring empty cells (more than 2/3)"
  [size]
  (-> (gen/one-of [(s/gen ::game-cell) (gen/return :empty) (gen/return :empty)])
      (gen/vector size)
      (gen/vector size)))

(def test-board-generator
  (gen/bind test-board-size-generator game-board-generator))

(s/def ::game-board
  (-> (s/every ::game-line
               :kind vector?
               :min-count min-board-size
               :max-count max-board-size)

      (s/and (fn [gb] (comment "lines and rows have same size")
               (every? #(= (count %) (count gb)) gb))
             (fn [gb] (comment "At least 2 empty cells")
               ;; counts cells without using  the funcion count-cells
               ;; to avoid cyclic reference since count-cells spec
               ;; relies on ::game-board
               (< 1 (->> gb (reduce into) (filter #(= % :empty)) count))))
      
      (s/with-gen (fn [] test-board-generator))))

(s/def ::position
  (s/tuple (s/int-in 0 max-board-size)
           (s/int-in 0 max-board-size)))

(s/fdef count-cells
  :args (s/cat :board ::game-board :value ::game-cell)
  :ret nat-int?
  :fn (fn [s] (comment "Less than the total number of cells")
        (<= (:ret s) (reduce * (repeat 2 (-> s :args :board count))))))

(defn count-cells
  "Number of cells with given value on given board"
  [board value]
  (->> board (reduce into) (filter #(= % value)) count))

(s/fdef empty-board
  :args (-> (s/cat :size ::board-size)
            (s/with-gen #(gen/vector test-board-size-generator 1)))
  :ret ::game-board
  :fn #(= (count-cells (:ret %) :empty) (reduce * (repeat 2 (-> % :args :size)))))

(defn empty-board
  [size]
  (vec (repeat size (vec (repeat size :empty)))))

(defn empty-board? [board] (every? #{:empty} (flatten board)))

(defn board-stats
  [board]
  (let [walls (count-cells board :wall) total-cells (* (count board) (count board))]

    {:total-cells total-cells

     :non-wall-cells (- total-cells walls)

     :density ;; map of each element's density on board (except wall)     
     (let [elts (remove #{:wall :empty} game-cell-values)
           compute-density
           #(-> (count-cells board %) (* 100) (/ (- total-cells walls)) int)]
       (zipmap elts (map compute-density elts)))}))

;;; For generic version, f should take as arg an element of coll and
;;; return an int, but specing that makes some valid functions not
;;; conform, e.g. `(first`. Thus no spec is given and a dummy
;;; generator is provided)")
(s/fdef get-closest
  :args (s/alt :ints (-> (s/cat :coll (s/coll-of int?)
                                :i int?)
                         ;; generator provided to avoid integer overflow
                         (s/with-gen #(gen/tuple (gen/vector (gen/choose -100 100))
                                                 (gen/choose -100 100))))
               :generic (s/cat :coll (s/coll-of any?)
                               :f (-> any? 
                                      (s/with-gen #(gen/return (fn [x] 0))))
                               :i int?))
  :ret any?
  :fn (fn [sp] (comment "Result actually belongs to collection")
        (or (nil? (:ret sp))
            (some #(= (:ret sp) %) (get-in sp [:args 1 :coll])))))

(defn get-closest 
  "Retrieves the element elt such that f(elt) is closest to i in coll.
  Returns nil for empty colls."
  ([coll f i]
   (when-not (empty? coll)
     (apply min-key #(- (max (f %) i) (min (f %) i)) coll)))
  ([coll i]
   (get-closest coll identity i)))

(s/fdef find-in-board
  :args (-> (s/cat :board ::game-board
                   :cell-set (s/coll-of ::game-cell :kind set?)
                   :position (s/? ::position))
            (s/with-gen (fn [] (gen/tuple
                                test-board-generator
                                (gen/one-of [(gen/return #{:fruit})
                                             (gen/return #{:wall})
                                             (gen/return #{:empty})])))))
  :ret (s/or :pos ::position :nil nil?)
  :fn #(or nil?
           (apply (-> % :args :cell-set)
                  (list (get-in (-> % :args :board) (:ret %))))))

(defn find-in-board
  "Finds the closest cell in board whose value is in cell-set,
  traversing the board from the given position (defaults to [0 0]),
  line by line. Returns nil if no result."
  ([board cell-set position]
   (->> board
       (keep-indexed
        (fn [ind line] ;; gets the first matching position in this line
          (->> line
               (keep-indexed #(when (cell-set %2) %1))
               (#(get-closest % (position 1)))
               (#(when % (vector ind %))))))
       (#(get-closest % first (position 0)))))
  
  ([board cell-set]
   (find-in-board board cell-set [0 0])))
