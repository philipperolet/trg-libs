(ns mzero.utils.commons
  "Utils for both Clojure and ClojureScript"
  (:require [clojure.string :as cstr]))

(defn reduce-until
  "Like clojure.core/reduce, but stops reduction when `pred` is
  true. `pred` takes one argument, the current reduction value, before
  applying another step of reduce. If `pred` does not become true,
  returns the result of reduce"
  ([pred f coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) coll))
  ([pred f val coll]
   (reduce #(if (pred %1) (reduced %1) (f %1 %2)) val coll)))

(defn filter-keys
  "Like select-keys, with a predicate on the keys"
  [pred map_]
  (select-keys map_ (filter pred (keys map_))))


(defn filter-vals
  "Like filter-keys, except on vals"
  [pred map_]
  (select-keys map_ (filter #(pred (map_ %)) (keys map_))))

(defn currTimeMillis []
  #?(:clj (System/currentTimeMillis)
     :cljs (. (js/Date.) (getTime))))

(defn get-rng
  "Used to generate a seeded RNG to bind with g/*rnd*, and ignored for CLJS"
  [seed]
  #?(:clj (if seed
            (java.util.Random. seed) (java.util.Random.))
     :cljs (js/console.warn "Call to get-rng with seed not implemented
     in javscript. Continuing with unseeded random")))

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
             (cstr/split #"-") (#(map cstr/capitalize %)) cstr/join
             ;; merge to constructor string
             (#(str impl-ns-string "/map->" % suffix)))
         error-message
         "Couldn't load impl. Please check the name matches a file in :"]
     (try
       ;; use of private fn serialized-require to avoid bugs due to
       ;; require not being thread safe ATTOW
       ;; see https://clojure.atlassian.net/browse/CLJ-1876
       (#?(:clj #'clojure.core/serialized-require :cljs require) (symbol impl-ns-string))
       ((resolve (symbol impl-constructor-string)) {})
       (catch #?(:clj java.io.FileNotFoundException :cljs js/Error) _
         (throw (#?(:clj RuntimeException. :cljs js/Error.) (str error-message impl-name full-ns)))))))
  ([impl-name full-ns]
   (load-impl impl-name full-ns "")))
