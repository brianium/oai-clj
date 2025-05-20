(ns dev
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.java.io :as io]
            [oai-clj.core :as oai]))

(defn start []
  (println "oai-clj start"))

(defn stop []
  (println "oai-clj stop"))

(defn refresh []
  (repl/refresh :after 'dev/start))

(comment
  ;;; :easy-input-messages are the OG - string content and :role as one of [:user :assistant :system]
  (def response
    (oai/create-response
     :easy-input-messages [{:role :system :content "Given 2 numbers, you add them together"}
                           {:role :user :content "3 and 4 babbyyyyyy"}]))

  ;;; We can pass a :raw? property to most model options to get the Java type back (instead of a Clojure data structure)
  (def response
    (oai/create-response
     :easy-input-messages [{:role :system :content "Given 2 numbers, you add them together"}
                           {:role :user :content "3 and 4 babbyyyyyy"}]
     :raw? true))

  ;;; We can leverage structured outputs by referencing a malli schema or passing a tuple that gives the output an explicit name
  (def SumResult
    [:map
     [:result {:description "The result of summing the numbers"} :int]
     [:joke {:description "A relevant math joke because laughter is medicine"} :string]])

  ;;; Using a variable will automatically provide the variable name as the format name. This is the recommended method because it is the coolest
  (def response
    (oai/create-response
     :easy-input-messages [{:role :system :content "Given 2 numbers, you add them together"}
                           {:role :user :content "3 and 4 babbyyyyyy"}]
     :format 'SumResult))

  ;;; You can also provide a tuple to give an explicit format name
  (def response
    (oai/create-response
     :easy-input-messages [{:role :system :content "Given 2 numbers, you add them together"}
                           {:role :user :content "3 and 4 babbyyyyyy"}]
     :format [SumResult "CoolFormat"]))

  ;;; Use a java.net.URL for the image-url. Strings are interpreted as {:type :text :text x}
  (def response
    (oai/create-response
     :input-items [{:role :user :content ["Describe this image."
                                          {:type :image :detail :auto :image-url (io/resource "turt.png")}]}]))

  ;;; File inputs work the same way - they just use a different builder map
  (def response
    (oai/create-response
     :input-items [{:role :user :content ["Describe this file."
                                          {:type :file :filename "dummy.pdf" :file-data (io/resource "dummy.pdf")}]}]))

  ;;; Input items aims to support all the ResponseInputItem/of* varieties. This is equivalent to the explicit :easy-input-messages
  (def response
    (oai/create-response
     :input-items [{:role :system :content "Given 2 numbers, you add them together"}
                   {:role :user :content "3 and 4 babbyyyyyy"}]))

  ;;; Mixing and matching is much easier with :input-items
  (def response
    (oai/create-response
     :input-items [{:role :system :content "You are an art critic specializing in cartoon turtles"}
                   {:role :user :content "I am going to show you some art. Be honest. Before you give your review, start with \"Here we go again.\""}
                   {:role :assistant :content "Understood"}
                   {:role :user :content ["Give it to me straight, what do you think of this?"
                                          {:type :image :detail :auto :image-url (io/resource "turt.png")}]}]))

  ;;; Response conversation example from openai-java
  (let [*context     (atom [{:role :user :content "Tell me a story about building the best SDK!"}])
        append       (fn [v x]
                       (into x v))
        with-outputs (fn [items response]
                       (reduce #(conj %1 (:message %2)) items (:output response)))]
    (dotimes [i 4]
      (->> (swap! *context with-outputs (oai/create-response :input-items @*context))
           (append [{:role :user :content (format "But why?%s" (reduce str "" (repeat i "?")))}])
           (reset! *context)))
    @*context)

  ;;; Async, callback driven responses - no Clojure map support yet all events are the raw Java type
  (oai/create-response-stream
   (fn [event]
     (println event))
   :input "What was prince's most popular album?"
   :on-complete #(println "All done here"))

  ;;; Image generation
  (def image-response
    (oai/generate-image
     :prompt "I want to see a kawaii frog knight riding on docile bunny steed"
     :model  :dall-e-3
     :size   :1024x1024))

  ;;; Response as a Clojure map
  (:url (first (:data image-response)))

  ;;; Raw responses here too
  (def image-response
    (oai/generate-image
     :prompt "I want to see a noir detective doing a sick kickflip on a skateboard"
     :model  :dall-e-3
     :size   :1024x1024
     :raw? true))

  ;;; TTS
  (def audio-input-stream
    (oai/create-speech :input "The Clojure programming language is a fun time indeed" :instructions "Speek in a thick Cockney accent"))

  ;;; We can just write that biz out to a file
  (with-open [o (io/output-stream "test.wav")]
    (io/copy audio-input-stream o))

  (defn audio-input [] (io/input-stream "resources/test.wav"))

  ;;; Transcription
  (def transcribe-response
    (oai/transcribe :file {:filename "test.wav" :value (audio-input)}))

  ;;; Raw responses here too
  (def transcribe-response
    (oai/transcribe :file {:filename "test.wav" :value (audio-input)} :raw? true))

  ;;; :file also supports java.net.URL instances
  (def transcribe-response
    (oai/transcribe :file (io/resource "test.wav")))

  (refresh))


