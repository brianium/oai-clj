# oai-clj

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brianium/oai-clj.svg)](https://clojars.org/com.github.brianium/oai-clj)

A no frills Clojure library on top of [openai-java](https://github.com/openai/openai-java)

## Goals

- Clojure maps over builders
- Clojure maps over Java result types
- Malli schemas for structured outputs
- Keywords over enum types
- Minimal syntactic sugar
- Single import

## Comparison

A simple request to the Responses API

```java
package com.openai.example;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;

public final class ResponsesExample {
    private ResponsesExample() {}

    public static void main(String[] args) {
        // Configures using one of:
        // - The `OPENAI_API_KEY` environment variable
        // - The `OPENAI_BASE_URL` and `AZURE_OPENAI_KEY` environment variables
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        ResponseCreateParams createParams = ResponseCreateParams.builder()
                .input("Tell me a story about building the best SDK!")
                .model(ChatModel.GPT_4O)
                .build();

        client.responses().create(createParams).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(outputText -> System.out.println(outputText.text()));
    }
}
```

And with Clojure:

```clojure
(require '[oai-clj.core :as oai])

(def response
  (oai/create-response :input "Tell me a story about building the best SDK!"))
  
(-> (:output response)
    (first)
    (:message)
    (:content)
    (first)
    (:output-text)
    (:text))
```

Or image generation:

```java
package com.openai.example;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import java.io.IOException;

public final class ImageGenerationExample {
    private ImageGenerationExample() {}

    public static void main(String[] args) throws IOException {
        // Configures using one of:
        // - The `OPENAI_API_KEY` environment variable
        // - The `OPENAI_BASE_URL` and `AZURE_OPENAI_KEY` environment variables
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        ImageGenerateParams imageGenerateParams = ImageGenerateParams.builder()
                .responseFormat(ImageGenerateParams.ResponseFormat.URL)
                .prompt("Two cats playing ping-pong")
                .model(ImageModel.DALL_E_2)
                .size(ImageGenerateParams.Size._512X512)
                .n(1)
                .build();

        client.images().generate(imageGenerateParams).data().orElseThrow().stream()
                .flatMap(image -> image.url().stream())
                .forEach(System.out::println);
    }
}
```

With clojure:

```clojure
(require '[oai-clj.core :as oai])

(def image-response
  (oai/generate-image
    :prompt "Two cats playing ping-pong"
    :model  :dall-e-2
    :size   :512x512
    :n 1))

(:url (first (:data image-response)))
```

## Structured Outputs Via Malli

Define a schema in Malli and reference it by Var or a tuple. `:format` is a non-standard key and only meaningful to `oai-clj`.

```clojure
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
```

Note:
In addition to Malli schemas, plain Clojure maps are also supported for structured outputs. This is particularly useful when the schema is not known ahead of time - such as when a different LLM is generating the schema.

Hoping to apply the same support to tool use when that gets implemented.

## General guidelines

In general, keyword names in Clojure will attempt a kebab cased keyword variant of the builder method. i.e `:file` will map to `(.file (TranscriptionCreateParams/builder))`.
The same is true for Java result types.

`java.util.Optional` types are treated as such: the corresponding value in the map will just be `nil` if there is no value, otherwise it will have the appropriate value.

Some "extra" keys are provided (mostly in the responses api) to make things a little easier for Clojurists. Look at the examples of using `create-response` with `:input-items` and `:easy-input-items`.

See [dev.clj](dev/dev.clj) for examples.

## Customizing clients

Clients are pulled from dynamic vars set in `oai-clj.http`. The default behavior is to instantiate the the Java clients via env. This expects the `OPENAI_API_KEY` environment variable mentioned in Java docs. If further customization is needed, those dynamic vars can be overwritten.

## Disclaimer

Some things might not be supported, or could be more Clojure-y. Pull requests welcome.
