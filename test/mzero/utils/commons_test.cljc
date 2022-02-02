(ns mzero.utils.commons-test
  (:require [mzero.utils.commons :as sut]
            [clojure.test :refer [is deftest]]))

(deftest reduce-until-test
  (is (= (sut/reduce-until #(< 8 %) + (range 10)) 10))
  (is (= (sut/reduce-until #(< 100 %) + (range 10)) 45))
  (is (= (sut/reduce-until #(< 8 %) + 11 (range 10)) 11))
  (is (= (sut/reduce-until #(< 100 %) + 90 (range 10)) 105))
  (is (= (sut/reduce-until #(< 100 %) + 11 (range 10)) 56)))
