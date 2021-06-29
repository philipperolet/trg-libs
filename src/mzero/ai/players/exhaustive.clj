(ns mzero.ai.players.exhaustive
  "This player tries to go everywhere on the board by exploring all
  possible paths that do not lead to an already explored position. It
  maintains a board simulation in which walls & explored territory are
  marked (using cell value :fruit) so they are not explored multiple
  times."
  (:require [mzero.ai.player :refer [init-player Player]]
            [mzero.ai.world :as aiw]
            [mzero.game.board :as gb]
            [mzero.game.events :as ge]
            [mzero.game.state :as gs]
            [mzero.utils.utils :as u]))

(defn- wall-present?
  "Checks if a wall blocked the player on the path.

  In the context of iterate-exploration, there can be no
  walls on the path except on the last movevement, which there is iff
  current player position is != to initial position + path."
  [initial-position player-position path board-size]
  (let [freqs (frequencies path)
        movement-down (- (freqs :down 0) (freqs :up 0))
        movement-right (- (freqs :right 0) (freqs :left 0))]
    (not= player-position
          [(mod (+ (initial-position 0) movement-down) board-size)
           (mod (+ (initial-position 1) movement-right) board-size)])))

(defn- get-walk-from-to
  "Computes the player's path to walk from its current position-- the
  end of the last path it explored--to its next destination--the end
  of the new path to explore.

  First come back to last common position, then move forward in new
  path direction. If there was a wall it means the last step must be
  ignored when coming back (since player bumped on a wall)."
  [path1 path2 wall?]
  (let [backward-path
        (->> (u/remove-common-beginning path1 path2)
             reverse
             (map {:up :down :down :up :left :right :right :left})
             (#(if wall? (rest %) %)))
        
        forward-path
        (u/remove-common-beginning path2 path1)]
    
    (concat backward-path forward-path)))

(defn- mark-board
  "Return board switching :empty cells next to position (and thus to be
  explored) to :fruit"
  [board player-position]
  (let [mark-cell
        (fn [board pos]
          (if (= (get-in board pos) :empty)
            (assoc-in board pos :fruit)
            board))]
    
    (->> '(:up :down :left :right)
         (map #(ge/move-position player-position % (count board)))
         (reduce mark-cell board))))

(defn- update-path-stack
  [path-stack board player-position]
  (let [get-position-from-dir
        (fn [direction] (ge/move-position player-position direction (count board)))

        explorable-direction?
        (fn [direction]
          (= (get-in board (get-position-from-dir direction)) :empty))]
    
    (->> (filter explorable-direction? '(:up :down :right :left))
         (map #(concat (first path-stack) (list %)))
         (concat (rest path-stack)))))

(defn- iterate-exploration
  "Starts at the end of the last explored path (the first path in path
  stack). If the expected postion does not match the actual, then the
  end of the last path was a wall and we should unstack another path
  without adding paths/ marking. Otherwise, get new paths to explore
  from the last path, from all 4 directions, except those already
  explored, a.k.a already marked with a fruit--so most of the time 3
  new paths or less will be added."
  [{:as player
    :keys [initial-position]
    {:keys [path-stack board]} :exploration-data}
   player-position]
  (let [wall? (wall-present? initial-position player-position
                             (first path-stack) (count board))
        next-board (if wall? board (mark-board board player-position))
        next-stack (if wall?
                     (rest path-stack)
                     (update-path-stack path-stack board player-position))
        next-path (get-walk-from-to (first path-stack) (first next-stack) wall?)]
    
    (assoc player :exploration-data {:current-path next-path
                                     :board next-board
                                     :path-stack next-stack})))

(defn- update-player-with-movement
  "While there is a path to explore, pop the next direction. When the
  path is empty, get a new path to explore. Once the board is fully
  explored the game should have ended -- next movement becomes nil."
  [player world]
  (-> (if (empty? (-> player :exploration-data :current-path))
          (iterate-exploration player (-> world ::gs/game-state ::gs/player-position))
          player)
        ;; get next direction in current path
        (#(assoc % :next-movement (-> % :exploration-data :current-path first)))
        ;; pop it
        (update-in [:exploration-data :current-path] rest)))

(defrecord ExhaustivePlayer [initial-position exploration-data]
  Player

  (init-player
    [this opts
     {:as world, {:keys [::gs/player-position ::gb/game-board]} ::gs/game-state}]
    (assoc this
           :initial-position player-position
           :exploration-data {:board (-> (gb/empty-board (count game-board))
                                         (assoc-in player-position :fruit))
                              :current-path '()
                              :path-stack '(())}))

  (update-player
    [player world]
    (comment "If the last movement has not yet been executed, wait and
    do not make a movement request. Else, go through with update.")
    (let [movement-still-pending?
          (some? (-> world ::aiw/requested-movements :player))]
      (if movement-still-pending?
        (assoc player :next-movement nil)
        (update-player-with-movement player world)))))
