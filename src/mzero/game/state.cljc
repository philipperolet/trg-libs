(ns mzero.game.state
  "Defines game state--a board, the player's position, enemy positions,
  a score and a status. Status can be:
  - initial
  - active: player and enemies can move
  - over: player has lost
  - won: player has won"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.set :as cset]
            [mzero.game.board :as gb]))


;; Game state
;;;;;;;;;;;;;

(defonce max-enemies 5)

(s/def ::player-position ::gb/position)

(s/def ::enemy-positions (s/coll-of ::gb/position :kind vector? :max-count max-enemies))

(s/def ::score nat-int?)

(defonce game-statuses #{:initial :active :over :won})

(s/def ::status game-statuses)

(defn- random-status-generator
  "Generates a random status that fits board specs, notably won status conditions"
  [board]
  (if (zero? (gb/count-cells board :fruit))
    (gen/return :won)
    (s/gen (cset/difference game-statuses #{:won}))))
  
(defn game-state-generator [size]
  (gen/bind
   (->> (gb/game-board-generator size)
        (gen/such-that #(s/valid? ::gb/game-board %)))
   (fn [board]
     (gen/hash-map
      ::gb/game-board (gen/return board)
      ::player-position (gen/vector (gen/choose 0 (dec size)) 2)
      ::score (s/gen ::score)
      ::enemy-positions (gen/bind
                         (gen/choose 0 max-enemies)
                         #(gen/vector (gen/vector (gen/choose 0 (dec size)) 2) %))
      ::status (random-status-generator board)))))

(s/def ::game-state
  (-> (s/keys :req [::gb/game-board
                    ::player-position
                    ::score
                    ::status
                    ::enemy-positions])
      (s/and
       (fn [{:keys [::player-position ::enemy-positions ::gb/game-board]}]
         (comment "player position & enemy positions should be inside board")
         (every?
          #(and (< (% 0) (count game-board))
                (< (% 1) (count game-board)))
          (conj enemy-positions player-position)))
               
         (fn [s] (comment "player should be on empty space")
           (= (-> s ::gb/game-board (get-in (::player-position s))) :empty))

         (fn [{:keys [::gb/game-board ::enemy-positions]}]
           (comment "enemies should not be on a wall")
           (every? #(not= (get-in game-board %) :wall) enemy-positions))

         (fn [{:keys [::player-position ::enemy-positions ::status]}]
           (comment "Game over if player and enemy on same position,
           except if game is won")
           (or (= status :over)
               (= status :won)
               (every? #(not= % player-position) enemy-positions)))
         
         (fn [{:keys [::gb/game-board ::status]}]
           (comment "Game is won if and only if no fruits are left.")
           (or (and (= (gb/count-cells game-board :fruit) 0)
                    (= status :won))
               (and (> (gb/count-cells game-board :fruit) 0)
                    (not= status :won)))))

       (s/with-gen #(gen/bind gb/test-board-size-generator game-state-generator))))

(s/def ::enemy-nb (s/int-in 0 max-enemies))

(s/fdef init-game-state
  :args (-> (s/cat :board ::gb/game-board
                   :enemy-nb ::enemy-nb)
            
            (s/and (fn [args] (comment "At least one fruit otherwise no game")
                     (> (gb/count-cells (:board args) :fruit) 0))))
  :ret ::game-state
  :fn (s/and
       (fn [{{:keys [::gb/game-board ::status]} :ret, {:keys [board]} :args}]
         (comment "Board and status correctly set")
         (and (identical? game-board board)
              (= status :initial)))
       (fn [{{:keys [::enemy-positions]} :ret,  {:keys [enemy-nb]} :args}]
         (comment "Enough enemies have been added")
         (= (count enemy-positions) enemy-nb))))


(defn- add-enemy-positions
  "Add enemies at 5 different positions on the board in the given order,
  avoiding enemy positions to be on the player"
  [enemy-nb board starting-position]
  (let [size (count board)
        middle-size (int (* size 0.5))
        base-positions [[middle-size middle-size]
                        [middle-size (+ 2 middle-size)]
                        [(+ 2 middle-size) middle-size]
                        [0 0]
                        [(dec size) (dec size)]]]
    (->> base-positions
        (map #(gb/find-in-board board #{:empty} %))
        (keep #(if (not= starting-position %) %))
        (cycle)
        (take enemy-nb)
        vec)))

(defn init-game-state
  "Initializes a game state with given board, player at top/center of board
  no enemies and null score."
  [board enemy-nb]
  (let [size (count board)
        starting-position (gb/find-in-board board #{:empty} (->> size (* 0.5) int (vector 5)))]
    {::score 0
     ::status :initial
     ::enemy-positions (add-enemy-positions enemy-nb board starting-position)
     ::player-position starting-position
     ::gb/game-board board}))

(defn- explore-cell
  [board size i value]
  (let [row (int (/ i size))
        positions-to-check
        [(-> row (* size) (+ (mod (inc i) size)))
         (-> row (* size) (+ (mod (dec i) size)))
         (-> i (- size) (mod (* size size)))
         (-> i (+ size) (mod (* size size)))]]
  (cond 
    (and (or (= value :empty) (= value :fruit))
         (some #(or (= (board %) :mark) (= (board %) :marked))
               positions-to-check))
    :mark

    (= value :mark)
    :marked

    :else
    value)))
    
;; No specs because generative testing is too expensive (and doesn't add much to case-based)
  
(defn enjoyable-game?
  "Returns true if no fruit, player or enemy is locked inside
  walls apart from the rest of the board. Does so by converting
  enemy positions to fruit and checking if all fruits can be
  reached from player position"
  [{:as state, :keys [::gb/game-board ::player-position ::enemy-positions]}]

  (let [size (count game-board)
        explorable-board
        (->> ;; turn all enemy positions to fruit
         (reduce #(assoc-in %1 %2 :fruit) game-board enemy-positions) 
         ;; start explo from player pos
         (#(assoc-in % player-position :mark))
         ;; flatten
         (reduce into)
         vec)]
    
    (loop [board explorable-board]
      ;; if no more exploration return true iff no fruit left
      (if (not (some #{:mark} board))
        (= ((frequencies board) :fruit) nil)
        
        ;; else go on exploring, marking new elements according
        ;; collapse to vector, since use of lazy seq adds a power to the complexity exponent
        ;; making the function very slow
        (recur (vec (map-indexed #(explore-cell board size %1 %2) board)))))))

;;; Conversion of state to Hiccup HTML
;;;;

(s/fdef get-html-for-state
  :args (s/cat :state ::game-state)
  :ret  (s/and vector?
               #(= (first %) :tbody)))

(defn- add-player-and-enemies-string
  [cell-index {:as state, :keys [::player-position ::enemy-positions]}]
  (let [enemy-nb (first (keep-indexed #(if (= %2 cell-index) %1) enemy-positions))]
    (cond
      enemy-nb (str ".enemy-" enemy-nb)
      (= player-position cell-index) ".player")))
    
(defn- get-html-for-cell
  "Generates html for a game board cell"
  [cell-index cell state]
  (-> (str "td." (name cell) (add-player-and-enemies-string cell-index state))
      keyword
      vector
      (conj {:key (str "mzero-" (cell-index 0) "-" (cell-index 1))})))

(defn- get-html-for-line
  "Generates html for a game board row"
  [row-index row {:as state, :keys [::player-position]}]
  (->> row ; for each cell of the row
       (map-indexed #(get-html-for-cell [row-index %1] %2 state))
       (concat [:tr {:key (str "mzero-" row-index)}])
       vec))

(defn get-html-for-state
  "Given a game state, generates the hiccup html to render it with reagent.

  E.g. for a game board [[:empty :empty] [:wall :fruit]] with player
  position [0 1] it should generate
  [:table [:tr [:td.empty] [:td.empty.player]] [:tr [:td.wall] [:td.fruit]]]"
  [{:keys [::gb/game-board ::player-position], :as state}]
  (->> game-board
       (map-indexed #(get-html-for-line %1 %2 state))
       (concat [:tbody])
       vec))

;;; Conversion of state to nice string display
;;;;;;;;;

(s/fdef state->string
  :args (s/cat :state ::game-state)
  :ret string?)

(def cell->char
  {:player \@
   :enemy \&
   :hborder \-
   :vborder \|
   :empty \space
   :fruit \o
   :cheese \x
   :wall \#
   :newline \newline})

(defn state->string
  "Returns a nice string representation of the game state"
  [{:keys [::gb/game-board ::player-position ::enemy-positions], :as state}]
  (->> game-board

       ;; put player (resp. enemy) keyword in cell at its position
       (#(assoc-in % player-position :player))
       (#(reduce (fn [val pos] (assoc-in val pos :enemy)) % enemy-positions))

       ;; add vertical border keywords on each row, with newlines
       (map #(into [:vborder] (conj % :vborder :newline)))

       ;; add horizontal border as rows before and after
       ((let [horizontal-border
              (conj (vec (repeat (+ (count game-board) 2) :hborder)) :newline)]
          #(into [horizontal-border] (conj (vec %) horizontal-border))))

       ;; convert to string
       (reduce into)
       (map cell->char)
       (apply str)))
       
