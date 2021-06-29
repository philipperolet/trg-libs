(ns mzero.ai.players.exhaustive-test
  (:require [clojure.test :refer [is are testing]]
            [mzero.utils.testing :refer [check-all-specs deftest]]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.game.generation :as gg]
            [mzero.game.events :as ge]
            [mzero.ai.players.exhaustive :as sut]
            [mzero.ai.player :as aip]
            [mzero.ai.world :as aiw]))

(check-all-specs mzero.ai.players.exhaustive)

(deftest get-walk-from-to-test
  (are [p1 p2 wall? res]
      (= res (#'sut/get-walk-from-to p1 p2 wall?))
    [:up :left] [:up :right] false '(:right :right)
    [:down :down :left :left] [:down :down :right :up] true '(:right :right :up)))

(def base-board (-> (gb/empty-board 5)
                    (assoc-in [3 3] :fruit)))

(def test-board (#'sut/mark-board base-board [3 3]))

(def test-board-2 (-> test-board
                         (#'sut/mark-board [3 4])
                         (#'sut/mark-board [4 3])))

(deftest mark-board-test
  (is (every? #{:fruit} (map #(get-in test-board %) [[3 4] [4 3] [3 2] [2 3]])))
  (is (every? #{:empty} (map #(get-in test-board %)
                             [[3 0] [0 3] [0 0] [2 2] [4 2] [2 4]])))
  (is (every? #{:fruit} (map #(get-in test-board-2 %)
                              [[3 4] [4 3] [3 2] [2 3]
                               [4 4] [3 0] [0 3] [4 2] [2 4]])))
  (is (every? #{:empty} (map #(get-in test-board %) [[4 0] [0 4] [0 0] [1 1]]))))

(deftest update-stack-test
  (are [stack board pos res]
      (= res (#'sut/update-path-stack stack board pos))

    '() (gb/empty-board 5) [3 3] '((:up) (:down) (:right) (:left))

    '((:left :up) (:down :down)) test-board [3 4]
    '((:down :down) (:left :up :up) (:left :up :down) (:left :up :right))))

(deftest wall-present-test
  (are [init-pos player-pos path res]
      (= res (#'sut/wall-present? init-pos player-pos path 5))
    [0 0] [1 1] '(:down :right) false
    [0 0] [1 0] '(:down :right) true
    [1 0] [2 1] '(:left :up :right :right :down :down) false
    [0 1] [3 3] '(:down :right :up :up :up :right) false
    [0 1] [4 4] '(:down :right :up :up :up :right) true))

(deftest iterate-exploration-test
  (are [player pos res]
      (= res (:exploration-data (#'sut/iterate-exploration player pos)))
    
    {:initial-position [3 3]
     :exploration-data {:current-path '()
                        :board base-board
                        :path-stack '(())}}
    [3 3]
    {:current-path '(:up)
     :board test-board
     :path-stack '((:up) (:down) (:right) (:left))}
    ;;;;;;;;;
    {:initial-position [3 3]
     :exploration-data {:current-path '()
                        :board test-board
                        :path-stack '((:up) (:down) (:right) (:left))}}
    [2 3]
    {:current-path '(:down :down)
     :board (#'sut/mark-board test-board [2 3])
     :path-stack '((:down) (:right) (:left) (:up :up) (:up :right) (:up :left))}))

(deftest update-player-state-test
  (let [world
        (aiw/get-initial-world-state
         (-> (gg/create-nice-game 8 {::gg/density-map {:fruit 10}})
             ;; ensures up of player is empty for this test board
             (#(update % ::gb/game-board
                       assoc-in (update (% ::gs/player-position) 0 dec) :empty))))
        player
        (aip/load-player "exhaustive" {} world)
        next-player
        (aip/update-player player world)
        next-world
        (update-in world [::gs/game-state ::gs/player-position]
                   ;; move-position may be a macro
                   (fn [pos dir bs] (ge/move-position pos dir bs))
                   (-> next-player :next-movement) 8)
        next-player-2
        (aip/update-player next-player next-world)
        next-world-2 ;; world with pending movement not yet executed
        (assoc-in next-world [::aiw/requested-movements :player] :down)]
    (is (= :up (-> next-player :next-movement)))
    (is (= '() (-> next-player :exploration-data :current-path)))
    (is (= :down (-> next-player-2 :next-movement)))
    (is (= '(:down) (-> next-player-2 :exploration-data :current-path)))

    (testing
        "if movement has not yet been executed, do not make movement requests"

      (is (nil? (:next-movement (aip/update-player next-player-2 next-world-2)))))))
