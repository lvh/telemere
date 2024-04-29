(ns taoensso.telemere.tools-logging
  "Intake support for `clojure.tools.logging` -> Telemere.
  Telemere will attempt to load this ns automatically when possible."
  (:require
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.telemere.impl :as impl]
   [clojure.tools.logging  :as ctl]))

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))

(deftype TelemereLogger [logger-ns]

  clojure.tools.logging.impl/Logger
  (enabled? [_ level]
    (when-debug (println [:tools.logger/enabled? logger-ns level]))
    (impl/signal-allowed?
      {:location nil
       :kind     :log
       :id       :taoensso.telemere/tools-logging
       :level    level}))

  (write! [_ level throwable message]
    (when-debug (println [:tools.logger/write! logger-ns level]))
    (impl/signal!
      {:allow?   true ; Pre-filtered by `enabled?` call
       :location nil
       :kind     :log
       :id       :taoensso.telemere/tools-logging
       :level    level
       :error    throwable
       :msg      message})
    nil))

(deftype TelemereLoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name       [_          ] "taoensso.telemere")
  (get-logger [_ logger-ns] (TelemereLogger. (str logger-ns))))

(defn tools-logging->telemere!
  "Configures `clojure.tools.logging` to use Telemere as its logging implementation.

  Called automatically if one of the following is \"true\":
          JVM property: `clojure.tools.logging.to-telemere`
          Env variable: `CLOJURE_TOOLS_LOGGING_TO_TELEMERE`
    Classpath resource: `clojure.tools.logging.to-telemere`"
  []
  (impl/signal!
    {:kind  :event
     :level :info
     :id    :taoensso.telemere/clojure.tools.logging->telemere!
     :msg   "Enabling intake: `clojure.tools.logging` -> Telemere"})

  (alter-var-root #'clojure.tools.logging/*logger-factory*
    (fn [_] (TelemereLoggerFactory.))))

(defn tools-logging->telemere?
  "Returns true iff `clojure.tools.logging` is configured to use Telemere
  as its logging implementation."
  []
  (when-let [lf clojure.tools.logging/*logger-factory*]
    (instance? TelemereLoggerFactory lf)))

;;;;

(defn check-intake
  "Returns {:keys [present? sending->telemere? telemere-receiving?]}."
  []
  (let [sending? (tools-logging->telemere?)
        receiving?
        (and sending?
          (impl/test-intake! "`clojure.tools.logging` -> Telemere"
            #(clojure.tools.logging/info %)))]

    {:present?            true
     :sending->telemere?  sending?
     :telemere-receiving? receiving?}))

(impl/add-intake-check! :tools-logging check-intake)

(impl/on-init
  (when (enc/get-env {:as :bool} :clojure.tools.logging/to-telemere)
    (tools-logging->telemere!)))
