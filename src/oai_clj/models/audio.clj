(ns oai-clj.models.audio
  (:require [oai-clj.http :as http]
            [oai-clj.util :refer [defbuilder optional]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.util ArrayList)
           (java.net URL)
           (com.openai.core MultipartField)
           (com.openai.models.audio AudioModel AudioResponseFormat)
           (com.openai.models.audio.speech SpeechModel SpeechCreateParams SpeechCreateParams$Voice SpeechCreateParams$ResponseFormat)
           (com.openai.models.audio.transcriptions TranscriptionCreateParams TranscriptionCreateParams$TimestampGranularity)))

(def voices
  {:alloy   SpeechCreateParams$Voice/ALLOY
   :ash     SpeechCreateParams$Voice/ASH
   :ballad  SpeechCreateParams$Voice/BALLAD
   :coral   SpeechCreateParams$Voice/CORAL
   :echo    SpeechCreateParams$Voice/ECHO
   :fable   SpeechCreateParams$Voice/FABLE
   :onyx    SpeechCreateParams$Voice/ONYX
   :nova    SpeechCreateParams$Voice/NOVA
   :sage    SpeechCreateParams$Voice/SAGE
   :shimmer SpeechCreateParams$Voice/SHIMMER
   :verse   SpeechCreateParams$Voice/VERSE})

(def speech-formats
  {:mp3  SpeechCreateParams$ResponseFormat/MP3
   :opus SpeechCreateParams$ResponseFormat/OPUS
   :wav  SpeechCreateParams$ResponseFormat/WAV
   :pcm  SpeechCreateParams$ResponseFormat/PCM
   :flac SpeechCreateParams$ResponseFormat/FLAC})

(def speech-models
  {:tts-1 SpeechModel/TTS_1
   :tts-1-hd SpeechModel/TTS_1_HD
   :gpt-4o-mini-tts SpeechModel/GPT_4O_MINI_TTS})

(def audio-models
  "The audio models available for transcription"
  {:whisper-1              AudioModel/WHISPER_1
   :gpt-4o-transcribe      AudioModel/GPT_4O_TRANSCRIBE
   :gpt-4o-mini-transcribe AudioModel/GPT_4O_MINI_TRANSCRIBE})

(def audio-response-formats
  {:json AudioResponseFormat/JSON
   :text AudioResponseFormat/TEXT
   :srt AudioResponseFormat/SRT
   :verbose-json AudioResponseFormat/VERBOSE_JSON
   :vtt AudioResponseFormat/VTT})

(def timestamp-granularities
  {:word    TranscriptionCreateParams$TimestampGranularity/WORD
   :segment TranscriptionCreateParams$TimestampGranularity/SEGMENT})

(defbuilder speech-create-params
  (SpeechCreateParams/builder)
  {:input "input"
   :voice ["voice" voices]
   :response-format ["responseFormat" speech-formats]
   :model ["model" speech-models]
   :speed "speech"
   :instructions "instructions"})

(defn create-speech
  [& {:keys [model response-format voice] :or {model :gpt-4o-mini-tts response-format :wav voice :verse} :as m}]
  (let [params   (speech-create-params (merge m {:model model :response-format response-format :voice voice}))
        client   (http/get-client)
        service  (.speech (.audio client))
        response (.create service params)]
    (.body response)))

(defbuilder multipart-field
  (MultipartField/builder)
  {:filename "filename" :value "value"})

(defn url->filename
  "Given a java.net.URL, returns the last path segment as a filename.
   If the path is empty or ends in `/`, returns the URL's host instead."
  [^URL url]
  (let [path     (.getPath url)
        ;; split on '/', drop any empty segments
        segments (->> (str/split path #"/")
                      (remove str/blank?))
        name     (last segments)]
    (if (and name (not (str/ends-with? path "/")))
      name
      ;; fallback to host if no usable segment
      (.getHost url))))

(defn with-url-support
  [file]
  (cond
    (map? file) (multipart-field file)
    (instance? URL file)
    (let [s (io/input-stream file)
          filename (url->filename file)]
      (multipart-field {:filename filename :value s}))
    :else (throw (ex-info "Invalid file type" {:file file}))))

(defbuilder transcription-create-params
  (TranscriptionCreateParams/builder)
  {:file            ["file" with-url-support]
   :model           ["model" audio-models]
   :language        "language"
   :prompt          "prompt"
   :temperature     "temperature"
   :response-format ["responseFormat" audio-response-formats]
   :timestamp-granularities ["timestampGranularities" (fn [gs]
                                                        (let [l (ArrayList.)]
                                                          (doseq [g gs]
                                                            (.add l (timestamp-granularities g)))
                                                          l))]})

(defn transcription->map
  [r]
  {:transcription (when-some [tr (optional (.transcription r))]
                    {:text (.text tr)
                     :logprobs
                     (when-some [lps (optional (.logprobs tr))]
                       (mapv (fn [lp]
                               {:token   (optional (.token lp))
                                :bytes   (optional (.bytes lp))
                                :logprob (optional (.logprob lp))}) lps))})
   :verbose (when-some [vb (optional (.verbose r))]
              {:duration (.duration vb)
               :language (.language vb)
               :text     (.text vb)
               :segments (when-some [sms (optional (.segments vb))]
                           (mapv (fn [seg]
                                   {:id (.id seg)
                                    :avg-logprob (.avgLogprob seg)
                                    :compression-ratio (.compressionRatio seg)
                                    :end (.end seg)
                                    :no-speech-prob (.noSpeechProb seg)
                                    :seek (.seek seg)
                                    :start (.start seg)
                                    :temperature (.temperature seg)
                                    :text (.text seg)
                                    :tokens (mapv identity (.tokens seg))}) sms))
               :words (when-some [wds (optional (.words vb))]
                        (mapv (fn [wd]
                                {:end (.end wd)
                                 :start (.start wd)
                                 :word (.word wd)}) wds))})})

(defn transcribe
  [& {:keys [raw?] :or {raw? false} :as m}]
  (let [params   (transcription-create-params (cond-> m
                                                (nil? (:model m)) (assoc :model :whisper-1)))
        client   (http/get-client)
        service  (.transcriptions (.audio client))
        response (.create service params)]
    (if raw?
      response
      (transcription->map response))))
