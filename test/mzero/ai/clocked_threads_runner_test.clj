(ns mzero.ai.clocked-threads-runner-test
  (:require [mzero.ai.clocked-threads-runner :as ctr]
            [mzero.ai.main :as aim :refer [parse-run-args]]
            [mzero.ai.main-test :refer [basic-run]]
            [mzero.ai.player :as aip]
            [mzero.ai.players.random :refer [->RandomPlayer]]
            [mzero.ai.world :as aiw]
            [mzero.ai.world-test :as aiwt]
            [mzero.game.generation :as gg]
            [mzero.utils.utils :as u]
            [mzero.utils.testing :refer [deftest]]
            [clojure.test :refer [are is testing]]))

(def timed-world-state
  (assoc aiwt/world-state ::ctr/missteps 0))

(deftest run-test-basic-clockedthreadsrunner
  (basic-run "ClockedThreadsRunner"))

(deftest update-timing-data-test
  (testing "It should update step timestamp and remaining time, and
  depending on time spent add a misstep"
    (let [game-step-duration 20]
      (are [timestamp time-to-wait missteps]
          (= (ctr/update-timing-data timed-world-state timestamp game-step-duration)
             (-> timed-world-state
                 (assoc ::ctr/missteps missteps)
                 (assoc ::ctr/time-to-wait time-to-wait)))
        10 10 0
        5 15 0
        20 0 1
        22 0 1
        30 0 1))))

(deftest run-test-timing-steps
  (let [world-state
        (atom (-> (aiw/get-initial-world-state
                   (gg/create-nice-game 8 {::gg/density-map {:fruit 5}}))
                  (assoc ::ctr/missteps 0)))
        opts (parse-run-args "-p 50 -g 50 -r ClockedThreadsRunner -v WARNING")]
    (testing "When running a step takes less time to run than game
    step duration, it waits for the remaining time (at ~3ms resolution)"
      (let [player-state (atom (aip/load-player "random" nil nil))
            _ (aip/request-movement player-state world-state)
            start-time (System/currentTimeMillis)]
        (ctr/run-timed-step world-state opts)
        (is (u/almost= (opts :game-step-duration)
                       (- (System/currentTimeMillis) start-time)
                       4))
        
        (aip/request-movement player-state world-state)
        (ctr/run-timed-step world-state opts)
        (is (u/almost= (* 2 (opts :game-step-duration))
                       (- (System/currentTimeMillis) start-time)        
                       6))
        
        (dotimes [_ 5]
          ;; since player & game are not in
          ;; separate threads, player should not
          ;; wait before updating its requests
          ;; for move
          (aip/request-movement player-state world-state)
          (ctr/run-timed-step world-state opts))
        (is (u/almost= (* (opts :game-step-duration) 7)
                       (- (System/currentTimeMillis) start-time)
                       21))))))

(deftest game-ends-on-player-error-test
  (let [failing-player
        (reify aip/Player
          (init-player [player _ _] player)
          (update-player [_ _] (throw (RuntimeException. "I crashed!"))))]
    
    (with-redefs [aip/load-player (constantly failing-player)]
      (is (thrown-with-msg?
           java.lang.Exception
           #".*I crashed!.*"
           (aim/run (aim/parse-run-args "-v WARNING -r ClockedThreadsRunner")))))))

