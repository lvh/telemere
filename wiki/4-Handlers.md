Signal handlers process created signals to *do something with them* (analyse them, write them to console/file/queue/db, etc.).

# Included handlers

The following signal handlers are currently included out-the-box:

| Name                                                                                                                                                     | Platform | Output target                                                                                                  | Output format                                                          |
| :------------------------------------------------------------------------------------------------------------------------------------------------------- | :------- | :------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------- |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Clj      | `*out*` or `*err*`                                                                                             | Formatted string [1]                                                   |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Cljs     | Browser console                                                                                                | Formatted string [1]                                                   |
| [`handler:console-raw`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console-raw)                                    | Cljs     | Browser console                                                                                                | Raw signal data [2]                                                    |
| [`handler:file`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:file)                                                  | Clj      | File/s on disk                                                                                                 | Formatted string [1]                                                   |
| [`handler:postal`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal)                                       | Clj      | Email (via [postal](https://github.com/drewr/postal))                                                          | Formatted string [1]                                                   |
| [`handler:open-telemetry-logger`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry-logger) | Clj      | [OpenTelemetry](https://opentelemetry.io/) [Java client](https://github.com/open-telemetry/opentelemetry-java) | [LogRecord](https://opentelemetry.io/docs/specs/otel/logs/data-model/) |

- \[1] [Configurable](https://cljdoc.org/d/com.taoensso/telemere/1.0.0-beta3/api/taoensso.telemere#help:signal-formatters): human-readable (default), [edn](https://github.com/edn-format/edn), [JSON](https://www.json.org/), etc.
- \[2] For use with browser formatting tools like [cljs-devtools](https://github.com/binaryage/cljs-devtools).
- See relevant docstrings (links above) for features, usage, etc.
- See section [8-Community](8-Community.md) for more (community-supported) handlers.
- If there's other handlers you'd like to see, feel free to [ping me](https://github.com/taoensso/telemere/issues), or ask on the [`#telemere` Slack channel](https://www.taoensso.com/telemere/slack). It helps to know what people most need!

# Configuring handlers

There's two kinds of config relevant to all signal handlers:

1. **Dispatch** opts (common to all handlers), and
2. **Handler-specific** opts

## Dispatch opts

Dispatch opts includes dispatch priority, handler filtering, handler middleware, queue semantics, back-pressure opts, etc.

This is all specified when calling [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) - and documented there.

Note that handler middleware in particular is an often overlooked but powerful feature, allowing you to arbitrarily transform and/or filter every [signal map](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) before it is given to the handler.

## Handler-specific opts

Handler-specific opts are specified when calling a particular **handler constructor** (like [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CONSOLE/api/taoensso.telemere#handler:console)) - and documented by the constructor.

Note that it's common for Telemere handlers to be customized by providing *Clojure/Script functions* to the relevant handler constructor call.

See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.

### Example

The standard Clj/s console handler ([`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)) writes signals **as strings** to `*out*`/`*err` or browser console.

By default it writes formatted strings intended for human consumption:

```clojure
;; Create a test signal
(def my-signal
  (t/with-signal
    (t/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (t/handler:console))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; =>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}
```

To instead writes signals as edn:

```clojure
;; Create console which writes edn
(def my-handler
  (t/handler:console
    {:format-signal-fn (taoensso.telemere.utils/format-signal->edn-fn)}))

(my-handler my-signal) ; =>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}
```

To instead writes signals as JSON:

```clojure
;; Create console which writes JSON
(def my-handler
  (t/handler:console
    {:format-signal-fn
     (taoensso.telemere.utils/format-signal->json-fn
       {:pr-json-fn jsonista.core/write-value-as-string})}))

(my-handler my-signal) ; =>
;; {"inst":"2024-04-11T10:54:57.202869Z","msg_":"My message","ns":"examples", ...}
```

Note that when writing JSON with Clojure, you *must* specify a `pr-json-fn`. This lets you plug in the JSON serializer of your choice ([jsonista](https://github.com/metosin/jsonista) is my default recommendation).

# Managing handlers

See [`help:signal-handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers) for info on handler management.

## Managing handlers on startup

Want to add or remove a particular handler when your application starts?

Just make an appropriate call to [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) or [`remove-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#remove-handler!).

## Environmental config

If you want to manage handlers **conditionally** based on **environmental config** (JVM properties, environment variables, or classpath resources) - Telemere provides the highly flexible [`get-env`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-env) util.

Use this to easily define your own arbitrary cross-platform config, and make whatever conditional handler management decisions you'd like.

# Writing handlers

Writing your own signal handlers for Telemere is straightforward, and a reasonable choice if you prefer customizing behaviour that way, or want to write signals to a DB/format/service for which a ready-made handler isn't available.

Remember that signals are just plain Clojure/Script [maps](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content), and handlers just plain Clojure/Script functions that do something with those maps.

Here's a simple Telemere handler:

```clojure
(fn my-handler [signal] (println signal))
```

For more complex cases, or for handlers that you want to make available for use by other folks, here's the general template that Telemere uses for all its included handlers:

```clojure
(defn handler:my-handler ; Note naming convention
  "Returns a (fn handler [signal] that:
    - Takes a Telemere signal.
    - Does something with it.

  Options:
    `:option1` - Description
    `:option2` - Description"

  ([] (handler:my-handler nil)) ; Use default opts
  ([{:as constructor-opts}]

   ;; Do option validation and expensive prep *outside* returned handler
   ;; fn whenever possible - i.e. at (one-off) construction time rather than
   ;; at every handler call.
   (let []

     (fn a-handler:my-handler ; Note naming convention

       ;; Shutdown arity - called by Telemere exactly once when the handler is
       ;; to be shut down. This is your opportunity to finalize/free resources, etc.
       ([])

       ;; Main arity - called by Telemere whenever the handler should handle the
       ;; given signal. Never called after shutdown.
       ([signal]
        ;; TODO Do something with given signal
        )))))
```

- See [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) for signal map content.
- See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.
- See section [8-Community](8-Community.md) for PRs to link to community-authored handlers.

# Example output

```clojure
(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") =>
```

## Clj console handler

String output:

```
2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
    data: {:x1 :x2}
```

## Cljs console handler

Chrome console:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console.png" alt="Default ClojureScript console handler output" width="640"/>

## Cljs raw console handler

Chrome console, with [cljs-devtools](https://github.com/binaryage/cljs-devtools):

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console-raw.png" alt="Raw ClojureScript console handler output" width="640"/>

## Clj file handler

MacOS terminal:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-clj-file.png" alt="Default Clojure file handler output" width="640"/>