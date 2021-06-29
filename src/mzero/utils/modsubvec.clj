(ns mzero.utils.modsubvec
  "ModSubVector : A view on vector vect that is a subvector starting at
`start`, of length `length`, such that:

- start & length can be bigger than vect count or smaller than 0 (via
  modulo (count vect)). In particular the subvec restarts from zero if
  start + length > (count vect)

- the resulting subvector is of size `length`, its first
element (index 0) is vect[position], its last element is
vect[(position + length) % vect-size].")

(deftype ModSubVector [vect start length]
  clojure.lang.Indexed
  (nth [this n m]
    (if (< -1 n length)
      (nth vect (mod (+ start n) (count vect)))
      m))
  (nth [this n]
    (or (nth this n nil)
        (throw (IndexOutOfBoundsException.))))

  clojure.lang.Counted
  (count [this] length)
  clojure.lang.IPersistentVector

  clojure.lang.IPersistentCollection
  (equiv [this obj]
    (cond
      (indexed? obj)
      (and (= (count obj) (count this))
           (loop [cnt (dec (count this))]
             (if (= (nth obj cnt) (nth this cnt))
               (or (zero? cnt)
                   (recur (dec cnt)))
               false)))

      (seqable? obj)
      (= (seq this) (seq obj))

      :else
      false))

  clojure.lang.Seqable
  (seq [this]
    (reify clojure.lang.ISeq
      (first [this_s] (vect (mod start (count vect))))
      (next [this_s]
        (if (= 1 length) nil (seq (ModSubVector. vect (inc start) (dec length)))))
      (seq [this_s] this_s)
      (more [this_s] (or (next this_s) '()))
      (equiv [this_s obj]
        (and (seqable? obj)
             (let [obj_s (seq obj)]
               (and (= (first this_s) (first obj_s))
                    (= (next this_s) (next obj_s)))))))))

(defn modsubvec
  "Return a subvector of `vect` starting at `start`, of length
  `length`, using modulo if start+length > (count vect)"
  [vect start length]
  {:pre [(vector? vect)
         (int? start)
         (nat-int? length)
         (< 0 length (inc (count vect)))]}
  (->ModSubVector vect start length))
