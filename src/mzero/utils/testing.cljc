(ns mzero.utils.testing
  "A few testing utilities."
  #?@
   (:clj
    [(:require
      [clojure.spec.test.alpha :as st]
      [clojure.string :as str]
      [clojure.test :refer [is testing]])]
    :cljs
    [(:require
      [cljs.spec.test.alpha :as st]
      [cljs.test :refer-macros [is testing]]
      [clojure.string :as str])]))

(defn check-results 
  "Returns a nicer version of test.check results for a namespace"
  [nsspecs]
  (->> (st/check #?(:cljs 'nsspecs :clj nsspecs))
       (map #(select-keys (st/abbrev-result %) [:failure :sym]))
       (remove #(not (:sym %)))
       ;; if spec conformance test failed, returns failure data 
       (filter #(:failure %))))

(defn check-failure
  "Returns a more pretty version of results of check on a symbol.
  
   If spec conformance test failed, returns failure data, else nil"
  ([sym opts]
   (-> (st/check #?(:cljs 'sym :clj sym) opts)
       first
       st/abbrev-result
       (select-keys [:failure :sym])
       :failure))
  ([sym] (check-failure sym {})))


(defmacro check-spec
  "Creates a test with deftest for a given symbol, with given options."
  ([sym opts]
   (-> `((testing ~(str "Testing spec " sym)
           (is (not (check-failure ~sym ~opts)))))
       (conj (symbol (str "check-spec-" (last (str/split (str sym) #"/")))))
       (conj 'deftest)))
  ([sym] `(check-spec ~sym {})))

(defmacro check-all-specs
  "Check-spec for all specs in namespace ns"
  [symbs]
  (let [ns-specs (st/enumerate-namespace #?(:cljs 'symbs :clj symbs))]
    (-> (map (fn [to-check]
               `(testing ~(str "Testing spec " to-check)
                  (is (not (check-failure (quote ~to-check))))))
             ns-specs)
        (conj 'check-namespace-specs)
        (conj 'deftest))))

(defn count-calls
  "Used in with-redefs to count function calls during testing.

  Returns a new function that mimics `f` but counts the number of
  calls and stores it as metadata.
  Resets count when call count is checked.
  Example:
  ```
  (with-redefs [fn/to-count (u/count-calls fn/to-count)]
     (fn/that-calls-to-count)
     (is (= expected-count ((:call-count (meta fn/to-count))))))
  ```"
  [f]
  (let [n (atom 0)]
    (with-meta
      (comp (fn [x] (swap! n inc) x) f)
      {:call-count (fn [] (first (reset-vals! n 0)))
       :private false})))

(defmacro deftest
  "Like `clojure.test/deftest`, with spec instrument run before and
  unstrument run after test body--except if the first element of body
  is the keyword `:unstrumented`, in which case it is exactly
  equivalent to `clojure.test/deftest`"
  [name & body]
  (if (= (first body) :unstrumented)
    `(clojure.test/deftest ~name ~@(rest body))
    `(clojure.test/deftest ~name
       (require '[clojure.spec.test.alpha])
       (clojure.spec.test.alpha/instrument)
       ~@body
       (clojure.spec.test.alpha/unstrument))))
