(ns mzero.utils.xp-test
  (:require [mzero.utils.xp :as x]
            [clojure.test :refer [deftest is]]))

(deftest measure-test
  (is (= (x/measure + identity [[3 4 5] [2 -1 -2]]) [[12 -1]]))
  (is (= (x/measure + (juxt identity -) [[3 4 5] [2 -1] [3 -3]])
         [[12 1 0] [-12 -1 0]]))
  (is (nil? (x/measure + str [[3 4] [2 -1]]))))

(deftest std-test
  (is (= (x/std [3 3]) 0.0))
  (is (= (x/std [1 -1 1 -1 0]) 1.0))
  (is (= (x/std [3]) -1.0)))
