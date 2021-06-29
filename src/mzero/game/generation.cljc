(ns mzero.game.generation
  "Tools for generating nice boards."
  (:require #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.properties]])
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.data.generators :as g]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]
            [mzero.game.events :as ge]))

;; Wall generation
;;;;;;;;

(defn generate-wall
  "Generates a random wall of given length for a given board size.

  It will favor lines in the wall by sampling next direction from
  a distribution favoring previous direction."
  [board-size length]
  ;; random position
  [[(g/uniform 0 board-size) (g/uniform 0 board-size)]
   ;; random directions length times
   (take length (iterate
                 #(g/rand-nth (-> ge/directions (conj %) (conj %)))
                 (g/rand-nth ge/directions)))])

(def wall-generator
  (gen/fmap #(apply generate-wall %)
            (gen/bind (gen/choose gb/min-board-size gb/max-test-board-size)
                      #(gen/tuple (gen/return %) (gen/choose 1 %)))))

(s/def ::wall
  #_("A wall is defined by a starting position and
  directions on which the wall is defined, e.g. 
  [[0 1] [:right :right :up]] describes a wall on [0 1] 
  [0 2] [0 3] and [0 4])")
  (-> (s/tuple ::gb/position (s/coll-of ::ge/direction :min-count 1))
      (s/with-gen (fn [] wall-generator))))

(s/fdef generate-wall
  :args (s/and (s/cat :board-size ::gb/board-size
                      :length (s/int-in 1 gb/max-board-size))
               #(<= (:length %) (:board-size %)))
  :ret ::wall
  :fn (s/and #(= (count (get-in % [:ret 1])) (-> % :args :length))
             #(every? (fn [x] (< x (-> % :args :board-size))) (get-in % [:ret 0]))))

(s/fdef add-wall
  :args (s/and (s/cat :board ::gb/game-board :wall ::wall)
               (fn [args] (every? #(< % (count (-> args :board)))
                                  (get-in args [:wall 0]))))
  :ret ::gb/game-board)
  
(defn add-wall
  "Adds a wall to a board"
  [board [position directions :as wall]]  
  (first (reduce (fn [[brd pos] dir] ;; for each direction update board and position
                   (let [new-pos (ge/move-position pos dir (count brd))]
                     [(assoc-in brd pos :wall) new-pos]))
                 [board position]
                 (conj directions :up)))) ;; add a dummy direction to add the last wall


;;; Adding other elements to board
;;;;;;;;;

(s/fdef add-n-elements-random
  :args (-> (s/cat :board ::gb/game-board
                   :element (s/and ::gb/game-cell #(not= % :empty))
                   :n pos-int?)
            (s/and (fn [args]
                     (comment "At least n+2 empty cells so 2 empty cells remain")
                     (>= (gb/count-cells (:board args) :empty) (+ 2 (:n args))))))
  :ret ::gb/game-board
  :fn (fn [{:keys [ret] {:keys [board element n]} :args}]
        (comment "Exactly n more element")
        (= (gb/count-cells ret element)
           (+ n (gb/count-cells board element)))))

(defn add-n-elements-random
  "Adds n elements at random on the board"
  [board element n]
  {:pre [(>= (gb/count-cells board :empty) (inc n))]}
  (->> board
       ;; get all empty positions in the board
       (map-indexed (fn [ind line] (keep-indexed #(when (= %2 :empty) [ind %1]) line)))
       (reduce into)
       ;; sample n positions
       (g/shuffle)
       (take n)
       ;; put element at these positions on the board
       (reduce #(assoc-in %1 %2 element) board)))

(s/def ::element-density (-> (s/int-in 0 99)
                             (s/with-gen #(gen/choose 0 30))))

(defn valid-density
  "Checks the density of element is within normal bounds for board"
  [board element density]
  (let [elt-cells (gb/count-cells board element)
              non-wall-cells ((gb/board-stats board) :non-wall-cells)]
          (<= (int (* (/ elt-cells non-wall-cells) 100))
              density
              (int (* (/ (inc elt-cells) non-wall-cells) 100)))))

(defn sum-of-densities
  [board]
  (reduce + (vals (:density (gb/board-stats board)))))

(s/fdef sow-by-density
  :args (-> (s/cat :board ::gb/game-board
                   :element (s/and ::gb/game-cell  (complement #{:wall :empty}))
                   :desired-density ::element-density)
            (s/and
             (fn [{:keys [board element desired-density]}]
               (comment "actual density should be less than desired density")
               (<= (-> board gb/board-stats :density element)
                  desired-density))
             (fn [{:keys [board element desired-density]}]
               (comment "sum of densities should be < 99 (100 not enough because of rounding)")
               (< (-> (sum-of-densities board)
                      (- (-> board gb/board-stats :density element))
                      (+ desired-density))
                  99))))

  :ret ::gb/game-board
  
  :fn (fn [{ret :ret, {:keys [element desired-density]} :args}]
        (comment "Checks ratio of element on board fits density.")
        (valid-density ret element desired-density)))

(defn sow-by-density
  "Sows fruits on the board randomly according to density percentage :
  the ratio of fruits in cells will be closest to density/100. Density
  should be in [1,50[. Walls are excluded from the count, existing fruits
  on initial board are taken into account in density computation."
  [board element desired-density]
  {:pre [(<= (-> board gb/board-stats :density element) desired-density)]}
  (let [non-wall-cells ((gb/board-stats board) :non-wall-cells)
        prior-density (-> board gb/board-stats :density element)
        incremental-density (- desired-density prior-density)
        nb-elts-to-sow (-> incremental-density (* non-wall-cells) (/ 100) int)]
    
    (if (> nb-elts-to-sow 0)
      (add-n-elements-random board element nb-elts-to-sow)
      board)))


(defn sow-path
  "Sows `element` on `board` along `path`, starting at `position`,
  excluding position"
  [board element position path]
  (let [sow-one-elt
        (fn [[brd pos] direction]
          (let [new-pos (ge/move-position pos direction (count board))]
            (vector (assoc-in brd new-pos element) new-pos)))]
    (first (reduce sow-one-elt [board position] path))))

;;; Nice board
;;;;;;

;; map of densities for non-walls (and non-empty)
(s/def ::density-map (s/map-of (s/and ::gb/game-cell (complement #{:wall :empty}))
                               ::element-density
                               :distinct true))

;; density for walls, see create-nice-board documentation
(s/def ::wall-density (-> (s/int-in 0 66)
                          (s/with-gen #(gen/choose 0 50))))

(defn- add-random-wall
  "Adds a wall of random length between 1 and board size, biased
  towards average-length wall. The bias is introduced by averaging
  five random lengths"
  [board]
  (let [board-size (count board)
        rand-lengths (repeatedly 5 #(inc (g/uniform 0 (dec board-size))))
        wall-length (int (/ (reduce + rand-lengths) 5))]
    (add-wall board (generate-wall board-size wall-length))))

(s/def ::level (s/keys
                :req [::density-map]
                :opt [::wall-density]
                :opt-unq [:enemies :message :message-color]))

(s/fdef create-nice-board
  :args (-> (s/alt :unseeded
                   (s/cat :size ::gb/board-size
                          :level ::level)
                   :seeded
                   (s/cat :size ::gb/board-size
                          :level ::level
                          :seed (s/or :non-nil nat-int? :nil nil?)))
            (s/with-gen #(gen/tuple
                          gb/test-board-size-generator
                          (s/gen (s/keys :req [::density-map])))))
  :ret ::gb/game-board)

(defn create-nice-board
  "Creates a board with randomly generated walls, fruit, cheese, etc. as
  specified by the level's density map and wall-density.

  While most elements are generated using sow-by-density (with a
  default value of 0), wall generation is handled differently--thus
  the separate map key in levels. Custom generation is done via
  add-random-wall, favoring walls of length ~board size (looking
  better). The default is to add as much random walls as the size of
  the board, so about half the board is walled--corresponding to a
  density of 50. Max density is set to 66, since too much walls
  prevent an enjoyable game. The density is not exact, since it is
  converted in a finite number of randomly-sized walls.

  Non-nil `seed` allows for repeatable randomness."
  ([size level seed]
   (binding [g/*rnd* (if seed (java.util.Random. seed) g/*rnd*)]
     (let [nb-of-walls (int (/ (* size (level ::wall-density 50)) 100))]
       (-> (gb/empty-board size)
           (as-> board (iterate add-random-wall board))
           (nth nb-of-walls)
           (#(reduce-kv sow-by-density % (-> level ::density-map)))))))
  ([size level]
   (create-nice-board size level nil)))

(defn create-nice-game
  "Creates a game state that is 'enjoyable', see
  state/enjoyable-game?. Non-nil `seed` allows for repeatable
  randomness. Note that while the board is randomly generated, the
  player position is fixed (see gs/init-game-state)."
  ([size level seed]
   (->> #(gs/init-game-state (create-nice-board size level seed)
                             (count (:enemies level [])))
        repeatedly
        (filter gs/enjoyable-game?)
        first))
  ([size level]
   (create-nice-game size level nil)))

(defn generate-game-states
  "Generate a coll of `nb-states` game states of given `board-size`,
  optionally with a `seed` to get always the same coll (in which case
  it is coerced to be evaluated while in the binding
  context). Checking games are `enjoyable?` is optional and not
  default because enjoyable-game detection can take a very long time."
  ([nb-states board-size seed enjoyable?]
   (let [level {::density-map {:fruit 5}}
         generate-game-state
         (fn [new-seed]
           (binding [g/*rnd* (java.util.Random. new-seed)]
             (if enjoyable?
               (create-nice-game board-size level)
               (gs/init-game-state (create-nice-board board-size level) 0))))
         seeds-list
         (binding [g/*rnd* (if seed (java.util.Random. seed) (java.util.Random.))]
           (vec (repeatedly nb-states g/int)))]
     (pmap generate-game-state seeds-list)))
  
  ([nb-states board-size seed]
   (generate-game-states nb-states board-size seed false))
  
  ([nb-states board-size]
   (generate-game-states nb-states board-size nil false)))

