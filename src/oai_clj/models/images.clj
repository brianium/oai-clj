(ns oai-clj.models.images
  (:require [oai-clj.util :refer [defbuilder optional]]
            [oai-clj.http :as http])
  (:import (com.openai.models.images Image ImageGenerateParams ImageGenerateParams$Size ImageGenerateParams$Moderation ImageGenerateParams$OutputFormat ImageGenerateParams$Quality ImageGenerateParams$ResponseFormat ImageGenerateParams$Style ImageModel)))

(def image-models
  {:dall-e-2 ImageModel/DALL_E_2
   :dall-e-3 ImageModel/DALL_E_3
   :gpt-image-1 ImageModel/GPT_IMAGE_1})

(def sizes
  {:auto      ImageGenerateParams$Size/AUTO
   :1024x1024 ImageGenerateParams$Size/_1024X1024
   :1536x1024 ImageGenerateParams$Size/_1536X1024
   :1024x1536 ImageGenerateParams$Size/_1024X1536
   :256x256   ImageGenerateParams$Size/_256X256
   :512x512   ImageGenerateParams$Size/_512X512
   :1792x1024 ImageGenerateParams$Size/_1792X1024
   :1024x1792 ImageGenerateParams$Size/_1024X1792})

(def moderations
  {:low  ImageGenerateParams$Moderation/LOW
   :auto ImageGenerateParams$Moderation/AUTO})

(def output-formats
  {:png  ImageGenerateParams$OutputFormat/PNG
   :jpeg ImageGenerateParams$OutputFormat/JPEG
   :webp ImageGenerateParams$OutputFormat/WEBP})

(def qualities
  {:standard ImageGenerateParams$Quality/STANDARD
   :hd       ImageGenerateParams$Quality/HD
   :low      ImageGenerateParams$Quality/LOW
   :medium   ImageGenerateParams$Quality/MEDIUM
   :high     ImageGenerateParams$Quality/HIGH
   :auto     ImageGenerateParams$Quality/AUTO})

(def response-formats
  {:url ImageGenerateParams$ResponseFormat/URL
   :b64-json ImageGenerateParams$ResponseFormat/B64_JSON})

(def styles
  {:vivid   ImageGenerateParams$Style/VIVID
   :natural ImageGenerateParams$Style/NATURAL})

(defbuilder image-generate-params
  (ImageGenerateParams/builder)
  {:prompt "prompt"
   :model ["model" image-models]
   :size ["size" sizes]
   :moderation ["moderation" moderations]
   :n "n"
   :output-compression "outputCompression"
   :output-format ["outputFormat" output-formats]
   :quality ["quality" qualities]
   :response-format ["responseFormat" response-formats]
   :style ["style" styles]
   :user  "user"})

(defn image->map
  [^Image image]
  {:b64-json       (optional (.b64Json image))
   :revised-prompt (optional (.revisedPrompt image))
   :url            (optional (.url image))})

(defn generate-image
  [& {:keys [raw?] :or {raw? false} :as m}]
  (let [client   (http/get-client)
        params   (image-generate-params m)
        service  (.images client)
        response (.generate service params)]
    (if raw?
      response
      {:created (.created response)
       :data    (mapv image->map (or (optional (.data response)) []))})))
