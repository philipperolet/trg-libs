(ns mzero.game.events-test
  (:require [clojure.test :refer [testing is are]]
            [mzero.utils.testing
             #?(:clj :refer :cljs :refer-macros) [check-all-specs deftest]]
            [mzero.game.board :as gb]
            [mzero.game.state :as gs]
            [mzero.game.events :as ge]
            [mzero.game.state-test :as gst]))


(check-all-specs mzero.game.events)

(defonce test-state (assoc gst/test-state ::gs/status :active))

(deftest move-position-t
  (are [x d y] (= y (ge/move-position x d 7))
    [0 0] :up [6 0]
    [0 0] :down [1 0]
    [0 0] :left [0 6]
    [0 0] :right [0 1]
    [6 6] :up [5 6]
    [6 6] :left [6 5]))

(deftest move-player-basic
  (testing "Moves correctly up, down, right, left on canonical
    board (see create game)"
    (let [test-state (assoc test-state ::gs/player-position [1 0])]
      (are [x y] (= x (::gs/player-position (ge/move-player test-state y)))
        [0 0] :up
        [1 1] :right
        [2 0] :down
        [1 (dec gst/test-size)] :left)))

  (testing "Multiple movement tests"
    (are [x y] (= x (::gs/player-position (ge/move-player-path test-state y)))
      [0 (- gst/test-size 3)] [:left :left :left]
      [2 2] [:right :down :right :down]
      [0 0] [:left :down :up :right])))

(deftest move-player-walls
  (testing "Moves correctly when blocked by wall. Here (canonical
  board, position [0 0]) it means up is not possible, all other
  directions are, down then twice up blocks back to initial position."
    (are [x y] (= x (::gs/player-position (ge/move-player test-state y)))
      [0 0] :up ;; blocked by wall on other side
      [0 1] :right
      [1 0] :down
      [0 (dec gst/test-size)] :left)
    (is (= [0 0]
           (::gs/player-position (ge/move-player-path test-state [:down :up :up])))))

  (testing "If encircled by walls, can't move"
    (let [test-state
          ;; creating test game with player encircled
          (-> test-state
              (assoc-in [::gb/game-board 0 1] :wall)
              (assoc-in [::gb/game-board 0 (dec gst/test-size)] :wall)
              (assoc-in [::gb/game-board 1 0] :wall))]
      ;; blocked everywhere
      (are [x y] (= x (::gs/player-position (ge/move-player test-state y)))
        [0 0] :up 
        [0 0] :right
        [0 0] :down
        [0 0] :left))))
      
(deftest move-player-fruits
  (testing "If player moves on fruits, fruit disappears and score goes
  up. Player goes all the way down, is blocked by wall, then eats 2
  fruits left and 3 right (6 total)"
    (is (every? #(= % :fruit) (get-in test-state [::gb/game-board 8])))
    
    (let [player-path (concat (repeat 10 :down) (repeat 2 :right) (repeat 5 :left))
          fruits-eaten-state (ge/move-player-path test-state player-path)
          fruit-row (get-in fruits-eaten-state [::gb/game-board 8])]
      (is (every? #(= % :fruit) (subvec fruit-row 3 7)))
      (is (every? #(= % :empty) (subvec fruit-row 7)))
      (is (every? #(= % :empty) (subvec fruit-row 0 3)))
      (is (= 6 (fruits-eaten-state ::gs/score))))))

(deftest move-being-test
  (are [direction] (and (= (ge/move-being gst/test-state-2 [:player direction])
                           (ge/move-player gst/test-state-2 direction))
                        (= (ge/move-being gst/test-state-2 [1 direction])
                           (ge/move-enemy gst/test-state-2 direction 1)))
    :up
    :down
    :left
    :right))
    
