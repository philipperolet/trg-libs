(ns mzero.ai.world-test
  (:require [clojure.test :refer [testing is are]]
            [mzero.utils.testing :refer [check-all-specs deftest]]
            [mzero.game.state :as gs]
            [mzero.game.board :as gb]
            [mzero.game.state-test :as gst]
            [mzero.ai.world :as aiw]))

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
               (dissoc ::aiw/missteps ::aiw/step-timestamp ::aiw/time-to-wait))
           {::aiw/requested-movements {}
            ::aiw/game-step 2
            ::gs/game-state (-> test-state
                                (assoc-in [::gb/game-board 1 1] :empty)
                                (assoc ::gs/player-position [2 1])
                                (update ::gs/score inc)
                                (assoc ::gs/enemy-positions [[3 0] [1 0]]))})))
  
  (testing "Game lost or won during step should not err even when
        some movements remain"
    (let [world-state (assoc world-state ::gs/game-state gst/test-state-2)]
      (is (= (-> world-state
                 (assoc ::aiw/requested-movements {0 :up 1 :down :player :left})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :won))
      
      (is (= (-> world-state
                 (assoc ::aiw/requested-movements {:player :left 0 :up 1 :down})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :won))

      (is (= (-> world-state
                 (assoc ::aiw/requested-movements {:player :right 1 :left 0 :up})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :over))

      (is (= (-> world-state
                 (assoc ::aiw/requested-movements {1 :left 0 :up :player :right})
                 (aiw/compute-new-state)
                 (get-in [::gs/game-state ::gs/status]))
             :over)))))
