(ns mzero.game.state-test
  (:require [clojure.test :refer [testing is are]]
            [clojure.spec.alpha :as s]
            [mzero.utils.testing
             #?(:clj :refer :cljs :refer-macros) [check-all-specs deftest]]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]))

(check-all-specs mzero.game.state)

(def test-size 10)

(def test-state
  "A game with a test board of size 10, last line wall and before last
  line fruits, player at position [0 0]"
  (-> (gb/empty-board test-size)
      (assoc (- test-size 2) (vec (repeat test-size :fruit)))
      (assoc (- test-size 1) (vec (repeat test-size :wall)))
      (gs/init-game-state 0)
      (assoc ::gs/player-position [0 0])))

(def test-state-2
  {::gs/status :active
   ::gs/score 10
   ::gs/enemy-positions [[0 0] [1 4]]
   ::gb/game-board [[:empty :empty :wall :empty :empty]
                    [:empty :fruit :empty :empty :empty]
                    [:empty :empty :wall :empty :empty]
                    [:empty :empty :cheese :cheese :empty]
                    [:empty :empty :empty :empty :empty]]
   ::gs/player-position [1 2]})

(def test-state-2-string
  (str "-------" \newline
       "|& #  |" \newline
       "| o@ &|" \newline
       "|  #  |" \newline
       "|  xx |" \newline
       "|     |" \newline
       "-------" \newline))

(deftest board-spec-test
  (testing "Player should not be able to be on a wall"
    (is (not (s/valid? ::gs/game-state
                       (assoc test-state ::gs/player-position [9 9]))))))

(deftest get-html-for-state-t
  (testing "Converts appropriately a board to reagent html"
    (is (= [:tbody
            [:tr {:key "mzero-0"}
             [:td.empty.enemy-0 {:key "mzero-0-0"}]
             [:td.empty {:key "mzero-0-1"}]
             [:td.wall {:key "mzero-0-2"}]
             [:td.empty {:key "mzero-0-3"}]
             [:td.empty {:key "mzero-0-4"}]]
            [:tr {:key "mzero-1"}
             [:td.empty {:key "mzero-1-0"}]
             [:td.fruit {:key "mzero-1-1"}]
             [:td.empty.player {:key "mzero-1-2"}]
             [:td.empty {:key "mzero-1-3"}]
             [:td.empty.enemy-1 {:key "mzero-1-4"}]]
            [:tr {:key "mzero-2"}
             [:td.empty {:key "mzero-2-0"}]
             [:td.empty {:key "mzero-2-1"}]
             [:td.wall {:key "mzero-2-2"}]
             [:td.empty {:key "mzero-2-3"}]
             [:td.empty {:key "mzero-2-4"}]]
            [:tr {:key "mzero-3"}
             [:td.empty {:key "mzero-3-0"}]
             [:td.empty {:key "mzero-3-1"}]
             [:td.cheese {:key "mzero-3-2"}]
             [:td.cheese {:key "mzero-3-3"}]
             [:td.empty {:key "mzero-3-4"}]]
            [:tr {:key "mzero-4"}
             [:td.empty {:key "mzero-4-0"}]
             [:td.empty {:key "mzero-4-1"}]
             [:td.empty {:key "mzero-4-2"}]
             [:td.empty {:key "mzero-4-3"}]
             [:td.empty {:key "mzero-4-4"}]]]

           (gs/get-html-for-state test-state-2)))))

(deftest enjoyable-game-test
  (let [fully-accessible-board-1                     
        [[:wall :empty :fruit :wall :wall]
         [:wall :fruit :wall :empty :wall]
         [:wall :empty :empty :empty :empty]
         [:wall :wall :cheese :wall :wall]
         [:wall :wall :fruit :wall :wall]]
        fully-accessible-board-2
        [[:empty :empty :wall :empty :empty]
         [:empty :fruit :empty :empty :empty]
         [:empty :empty :wall :wall :wall]
         [:wall :empty :cheese :cheese :fruit]
         [:empty :empty :empty :wall :empty]]
        board-with-locked-player-or-enemies
        [[:empty :empty :wall :wall :empty]
         [:empty :wall :empty :empty :wall]
         [:empty :empty :wall :wall :wall]
         [:fruit :empty :cheese :cheese :fruit]
         [:empty :empty :empty :wall :empty]]
        board-with-locked-fruit
        [[:empty :empty :wall :empty :empty]
         [:empty :fruit :empty :empty :empty]
         [:empty :empty :wall :wall :wall]
         [:wall :empty :cheese :cheese :fruit]
         [:empty :empty :empty :wall :wall]]
        player-and-enemy-positions
        [[[0 1] [2 1] [1 3]]
         [[1 3] [0 1] [2 1]]
         [[2 1] [1 3] [0 1]]]
        create-state-from-positions
        (fn [b pos]
          (-> (gs/init-game-state b 0)
              (assoc ::gs/player-position (first pos))
              (assoc ::gs/enemy-positions (vec (rest pos)))))]
        
    (is (every? gs/enjoyable-game?
                (map #(create-state-from-positions (first %) (second %))
                     (for [board [fully-accessible-board-1
                                  fully-accessible-board-2]
                           pos player-and-enemy-positions]
                       [board pos]))))
    (is (not-any? gs/enjoyable-game?
                (map #(create-state-from-positions (first %) (second %))
                     (for [board [board-with-locked-player-or-enemies
                                  board-with-locked-fruit]
                           pos player-and-enemy-positions]
                       [board pos]))))
    (is (gs/enjoyable-game? (create-state-from-positions
                          board-with-locked-player-or-enemies
                          [[0 0] [0 1] [1 0]])))
    (is (not (gs/enjoyable-game? (create-state-from-positions
                               board-with-locked-fruit
                               [[0 0] [0 1] [1 0]]))))))    
    
(deftest state->string-test
  (is (= test-state-2-string (gs/state->string test-state-2))))
