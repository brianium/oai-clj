(ns oai-clj.http
  (:import (com.openai.client.okhttp OpenAIOkHttpClient OpenAIOkHttpClientAsync)))

(def ^:dynamic *client*
  (delay
    (OpenAIOkHttpClient/fromEnv)))

(defn get-client
  "Access the dynamic *client* variable. Supports treating *client* as an IDeref
   or a literal value. The result of this function is what all openai services will
   use"
  []
  (if (instance? clojure.lang.IDeref *client*)
    @*client*
    *client*))

(def ^:dynamic *client-async*
  (delay
    (OpenAIOkHttpClientAsync/fromEnv)))

(defn get-client-async
  "Access the dynamic *client* variable. Supports treating *client* as an IDeref
   or a literal value. The result of this function is what all openai services will
   use"
  []
  (if (instance? clojure.lang.IDeref *client-async*)
    @*client-async*
    *client-async*))
