(ns oai-clj.util
  (:require [clojure.string :as str])
  (:import (java.util Optional)))

;;;; Utilities

(defn optional
  "Return the contained value or nil. Acts as an identify function for non Optional types"
  [x]
  (if (instance? Optional x)
    (.orElse ^Optional x nil)
    x))

(defn stream
  "A helper for converting a value's stream property
  into a Clojure seq"
  [x]
  (-> (.stream x)
      .iterator
      iterator-seq))

;;; map supremacy 

(defmacro defbuilder
  "Map a java builder to a Clojure function that accepts plain maps. A spec generally
  maps the Clojure key desired to a string name of the Java method. If a vector is given instead
  of a string, a tuple of [string-java-name fn-1] is expected. This is used to bundle a transformation
  into a method call. This could be useful for supporting keywords instead of Java enums"
  [sym builder spec]
  `(defn ~sym [m#]
     (let [b# ~builder]
       (doseq [[k# v#] m#]
         (when-let [meth# (get ~spec k#)]
           (let [call# (if (vector? meth#) (first meth#) meth#)
                 xf#   (if (vector? meth#) (second meth#) identity)]
             (clojure.lang.Reflector/invokeInstanceMethod
              b# call# (into-array Object [(xf# v#)])))))
       (.build b#))))

















