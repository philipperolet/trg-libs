(ns mzero.ai.world-test
  (:require [clojure.test :refer [testing is are]]
            [mzero.utils.testing :refer [check-all-specs deftest]]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.game.state-test :as gst]
            [mzero.ai.world :as aiw]
            [mzero.game.generation :as gg]))

(check-all-specs mzero.ai.world)

(def test-state
  "add a 2nd fruit to test state board to avoid clearing the game by
  only eating 1 fruit"
  (assoc-in gst/test-state-2 [::gb/game-board 4 4] :fruit))

(def world-state (aiw/get-initial-world-state test-state 0))

(deftest compute-new-state-test
  (testing "Basic behaviour, correctly updating world state on movement requests."
    (is (= test-state (-> world-state (aiw/compute-new-state) ::gs/game-state)))
    
    (is (= (-> world-state
               (assoc ::aiw/requested-movements {0 :up :player :left})
               (aiw/compute-new-state)
               (assoc ::aiw/requested-movements {:player :down 0 :up 1 :right})
               (aiw/compute-new-state)
               (dissoc ::aiw/missteps ::aiw/step-timestamp ::aiw/time-to-wait
                       ::aiw/current-level-start-step))
           {::aiw/requested-movements {}
            ::aiw/game-step 2
            ::gs/game-state (-> test-state
                                (assoc-in [::gb/game-board 1 1] :empty)
                                (assoc ::gs/player-position [2 1])
                                (update ::gs/score inc)
                                (assoc ::gs/enemy-positions [[3 0] [1 0]]))})))
  
  (testing "Game lost or won during step should not err even when
        some movements remain"
    (let [game-state (assoc gst/test-state-2 ::gs/score 0.0)
          world-state (assoc world-state ::gs/game-state game-state)
          winning-state-1
          (-> world-state
              (assoc ::aiw/requested-movements {0 :up 1 :down :player :left})
              (aiw/compute-new-state))
          winning-state-2
          (-> world-state
              (assoc ::aiw/requested-movements {:player :left 0 :up 1 :down})
              (aiw/compute-new-state))
          losing-state-1
          (-> world-state
              (assoc ::aiw/requested-movements {:player :right 1 :left 0 :up})
              (aiw/compute-new-state))
          losing-state-2
          (-> world-state
              (assoc ::aiw/requested-movements {1 :left 0 :up :player :right})
              (aiw/compute-new-state))]
      (is (= (-> winning-state-1 ::gs/game-state ::gs/status) :won))
      (is (== (-> winning-state-1 ::gs/game-state ::gs/score) (+ 1 0))) ;; winning level bonus
      (is (= (-> winning-state-2 ::gs/game-state ::gs/status) :won))
      (is (== (-> winning-state-2 ::gs/game-state ::gs/score) (+ 0 1))) 
      (is (= (-> losing-state-1 ::gs/game-state ::gs/status) :over))
      (is (== (-> losing-state-1 ::gs/game-state ::gs/score) 0))
      (is (= (-> losing-state-2 ::gs/game-state ::gs/status) :over))
      (is (== (-> losing-state-2 ::gs/game-state ::gs/score) 0)))))

(deftest indices-of-enemies-to-move
  (are [game-step start-step enemies indices]
      (= (#'aiw/indices-of-enemies-to-move game-step start-step enemies) indices)
    58 0 [:drink :mouse :mouse :drink] [1 2]
    120 50 [:drink :mouse :virus] [0 1 2]
    40 0 [:virus :virus] [0 1]
    111 0 [:drink :mouse :virus] [2]))

(deftest request-enemies-movements
  (let [world
        (-> (aiw/multilevel-world 15 15
                                  [{::gg/density-map {:fruit 5}
                                    :enemies [:drink :virus :mouse]}])
            (assoc ::aiw/game-step 120)
            aiw/request-enemies-movements)]
    (is (= (keys (-> world ::aiw/requested-movements)) [0 1 2]))))
