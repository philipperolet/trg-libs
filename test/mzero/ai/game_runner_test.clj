(ns mzero.ai.game-runner-test
  (:require [clojure.test]
            [mzero.utils.testing :refer [deftest]]
            [mzero.ai.main-test :refer [basic-run]]))

(deftest run-test-basic-monothreadrunner
  (basic-run "MonoThreadRunner"))

(deftest run-test-basic-watcherrunner
  (basic-run "WatcherRunner"))
