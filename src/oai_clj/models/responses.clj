(ns oai-clj.models.responses
  "Mirrors com.openai.models.responses in openai-java"
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [malli.json-schema :as mj]
            [malli.util :as mu]
            [oai-clj.http :as http]
            [oai-clj.util :refer [defbuilder optional]])
  (:import (java.net URL URLConnection)
           (java.io BufferedInputStream ByteArrayOutputStream)
           (java.nio.file Files Paths)
           (java.util ArrayList Base64)
           (java.util.function Function)
           (com.openai.core JsonValue)
           (com.openai.core.http AsyncStreamResponse$Handler)
           (com.openai.models ChatModel)
           (com.openai.models.responses EasyInputMessage EasyInputMessage$Role ResponseCreateParams Response ResponseFormatTextJsonSchemaConfig ResponseFormatTextJsonSchemaConfig$Schema ResponseTextConfig ResponseInputItem ResponseInputItem$Message ResponseInputItem$Message$Role ResponseInputImage ResponseInputImage$Detail ResponseInputText ResponseInputContent ResponseInputFile ResponseCreateParams$Metadata Response$IncompleteDetails$Reason ResponseOutputMessage$Status ResponseStatus ResponseOutputMessage ResponseOutputMessage$Content ResponseOutputText ResponseOutputText$Annotation ResponseOutputText$Annotation$FileCitation ResponseOutputText$Annotation$UrlCitation ResponseOutputText$Annotation$FilePath ResponseOutputRefusal)))

(def easy-input-roles
  {:user      EasyInputMessage$Role/USER
   :assistant EasyInputMessage$Role/ASSISTANT
   :system    EasyInputMessage$Role/SYSTEM})

(def details
  {:auto ResponseInputImage$Detail/AUTO
   :low  ResponseInputImage$Detail/LOW
   :high ResponseInputImage$Detail/HIGH})

(def chat-models
  {:gpt-4-1      ChatModel/GPT_4_1
   :gpt-4-1-mini ChatModel/GPT_4_1_MINI
   :gpt-4-1-nano ChatModel/GPT_4_1_NANO
   :o4-mini      ChatModel/O4_MINI
   :o3           ChatModel/O3
   :o3-mini      ChatModel/O3_MINI
   :gpt-4o       ChatModel/GPT_4O
   :gpt-4o-mini  ChatModel/GPT_4O_MINI})

