(ns mzero.ai.players.java-dag-test
  (:require [mzero.ai.players.java-dag :as jd]
            [mzero.game.state-test :as gst]
            [clojure.test :refer [is testing]]
            [mzero.utils.testing :refer [deftest]]))

(deftest java-dag-node-t
  (is (every? #{##Inf}
              (reduce into [] (:values (jd/java-dag-node gst/test-state-2)))))
  (is (every? #(= % 0.0)
              (reduce into [] (:frequencies (jd/java-dag-node gst/test-state-2))))))
