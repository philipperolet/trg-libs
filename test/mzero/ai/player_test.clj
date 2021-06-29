(ns mzero.ai.player-test
  (:require [mzero.ai.player :as sut]
            [clojure.test :refer [is testing]]
            [mzero.ai.world :as aiw]
            [mzero.utils.testing :refer [deftest]]
            [mzero.game.state-test :as gst]))

(deftest seed-player-test
  (let [world-state (aiw/get-initial-world-state gst/test-state-2)]
    (is (= (type (-> (sut/load-player "random" nil world-state) :rng)) java.util.Random))
    (testing "Without seed, randomly seeded"
      (let [p1 (sut/load-player "random" nil world-state)
            p2 (sut/load-player "random" nil world-state)]
        (is (not= (.nextInt (-> p1 :rng)) (.nextInt (-> p2 :rng))))))
    (testing "With seed, properly seeded"
      (let [p1 (sut/load-player "random" {:seed 39} world-state)
            p2 (sut/load-player "random" {:seed 39} world-state)
            p3 (sut/load-player "random" {:seed 40} world-state)]
        (is (= (.nextInt (-> p1 :rng)) (.nextInt (-> p2 :rng))))
        (is (not= (.nextInt (-> p1 :rng)) (.nextInt (-> p3 :rng))))))))
