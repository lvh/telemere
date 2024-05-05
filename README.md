<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**Documentation**](#documentation) | [Latest releases](#latest-releases) | [Slack channel][]

# <img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/telemere-logo.svg" alt="Telemere logo" width="360"/>

### Structured telemetry library for Clojure/Script

**Telemere** is a next-generation replacement for [Timbre](https://www.taoensso.com/timbre). It handles **structured and traditional logging**, **tracing**, and **basic performance monitoring** with a simple unified API.

It helps enable Clojure/Script systems that are **observable**, **robust**, and **debuggable** - and it represents the refinement and culmination of ideas brewing over 12+ years in [Timbre](https://www.taoensso.com/timbre), [Tufte](https://www.taoensso.com/tufte), [Truss](https://www.taoensso.com/truss), etc.

> [Terminology] *Telemetry* derives from the Greek *tele* (remote) and *metron* (measure). It refers to the collection of *in situ* (in position) data, for transmission to other systems for monitoring/analysis. *Logs* are the most common form of software telemetry. So think of telemetry as the *superset of logging-like activities* that help monitor and understand (software) systems.

## Latest release/s

- `v1.0.0-beta5`: [release info](../../releases/tag/v1.0.0-beta5)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Why Telemere?

#### Ergonomics

- Elegant, lightweight API that's **easy to use**, **easy to configure**, and **deeply flexible**.
- **Sensible defaults** to make getting started **fast and easy**.
- Extensive **beginner-oriented** [documentation][GitHub wiki], [docstrings](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso), and error messages.

#### Interop

- 1st-class **out-the-box interop** with [SLF4J v2](https://www.slf4j.org/), [clojure.tools.logging](https://github.com/clojure/tools.logging), [OpenTelemetry](https://opentelemetry.io/), and [Tufte](https://www.taoensso.com/tufte).
- Included [shim](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre) for easy/gradual [migration from Timbre](../../wiki/5-Migrating).
- Included [handlers](../../wiki/4-Handlers#included-handlers) for consoles, files, email, Redis, Slack, TCP/UDP sockets, Logstash, etc.

#### Scaling

- Hyper-optimized and **blazing fast**, see [benchmarks](#benchmarks).
- An API that **scales comfortably** from the smallest disposable code, to the most massive and complex real-world production environments.

#### Flexibility

- Config via plain **Clojure vals and fns** for easy customization, composition, and REPL debugging.
- Unmatched **environmental config** support (JVM properties, environment variables, or classpath resources).
- Expressive **per-call** and **per-handler** filtering at both **runtime** and **compile-time**.
- Filter by namespace and id pattern, level, **level by namespace pattern**, etc.
- **Sampling**, **rate-limiting**, and **back-pressure monitoring**.
- **Fully configurable a/sync dispatch** (blocking, dropping, sliding, etc.).

## Video demo

See for intro and usage:

<a href="https://www.youtube.com/watch?v=-L9irDG8ysM" target="_blank">
 <img src="https://img.youtube.com/vi/-L9irDG8ysM/maxresdefault.jpg" alt="Telemere demo video" width="480" border="0" />
</a>

## Quick examples

```clojure
(require '[taoensso.telemere :as t])

;; Start simple
(t/log! "This will send a `:log` signal to the Clj/s console")
(t/log! :info "This will do the same, but only when the current level is >= `:info`")

;; Easily construct messages
(let [x "constructed"] (t/log! :info ["Here's a" x "message!"]))

;; Attach an id
(t/log! {:level :info, :id ::my-id} "This signal has an id")

;; Attach arb user data
(t/log! {:level :info, :data {:x :y}} "This signal has structured data")

;; Capture for debug/testing
(t/with-signal (t/log! "This will be captured"))
;; => {:keys [location level id data msg_ ...]}

;; `:let` bindings available to `:data` and message, only paid for
;; when allowed by minimum level and other filtering criteria
(t/log!
  {:level :info
   :let [expensive-metric1 (last (for [x (range 100), y (range 100)] (* x y)))]
   :data {:metric1 expensive-metric1}}
  ["Message with metric value:" expensive-metric1])

;; With sampling 50% and 1/sec rate limiting
(t/log!
  {:sample-rate 0.5
   :rate-limit  {"1 per sec" [1 1000]}}
  "This signal will be sampled and rate limited")

;;; A quick taste of filtering...

(t/set-ns-filter! {:deny "taoensso.*" :allow "taoensso.sente.*"}) ; Set namespace filter
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})      ; Set id        filter

(t/set-min-level!       :warn) ; Set minimum level
(t/set-min-level! :log :debug) ; Set minimul level for `:log` signals

;; Set minimum level for `:event` signals originating in the "taoensso.sente.*" ns
(t/set-min-level! :event "taoensso.sente.*" :warn)
```

See [examples.cljc](https://github.com/taoensso/telemere/blob/master/examples.cljc) for more REPL-ready snippets.

## API overview

See relevant docstrings (links below) for usage info-

### Signal creators

| Name                                                                                                        | Signal kind | Main arg | Optional arg   | Returns                      |
| :---------------------------------------------------------------------------------------------------------- | :---------- | :------- | :------------- | :--------------------------- |
| [`log!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#log!)                     | `:log`      | `msg`    | `opts`/`level` | Signal allowed?              |
| [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!)                 | `:event`    | `id`     | `opts`/`level` | Signal allowed?              |
| [`error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#error!)                 | `:error`    | `error`  | `opts`/`id`    | Given error                  |
| [`trace!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#trace!)                 | `:trace`    | `form`   | `opts`/`id`    | Form result                  |
| [`spy!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#spy!)                     | `:spy`      | `form`   | `opts`/`level` | Form result                  |
| [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) | `:error`    | `form`   | `opts`/`id`    | Form value or given fallback |
| [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!)               | `<arb>`     | `opts`   | -              | Depends on opts              |

### Signal filters

| Global                                                                                                          | Dynamic                                                                                                         | Filters by                                                |
| :-------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------- |
| [`set-kind-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-kind-filter!) | [`with-kind-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-kind-filter) | Signal kind (`:log`, `:event`, etc.)                      |
| [`set-ns-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-ns-filter!)     | [`with-ns-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-ns-filter)     | Signal namespace                                          |
| [`set-id-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-id-filter!)     | [`with-id-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-id-filter)     | Signal id                                                 |
| [`set-min-level`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-min-level)       | [`with-min-level`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-min-level)     | Signal level (minimum can be specified by kind and/or ns) |

### Internal help

| Var                                                                                                                         | Help with                                     |
| :-------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------- |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)     | List of signal creators                       |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)       | Options for signal creators                   |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)       | Signal map content                            |
| [`help:signal-flow`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-flow)             | Ordered flow from signal creation to handling |
| [`help:signal-filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-filters)       | API for configuring signal filters            |
| [`help:signal-handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers)     | API for configuring signal handlers           |
| [`help:signal-formatters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-formatters) | Signal formatters for use by handlers         |

### Example handler output

```clojure
(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") =>
```

#### Clj console handler

String output:

```
2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
    data: {:x1 :x2}
```

#### Cljs console handler

Chrome console:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console.png" alt="Default ClojureScript console handler output" width="640"/>

#### Cljs raw console handler

Chrome console, with [cljs-devtools](https://github.com/binaryage/cljs-devtools):

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console-raw.png" alt="Raw ClojureScript console handler output" width="640"/>

#### Clj file handler

MacOS terminal:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-clj-file.png" alt="Default Clojure file handler output" width="640"/>

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference: [cljdoc][cljdoc docs] or [Codox][Codox docs]
- Support: [Slack channel][] or [GitHub issues][]

## Observability tips

See [here](../../wiki/7-Tips) for general advice re: building and maintaining observable Clojure/Script systems.

## Benchmarks

Telemere is **highly optimized** and offers terrific performance at any scale:

| Compile-time filtering? | Runtime filtering? | Time? | Trace? | nsecs
| :-: | :-: | :-: | :-: | --:
| ✓ (elide) | - | - | - | 0
| - | ✓ | - | - | 200
| - | ✓ | ✓ | - | 280
| - | ✓ | ✓ | ✓ | 650

Measurements:

- Are **~nanoseconds per signal call** (= milliseconds per 1e6 calls)
- Exclude handler runtime (which depends on handler/s, is usually async)
- Taken on a 2020 Macbook Pro M1, running OpenJDK 21

**Tip**: Telemere offers extensive per-call and per-handler **filtering**, **sampling**, and **rate-limiting**. Use these to ensure that you're not capturing useless/low-value information in production. See [here](../../wiki/7-Tips) for more tips!

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2023-2024 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack channel]: https://www.taoensso.com/telemere/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox docs]:   https://taoensso.github.io/telemere/
[cljdoc docs]: https://cljdoc.org/d/com.taoensso/telemere/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/telemere.svg
[Clojars URL]: https://clojars.org/com.taoensso/telemere

[Main tests SVG]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml
