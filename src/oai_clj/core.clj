(ns oai-clj.core
  "A Clojure friendly facade on top of openai-java"
  (:require [oai-clj.models.images :as models.images]
            [oai-clj.models.responses :as models.responses]
            [oai-clj.models.audio :as models.audio]))

;;; Responses API

(defn create-response
  "Creates a response using the Responses API. All keys map to builder methods in the java sdk. Some extra keys are supported
  for convenience

  Options:
  :model - (optional) The model to use for responses. Can be a keyword from chat-models or a Java enum from the open ai sdk. Defaults to :gpt-4o
  :input-of-response - (optional)
  :input  - (optional)
  :metadata - (optional, map)
  :max-output-tokens - (optional, int)
  :format - (optional) - A var pointing to a Malli Schema or a [schema format-name] tuple. Used for structured outputs
  :easy-input-messages - (optional) A vector of {:role, :content} maps. Will be converted to an ArrayList of EasyInputMessage and passed to .inputOfResponse on the java builder
  :input-items - (optional) A vector of {:role, :content} maps. Supports more content types than easy-input-messages. :content itself should be a vector of content maps - .ie [{:type :image :detail :auto} {:type :text :text \"Describe this image.\"]
  :raw? - (optional, boolean) - If true, returns the Java type instead of a clojure map"
  [& {:as m}]
  (models.responses/create-response m))

(defn create-response-stream
 "Nearly identical to create-response except it uses the async client. The first argument
  is a function that will receive events as they become available. All other options are the same
  as create-response with the addition of two new options:

  Additional options:
  :on-complete - (optional) - Called when the underlying future completes
  :on-error   - (optional)  - Called with any exceptions generated in the underlying future"
  [on-event & {:as m}]
  (models.responses/create-response-stream on-event m))

;;; Image generation

(defn generate-image
  "A Clojure version of openai-java's image generation sdk. Builder methods are mapped to optional
  keys (as either keyword args or a map). Keywords (instead of java enums) are used for the :model, :size,
  :moderation, :output-format, :quality, :response-format, and :style properties. See oai-clj.models.image for
  possible values.

  Options:
  :prompt - (required, string)
  :model -  (optional, keyword)
  :size  -  (optional, keyword)
  :moderation - (optional, keyword)
  :n  - (optional, int)
  :output-compression - (optional, int)
  :output-format - (optional, keyword)
  :quality - (optional, keyword)
  :response-format - (optional, keyword)
  :style - (optional, keyword)
  :user - (optional, string)
  :raw? - (optional, boolean) - If true, the Java type will be returned. Defaults to false"
  [& {:as m}]
  (models.images/generate-image m))

;;; Audio

(defn create-speech
  "See oai-clj.models.audio for key values

  Options:
  :input - (required, string) - The test to generate audio for
  :model - (optional, keyword) - defaults :gpt-4o-mini-tts
  :voice - (optional, keyword) - defaults to :verse
  :instructions - (optional, string)
  :response-format - (optional, keyword)
  :speed - (optional, double)"
  [& {:as m}]
  (models.audio/create-speech m))

(defn transcribe
  "See oai-clj.models.audio for key values

  Options:
  :file - (required, map|java.net.URL) - A map of {:filename string, :value input-stream} or a java.net.URL. If a java.net.URL, the name will be inferred from the url path
  :model - (optional, keyword) - Defaults to :whisper-1
  :language - (optinoal, string)
  :prompt - (optional, string)
  :temperature - (optional, double)
  :response-format - (optional, keyword)
  :timestamp-granularities - (optional, vector<keyword>)
  :raw? - (optional) - If true, returns that Java type. Defaults to false"
  [& {:as m}]
  (models.audio/transcribe m))
