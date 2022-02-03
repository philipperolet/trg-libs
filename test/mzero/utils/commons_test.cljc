(ns mzero.utils.commons-test
  (:require [mzero.utils.commons :as sut]
            [clojure.test :refer [is deftest]]))

(deftest reduce-until-test
  (is (= (sut/reduce-until #(< 8 %) + (range 10)) 10))
  (is (= (sut/reduce-until #(< 100 %) + (range 10)) 45))
  (is (= (sut/reduce-until #(< 8 %) + 11 (range 10)) 11))
  (is (= (sut/reduce-until #(< 100 %) + 90 (range 10)) 105))
  (is (= (sut/reduce-until #(< 100 %) + 11 (range 10)) 56)))

(deftest filter-keys-test
  (is (= (sut/filter-keys #(>= % 3) {1 :a 2 :b 3 :c 4 :d}) {3 :c 4 :d}))
  (is (= (sut/filter-keys #(= 2 (count %)) {"a" 1 "bc" 2 "cde" 3}) {"bc" 2}))
  (is (= (sut/filter-keys #(int? %) {:a 2 :b 3}) {})))

(deftest filter-vals-test
  (is (= (sut/filter-vals #{1 2} {:a 1 :b 3 :d 0}) {:a 1}))
  (is (= (sut/filter-vals #(< 2 %) {:a 1 :b 3 :d 0 :c 5}) {:b 3 :c 5}))
  (is (= (sut/filter-vals some? {:a nil :b nil :d 0}) {:d 0})))
