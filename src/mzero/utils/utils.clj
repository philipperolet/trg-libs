(ns mzero.utils.utils
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.data.generators :as g]
            [clojure.string :as str]
            [clojure.tools.logging.impl :as li]))

(defmacro with-loglevel
  "Set the logging `level` during the execution of `body`,
  tailored to use of java.util.logging impl"
  [level & body]
  `(let [logger# (li/get-logger log/*logger-factory* "")
         old-level# (.getLevel logger#)]
     (.setLevel logger# ~level)
     (try
       ~@body
       (finally
         (.setLevel logger# old-level#)))))

(defn weighted-rand-nth
  "Pick an element of `coll` randomly according to the
  distribution represented by positive `weights`"
  [coll weights]
  (loop [sum-rest (* (g/double) (apply + weights))
         [item & restc] coll
         [w & restw] weights]
    (if (<= (- sum-rest w) 0) item (recur (- sum-rest w) restc restw))))

(defn abs [x]
  (if (pos? x) x (- x)))

(defn almost=
  "Compares 2 numbers with a given `precision`, returns true if the
  numbers' difference is lower or equal than the precision

  Precision defaults to a ten thousandth of the first number's value."
  ([a b precision]
   (<= (abs (- a b)) precision))
  ([a b]
   (almost= a b (* (Math/abs a) 0.0001))))

(defn coll-almost=
  "almost= for collections"
  ([c1 c2]
   (and (= (count c1) (count c2))
        (every? true? (map almost= c1 c2))))
  ([c1 c2 precision]
   (and (= (count c1) (count c2))
        (every? true? (map #(almost= %1 %2 precision) c1 c2)))))

(defmacro timed
  "Returns a vector with 2 values:
  -  time in miliseconds to run the expression, as a
  float--that is, taking into account micro/nanoseconds, subject to
  the underlying platform's precision;
  - expression return value."
  [expr]
  `(let [start-time# (System/nanoTime)
         result# ~expr]
     [(/ (- (System/nanoTime) start-time#) 1000000.0) result#]))

(defn remove-common-beginning
  "Checks if seq1 and seq2 begin with a common subsequence, and returns
  the remainder of seq1--that is, seq1 stripped of the common
  subsequence. Returns a lazy sequence."
  [seq1 seq2]
  (if (or (empty? seq1) (not= (first seq1) (first seq2)))
    seq1
    (recur (rest seq1) (rest seq2))))

(defn map-map
  "Return a map with `f` applied to each of `m`'s keys"
  [f m]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} m))

(defn fn-name
  [fn-var]
  {:pre [(var? fn-var)]}
  (str (:name (meta fn-var))))

(defn with-logs
  "Return a function identical to `fn_`, that logs a message every time
  it is called, whose first part is fn_'s name & call count and second
  part is a custom message computed by `str-fn`"
  ([fn-var str-fn]
   {:pre [(var? fn-var)]}
   (let [call-count (atom 0)]
     (fn [& args]
       (log/info (format "%s : call # %d %s"
                         (fn-name fn-var)
                         @call-count
                         (apply str-fn args)))
       (swap! call-count inc)
       (apply fn-var args))))
  ([fn_]
   (with-logs fn_ (constantly ""))))

(defn with-mapped-gen
  "Like with-gen, but takes a fn to be fed to fmap, and applies it to a
  regular generator for the provided spec"
  [spec fn]
  (s/with-gen spec #(gen/fmap fn (s/gen spec))))

(defn ipmap
  "Similar to pmap, but interruptible--all threads will receive an
  interrupt signal if the main thread is interrupted. However,
  contrarily to pmap, evaluation is eager."
  [f & colls]
  (let [rets (apply map (fn [& vals_] (future (apply f vals_))) colls)]
    (try
      (vec (map deref rets))
      (catch InterruptedException _
        (doall (map future-cancel rets))
        (throw (InterruptedException.))))))

(defn load-impl
  "Load an implementation of a given protocol given its `impl-name` and
  `full-ns` as strings. It assumes the implementing record is stored
  in a file named `impl-name`, and is itself named as the camel-cased
  version of `impl-name` + an optional string `suffix`"
  ([impl-name full-ns suffix]
   (let [impl-ns-string (str full-ns "." impl-name)
         impl-constructor-string
         (-> impl-name
             ;; convert impl-type to CamelCase
             (str/split #"-") (#(map str/capitalize %)) str/join
             ;; merge to constructor string
             (#(str impl-ns-string "/map->" % suffix)))
         error-message
         "Couldn't load impl %s. Please check the name matches a file in %s"]
     (try
       ;; use of private fn serialized-require to avoid bugs due to
       ;; require not being thread safe ATTOW
       ;; see https://clojure.atlassian.net/browse/CLJ-1876
       (#'clojure.core/serialized-require (symbol impl-ns-string))
       ((resolve (symbol impl-constructor-string)) {})
       (catch java.io.FileNotFoundException _
         (throw (RuntimeException. (format error-message impl-name full-ns)))))))
  ([impl-name full-ns]
   (load-impl impl-name full-ns "")))

(defn scaffold
  "Show all the interfaces implemented by given `iface`"
  [iface]
  (doseq [[iface methods] (->> iface .getMethods
                            (map #(vector (.getName (.getDeclaringClass %))
                                    (symbol (.getName %))
                                    (count (.getParameterTypes %))))
                            (group-by first))]
    (println (str "  " iface))
    (doseq [[_ name argcount] methods]
      (println
        (str "    "
          (list name (into ['this] (take argcount (repeatedly gensym)))))))))

;; Demonic stuff because I want to use stuff without adding a require
(ns clojure.core)
(defn prr
  "Prints v & returns v"
  ([f v]
   (println (str (f v)))
   v)
  ([v]
   (prr identity v)))
