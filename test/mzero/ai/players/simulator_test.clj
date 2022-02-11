(ns mzero.ai.players.simulator-test
  (:require [mzero.ai.players.simulator :as sut]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.main :as aim]
            [mzero.game.state :as gs]
            [mzero.ai.world :as aiw]))

(deftest simulator-run-instrumented
  (testing "No wrong calls"
    (with-redefs [sut/nb-simulations 50]
      (let [game-run
            (aim/run (aim/parse-run-args "-S 26 -s 15 -n 5 -t simulator"))]
        (is true)))))

(deftest simulator-run
  :unstrumented
  (testing "Gets full score in less than 200 steps on a small board")
  (let [game-run
          (aim/run (aim/parse-run-args "-S 26 -s 15 -n 200 -t simulator"))]
      (is (== (-> game-run :world ::gs/game-state ::gs/score) 27))
      (is (< (-> game-run :world ::aiw/game-step) 200))))

(deftest simulator-run-speed
  :unstrumented
  (testing "Less than 50ms per step"
    (let [world (aiw/world 26 25)
          before (System/currentTimeMillis)
          game-run
          (aim/run (aim/parse-run-args "-n 100 -t simulator") world)
          after (System/currentTimeMillis)]
      (is (< (int (/ (- after before) 100)) 40)))))
