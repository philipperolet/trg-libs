(ns mzero.utils.modsubvec-test
  (:require [mzero.utils.modsubvec :as sut]
            [clojure.test :refer [are deftest is]]))

(deftest modsubvec-test
  (let [msv (sut/modsubvec [1 2 3 4 5] 3 3)
        msv2 (sut/modsubvec [9 11 14 13 8 16] -2 4)
        msv3 (sut/modsubvec [9 11 14 13 8 16] -8 4)]
    (are [index res] (= (nth msv index) res)
      0 4
      1 5
      2 1)
    (is (= msv [4 5 1]))
    (is (not= msv [5 1 2]))
    (is (every? true?  (map #(= %1 %2) (seq msv) [4 5 1])))
    (is (= (count msv) 3))
    (is (= msv2 [8 16 9 11]))
    (is (= msv2 msv3))
    (is (= msv2 (seq [8 16 9 11])))
    (is (= (map inc msv2) [9 17 10 12]))))
