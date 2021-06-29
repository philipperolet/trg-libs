(ns mzero.utils.utils-test
  (:require [mzero.utils.utils :as u]
            [clojure.test :refer [are deftest is]]
            [clojure.data.generators :as g]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.impl :as li]))


(deftest ^:integration with-loglevel
  (.setLevel (li/get-logger log/*logger-factory* "") java.util.logging.Level/INFO)
  (u/with-loglevel java.util.logging.Level/WARNING
    (+ 3 3)
    (- 2 2)
    (log/info "ERROR: Should NOT show")
    (log/warn "Should show 1/3"))
  (log/info "Should 2/3")
  (try 
    (u/with-loglevel java.util.logging.Level/WARNING
      (+ 3 3)
      (log/info "ERROR: Should NOT show")
      (throw (java.lang.Exception. "ow")))
    (catch java.lang.Exception _
      (log/info "Should show 3/3"))))

(deftest weighted-rand-nth-test
  (binding [g/*rnd* (java.util.Random. 30)]
    (let [test-coll [:a :b :c :d]
          random-10000-freqs
          (->> #(u/weighted-rand-nth test-coll [0.4 0.1 0.1 0.2])
               (repeatedly 10000)
               vec
               frequencies)
          perfect-average
          {:a 5000 :b 1250 :c 1250 :d 2500}]

      (is (->> test-coll
               (map #(u/almost= (% random-10000-freqs) (% perfect-average) 60))
               (every? true?))))
    (let [test-coll [:a :b :c :d :e :f]
          random-1k
          (->> #(u/weighted-rand-nth test-coll [0 0.5 0.5 0 0.5 0])
               (repeatedly 1000)
               vec
               frequencies)]
      (is (every? nil? (map random-1k [:a :d :f])))
      (is (every? #(> % 300) (map random-1k [:b :c :e]))))))

(deftest almost=-test
  (is (u/almost= 10 12 2))
  (is (not (u/almost= 10 13 2)))
  (is (u/almost= 1000 1000 3))
  (is (u/almost= 0.1 0.2 1))
  (is (u/almost= 0.1 0.09 0.02))
  (is (not (u/almost= 0.1 0.09 0.005)))
  (is (u/almost= 0.1 0.1000001))
  (is (u/almost= 1000.001 1000))
  (is (not (u/almost= 0.1 0.10002)))
  (is (u/almost= 1.0 1.0))
  (is (u/almost= 0.0 0.0))
  (is (u/almost= -1.1 -1.1))
  (is (not (u/almost= -1.1 1.1)))
  (is (not (u/almost= 0.01 0.0100011)))
  (is (not (u/almost= 100.0 100.02)))
  (is (not (u/almost= 0.00000001 0)))
  (is (not (u/almost= 0 0.00000001))))

(deftest reduce-until-test
  (is (= (u/reduce-until #(< 8 %) + (range 10))
         10))

  (is (= (u/reduce-until #(< 100 %) + (range 10))
         45))

  (is (= (u/reduce-until #(< 8 %) + 11 (range 10))
         11))

  (is (= (u/reduce-until #(< 100 %) + 90 (range 10))
         105))

  (is (= (u/reduce-until #(< 100 %) + 11 (range 10))
         56)))

(deftest timed-test
  (is (u/almost= (first (u/timed (Thread/sleep 10))) 10 0.5))
  (is (not (u/almost= (first (u/timed (Thread/sleep 10))) 10 0.00001)))
  (is (u/almost= (first (u/timed (Thread/sleep 10)))
                 (first (u/timed (Thread/sleep 10)))
                 0.5))
  (is (not (u/almost= (first (u/timed (Thread/sleep 5)))
                      (first (u/timed (Thread/sleep 5)))
                      0.0001)))
  (is (= (second (u/timed (* 3 3))) 9)))

(deftest filter-keys-test
  (is (= (u/filter-keys #(>= % 3) {1 :a 2 :b 3 :c 4 :d}) {3 :c 4 :d}))
  (is (= (u/filter-keys #(= 2 (count %)) {"a" 1 "bc" 2 "cde" 3}) {"bc" 2}))
  (is (= (u/filter-keys #(int? %) {:a 2 :b 3}) {})))

(deftest filter-vals-test
  (is (= (u/filter-vals #{1 2} {:a 1 :b 3 :d 0}) {:a 1}))
  (is (= (u/filter-vals #(< 2 %) {:a 1 :b 3 :d 0 :c 5}) {:b 3 :c 5}))
  (is (= (u/filter-vals some? {:a nil :b nil :d 0}) {:d 0})))

(deftest map-map-test
  (is (= {:a 6 :b 4} (u/map-map #(* % 2) {:a 3 :b 2}))))

(deftest remove-common-beginning
  (are [s1 s2 res]
      (= (u/remove-common-beginning s1 s2) res)
    '(1 2 3 4 5) '(1 2 5 4) '(3 4 5)
    [] [] []
    [] '() []
    '(1 2 3) '(4 5 6) '(1 2 3)
    [2 4 7] '(2 4 7 8) []
    '(2 4 7 8) [2 4 7] '(8)))

(deftest fn-name
  (is (= (u/fn-name #'+) "+")))