(def chat-models' (set/map-invert chat-models))

(def incomplete-details-reasons
  {Response$IncompleteDetails$Reason/MAX_OUTPUT_TOKENS :max-output-tokens
   Response$IncompleteDetails$Reason/CONTENT_FILTER :content-filter})

(def message-status
  {ResponseOutputMessage$Status/IN_PROGRESS :in-progress
   ResponseOutputMessage$Status/COMPLETED :completed
   ResponseOutputMessage$Status/INCOMPLETE :incomplete})

(def message-status' (set/map-invert message-status))

(def response-status
  {ResponseStatus/COMPLETED :completed
   ResponseStatus/FAILED :failed
   ResponseStatus/IN_PROGRESS :in-progress
   ResponseStatus/INCOMPLETE :incomplete})

(defbuilder easy-input-message
  (EasyInputMessage/builder)
  {:role ["role" easy-input-roles] :content "content"})

(defn easy-input-messages
  "Create a response with easy input - that is entries is expected to be a vector
  of maps containing :role and :content keys. Supported roles are :user, :assistant, and :system. :content
  should be anyhing supported by the EasyInputMessage.Builder class"
  [entries]
  (reduce (fn [items entry]
              (.add items (easy-input-message entry))
              items) (ArrayList.) entries))

(def response-input-item-roles
  {:user      ResponseInputItem$Message$Role/USER
   :system    ResponseInputItem$Message$Role/SYSTEM
   :developer ResponseInputItem$Message$Role/DEVELOPER})

(defbuilder response-input-item
  (ResponseInputItem$Message/builder)
  {:role ["role" response-input-item-roles] :content "content"})

(defn detect-mime-type-url
  "Guess the MIME type of the content at `url`. Tries, in order:
   1. `Files.probeContentType` (for file:// URLs)
   2. `URLConnection/guessContentTypeFromName` (by extension)
   3. `URLConnection/guessContentTypeFromStream` (content‑sniffing)."
  [^URL url]
  (let [protocol  (.getProtocol url)
        file-ctype (when (= "file" protocol)
                     (try
                       (Files/probeContentType (Paths/get (.toURI url)))
                       (catch Exception _)))]
    (or file-ctype
        (URLConnection/guessContentTypeFromName (.getPath url))
        (with-open [^BufferedInputStream in (BufferedInputStream. (.openStream url))]
          (.mark in 8192)
          (let [sniffed (URLConnection/guessContentTypeFromStream in)]
            (.reset in)
            sniffed)))))

(defn url->base64-data-url
  "Read all bytes from `url`, Base64‑encode them, and return a
   data URL of the form
   \"data:<mime-type>;base64,<base64‑data>\"."
  [^URL url]
  (let [mime (or (detect-mime-type-url url)
                 "application/octet-stream")
        bytes (with-open [in   (.openStream url)
                          baos (ByteArrayOutputStream.)]
                (io/copy in baos)
                (.toByteArray baos))
        b64   (.encodeToString (Base64/getEncoder) bytes)]
    (format "data:%s;base64,%s" mime b64)))

(defn with-url-support
  "Allows us to pass a java.net.URL to the :image-url property. Very convenient
  to be able to use (io/resource) as an input to openai-java"
  [x]
  (if (instance? URL x)
    (url->base64-data-url x)
    x))

(defbuilder response-input-image
  (ResponseInputImage/builder)
  {:detail ["detail" details] :image-url ["imageUrl" with-url-support]})

(defbuilder response-input-text
  (ResponseInputText/builder)
  {:text "text"})

(defbuilder response-input-file
  (ResponseInputFile/builder)
  {:filename "filename" :file-data ["fileData" with-url-support]})

(defbuilder file-citation
  (ResponseOutputText$Annotation$FileCitation/builder)
  {:file-id "fileId" :index "index"})

(defbuilder file-path
  (ResponseOutputText$Annotation$FilePath/builder)
  {:file-id "fileId" :index "index"})

(defbuilder url-citation
  (ResponseOutputText$Annotation$UrlCitation/builder)
  {:end-index "endIndex" :start-index "startIndex" :title "title" :url "url"})

(defn output-annotations
  [v]
  (let [a (ArrayList.)]
    (doseq [an v]
      (when-some [fc (:file-citation an)]
        (.add a (ResponseOutputText$Annotation/ofFileCitation (file-citation fc))))
      (when-some [uc (:url-citation an)]
        (.add a (ResponseOutputText$Annotation/ofUrlCitation (url-citation uc))))
      (when-some [fp (:file-path an)]
        (.add a (ResponseOutputText$Annotation/ofFilePath (file-path fp)))))
    a))

(defbuilder response-output-text
  (ResponseOutputText/builder)
  {:annotations ["annotations" output-annotations] :text "text"})

(defbuilder response-output-refusal
  (ResponseOutputRefusal/builder)
  {:refusal "refusal"})

(defn output-message-content
  [v]
  (let [c (ArrayList.)]
    (doseq [{:keys [output-text refusal]} v]
      (when (some? output-text)
        (.add c (ResponseOutputMessage$Content/ofOutputText (response-output-text output-text))))
      (when (some? refusal)
        (.add c (ResponseOutputMessage$Content/ofRefusal (response-output-refusal refusal)))))
    c))

(defbuilder response-output-message
  (ResponseOutputMessage/builder)
  {:id "id" :content ["content" output-message-content] :status ["status" message-status']})

(defn response-input-items
  "Convert a vector into a valid builder call to inputOfResponse.

  ```clojure
  [{:role :user :content [\"Describe this image.\"
                          {:type :image :detail :auto :image-url (io/resource \"image.png\")}]}
   {:role :user :content [{:type :text :text \"Describe this file\"}
                          {:type :file :filename \"notes.pdf\" :file-data (io/resource \"notes.pdf\")]}]
  ```"
  [entries]
  (letfn [(content-entry? [m]
            (and (contains? m :role) (= :user (:role m)) (not (string? (:content m)))))
          (easy-input-entry? [m]
            (and (contains? m :role) (string? (:content m))))
          (output-message? [m]
            (and (contains? m :id) (contains? m :content) (contains? m :status)))
          (response-input-content [{:keys [type text detail image-url filename file-data]}]
            (condp = type
              :image              (ResponseInputContent/ofInputImage (response-input-image {:detail detail :image-url image-url}))
              :file               (ResponseInputContent/ofInputFile (response-input-file {:filename filename :file-data file-data}))
              (ResponseInputContent/ofInputText (response-input-text {:text text}))))
          (content-input-item [{:keys [role content]}]
            (let [flattened (if (sequential? content) content [content])
                  c         (ArrayList.)]
              (loop [remaining flattened]
                (if (empty? remaining)
                  (response-input-item {:role role :content c})
                  (let [co   (first remaining)
                        rest (rest remaining)]
                    (cond
                      (string? co) (do
                                     (.add c (response-input-content {:type :text :text co}))
                                     (recur rest))
                      (map? co)    (do
                                     (.add c (response-input-content co))
                                     (recur rest))
                      :else        (recur (concat rest (seq co)))))))))
          (input-item [entry]
            (cond
              (easy-input-entry? entry) (ResponseInputItem/ofEasyInputMessage (easy-input-message entry))
              (output-message? entry)   (ResponseInputItem/ofResponseOutputMessage (response-output-message entry))
              :else (throw (ex-info "Invalid input item" entry))))]
    (reduce (fn [items entry]
              (cond
                (content-entry? entry) (.add items (content-input-item entry))
                :else (.add items (input-item entry)))
              items)
            (ArrayList.)
            entries)))

(defn ->java-json
  "Recursively turn a Clojure data structure into the Java‑friendly
   shape that JsonValue.from can digest:

   • keyword keys ⇒ strings
   • keyword values ⇒ strings
   • Clojure maps ⇒ java.util.HashMap
   • Clojure vectors / lists / sets ⇒ java.util.ArrayList
  "
  [x]
  (cond
    (keyword? x) (name x)

    (map? x)
    (let [m (java.util.HashMap.)]
      (doseq [[k v] x]
        (.put m (name k) (->java-json v)))
      m)

    (coll? x)
    (let [l (java.util.ArrayList.)]
      (doseq [v x] (.add l (->java-json v)))
      l)

    :else x))

(defn malli->json-schema
  "Malli schema  ➜  JSON‑Schema map with additionalProperties=false on
   every :map (object) level."
  [mal-schema]
  (-> mal-schema
      mu/closed-schema   ; <- adds {:closed true} to every nested :map
      mj/transform))

(defn schema->openai-schema ^ResponseFormatTextJsonSchemaConfig$Schema
  [schema-map]
  (let [builder (ResponseFormatTextJsonSchemaConfig$Schema/builder)]
    (doseq [[k v] schema-map]
      (.putAdditionalProperty builder k (JsonValue/from v)))
    (.build builder)))


(defn response-text-config
  "Return a ResponseTextConfig that uses the given Malli schema as a
   structured‑output format.  Accepts either

     (response-text-config ::my.ns/SomeSchema)
   or
     (response-text-config some-malli-schema \"my-format\")

   When you pass a single var, its *name* becomes the format name."
  ([schema-var]
   (let [s @(resolve schema-var)]
     (response-text-config s (name schema-var))))

  ([malli-schema format-name]
   (let [json-schema     (malli->json-schema malli-schema)        ; Malli → JSON‑schema map
         schema-java     (->java-json json-schema)          ; keyword → string, etc.
         openai-schema   (schema->openai-schema schema-java)
         format          (-> (ResponseFormatTextJsonSchemaConfig/builder)
                             (.name format-name)
                             (.type (JsonValue/from "json_schema"))
                             (.schema openai-schema)
                             (.strict true)
                             (.build))]
     (-> (ResponseTextConfig/builder)
         (.format format)
         (.build)))))

(defn chat-model
  "Supports keywords and Java enums"
  ([x]
   (chat-model x (chat-models :gpt-4o)))
  ([x default]
   (if-some [m (chat-models x)]
     m
     (or x default))))

(defn metadata
  [m]
  (let [builder   (ResponseCreateParams$Metadata/builder)
        jsonified (reduce-kv
                   (fn [m k v]
                     (assoc m (name k) (JsonValue/from v))) {} m)]
    (.putAllAdditionalProperties builder jsonified)
    (.build builder)))

(defbuilder response-create-params
  (ResponseCreateParams/builder)
  {:model ["model" chat-model]
   :input-of-response "inputOfResponse"
   :input "input"
   :max-output-tokens "maxOutputTokens"
   :metadata ["metadata" metadata]
   :format ["text" (fn [arg]
                     (if (vector? arg)
                       (apply response-text-config arg)
                       (response-text-config arg)))]})

(defn response-create-params'
  "Adds some defaults and support for Clojure only keys (like :easy-input-messages or :input-items)"
  [m]
  (let [input-of-response (cond
                            (some? (:easy-input-messages m)) (easy-input-messages (:easy-input-messages m))
                            (some? (:input-items m)) (response-input-items (:input-items m)))]
    (response-create-params (merge (cond-> {:model :gpt-4o}
                                     (some? input-of-response) (assoc :input-of-response input-of-response))
                                   (dissoc m :easy-input-messages :input-items)))))

(defn annotation->map
  [a]
  {:file-citation (when-some [fc (optional (.fileCitation a))]
                    {:file-id (.fileId fc)
                     :index   (.index fc)})
   :url-citation  (when-some [uc (optional (.urlCitation a))]
                    {:end-index (.endIndex uc)
                     :start-index (.startIndex uc)
                     :title (.title a)
                     :url   (.url a)})
   :file-path     (when-some [fp (optional (.filePath a))]
                    {:file-id (.fileId fp)
                     :index   (.index fp)})})

(defn output->map
  [o]
  {:message (when-some [msg (optional (.message o))]
              {:id (.id msg)
               :content (when-some [content (.content msg)]
                          (mapv (fn [c]
                                  {:output-text (when-some [ot (optional (.outputText c))]
                                                  {:annotations (mapv annotation->map (.annotations ot))
                                                   :text        (.text ot)})
                                   :refusal     (when-some [rf (optional (.refusal c))]
                                                  (.refusal rf))}) content))
               :status (message-status (.status msg))})})

(defn response->map
  [r]
  {:id (.id r)
   :created-at (.createdAt r)
   :error      (optional (.error r))
   :incomplete-details (some->
                        (optional (.incompleteDetails r))
                        (.reason)
                        (optional)
                        (incomplete-details-reasons))
   :instructions       (optional (.instructions r))
   :metadata           (when-some [md (optional (.metadata r))]
                         (let [m  (._additionalProperties md)
                               ks (.keySet m)]
                           (reduce #(assoc %1 (keyword %2) (str (.get m %2))) {} ks)))
   :model              (let [m (.model r)]
                         {:string (optional (.string m))
                          :model  (when-some [cm (optional (.chat m))]
                                    (if-some [kw (chat-models' cm)]
                                      kw
                                      (keyword (.asString cm))))})
   :max-output-tokens  (optional (.maxOutputTokens r))
   :previous-response-id (optional (.previousResponseId r))
   :output (mapv output->map (.output r))
   :temperature (optional (.temperature r))
   :top-p (optional (.topP r))
   :status (when-some [s (optional (.status r))]
             (response-status s))
   :text (when-some [t (optional (.text r))]
           {:format
            (when-some [f (optional (.format t))]
              {:text
               (when-some [tx (optional (.text f))]
                 (str (._type tx)))
               :json-schema
               (when-some [js (optional (.jsonSchema f))]
                 {:name   (.name js)
                  ;;; Just going to return the ResponseFormatTextJsonSchemaConfig$Schema until a better idea presents itself
                  :schema (.schema js)
                  :description (optional (.description js))
                  :strict (optional (.strict js))})})})
   :usage (when-some [usg (optional (.usage r))]
            {:input-tokens (.inputTokens usg)
             :input-tokens-details
             {:cached-tokens (-> (.inputTokensDetails usg)
                                 (.cachedTokens))}
             :output-tokens (.outputTokens usg)
             :output-tokens-details
             {:reasoning-tokens
              (-> (.outputTokensDetails usg)
                  (.reasoningTokens))}
             :total-tokens (.totalTokens usg)})
   :user  (optional (.user r))})

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
  :raw? - (optional, boolean) If true, the Java type will be returned instead of a Clojure map. Defaults to false"
  ^Response
  [& {:keys [raw?] :or {raw? false} :as m}]
  (let [params (response-create-params' m)
        client (http/get-client)]
    (-> client
        (.responses)
        (.create params)
        (cond->
         (not raw?) (response->map)))))

(defn create-response-stream
  "Nearly identical to create-response except it uses the async client. The first argument
  is a function that will receive events as they become available. All other options are the same
  as create-response with the addition of two new options:

  Additional options:
  :on-complete - (optional) - Called when the underlying future completes
  :on-error   - (optional)  - Called with any exceptions generated in the underlying future"
  [on-event & {:keys [on-complete on-error] :as m}]
  (let [client  (http/get-client-async)
        params  (response-create-params' m)
        handler (.createStreaming (.responses client) params)
        _       (.subscribe handler
                            (reify AsyncStreamResponse$Handler
                              (onNext [_ event]
                                (on-event event))))
        fut     (.onCompleteFuture handler)]
    (when (fn? on-complete)
      (.thenRun fut ^Runnable on-complete))
    (when on-error
      (.exceptionally fut
       (reify Function
         (apply [_ ex] (do (on-error ex) nil)))))))
