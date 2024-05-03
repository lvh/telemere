(ns ^:no-doc taoensso.telemere.impl
  "Private ns, implementation detail.
  Signal design shared by: Telemere, Tufte, Timbre."
  (:refer-clojure :exclude [binding])
  (:require
   [taoensso.encore         :as enc :refer [binding have have?]]
   [taoensso.encore.signals :as sigs])

  #?(:cljs
     (:require-macros
      [taoensso.telemere.impl :refer [with-signal]])))

(comment
  (remove-ns 'taoensso.telemere.impl)
  (:api (enc/interns-overview)))

#?(:clj
   (enc/declare-remote ; For macro expansions
     ^:dynamic taoensso.telemere/*ctx*
     ^:dynamic taoensso.telemere/*middleware*))

;;;; Utils

#?(:clj (defmacro threaded [& body] `(let [t# (Thread. (fn [] ~@body))] (.start t#) t#)))

#?(:clj
   (defmacro on-init [& body]
     (let [sym        (with-meta '__on-init {:private true})
           compiling? (if (:ns &env) false `*compile-files*)]
       `(defonce ~sym (when-not ~compiling? ~@body nil)))))

(comment (macroexpand-1 '(on-init (println "foo"))))

;;;; Config

#?(:clj
   (let [base        (enc/get-env {:as :edn} :taoensso.telemere/ct-filters<.platform><.edn>)
         kind-filter (enc/get-env {:as :edn} :taoensso.telemere/ct-kind-filter<.platform><.edn>)
         ns-filter   (enc/get-env {:as :edn} :taoensso.telemere/ct-ns-filter<.platform><.edn>)
         id-filter   (enc/get-env {:as :edn} :taoensso.telemere/ct-id-filter<.platform><.edn>)
         min-level   (enc/get-env {:as :edn} :taoensso.telemere/ct-min-level<.platform><.edn>)]

     (enc/defonce ct-sig-filter
       "`SigFilter` used for compile-time elision, or nil."
       (sigs/sig-filter
         {:kind-filter (or kind-filter (get base :kind-filter))
          :ns-filter   (or ns-filter   (get base :ns-filter))
          :id-filter   (or id-filter   (get base :id-filter))
          :min-level   (or min-level   (get base :min-level))}))))

(let [base        (enc/get-env {:as :edn}                 :taoensso.telemere/rt-filters<.platform><.edn>)
      kind-filter (enc/get-env {:as :edn}                 :taoensso.telemere/rt-kind-filter<.platform><.edn>)
      ns-filter   (enc/get-env {:as :edn}                 :taoensso.telemere/rt-ns-filter<.platform><.edn>)
      id-filter   (enc/get-env {:as :edn}                 :taoensso.telemere/rt-id-filter<.platform><.edn>)
      min-level   (enc/get-env {:as :edn, :default :info} :taoensso.telemere/rt-min-level<.platform><.edn>)]

  (enc/defonce ^:dynamic *rt-sig-filter*
    "`SigFilter` used for runtime filtering, or nil."
    (sigs/sig-filter
      {:kind-filter (or kind-filter (get base :kind-filter))
       :ns-filter   (or ns-filter   (get base :ns-filter))
       :id-filter   (or id-filter   (get base :id-filter))
       :min-level   (or min-level   (get base :min-level))})))

(comment (enc/get-env {:as :edn, :return :explain} :taoensso.telemere/rt-filters<.platform><.edn>))

;;;; Context (optional arb app-level state)
;; taoensso.telemere/*ctx*

(defn update-ctx
  "Returns `new-ctx` given `old-ctx` and an update map or fn."
  [old-ctx update-map-or-fn]
  (enc/cond
    (nil? update-map-or-fn)                   old-ctx
    (map? update-map-or-fn) (enc/fast-merge   old-ctx update-map-or-fn) ; Before ifn
    (ifn? update-map-or-fn) (update-map-or-fn old-ctx)
    :else
    (enc/unexpected-arg! update-map-or-fn
      {:context  `update-ctx
       :param    'update-map-or-fn
       :expected '#{nil map fn}})))

;;;; Unique IDs (UIDs)

(enc/def* nanoid-readable (enc/rand-id-fn {:chars :nanoid-readable, :len 23}))

#?(:clj
   (defn- parse-uid-form [uid-form]
     (when   uid-form
       (case uid-form
         :auto/uuid            `(enc/uuid)
         :auto/uuid-str        `(enc/uuid-str)
         :auto/nanoid          `(enc/nanoid)
         :auto/nanoid-readable `(nanoid-readable)
         uid-form))))

(comment
  (enc/qb 1e6 ; [161.36 184.69 274.53 468.67]
    (enc/uuid)
    (enc/uuid-str)
    (enc/nanoid)
    (nanoid-readable)))

;;;; Messages

(deftype MsgSkip   [])
(deftype MsgSplice [args])

(def ^:public msg-skip
  "For use within signal message vectors.
  Special value that will be ignored (no-op) when creating message.
  Useful for conditionally skipping parts of message content, etc.:

    (signal! {:msg [\"Hello\" (if <cond> <then> msg-skip) \"world\"] <...>}) or
    (log!          [\"Hello\" (if <cond> <then> msg-skip) \"world\"]), etc.

      %> {:msg_ \"Hello world\" <...>}"

  (MsgSkip.))

(defn ^:public msg-splice
  "For use within signal message vectors.
  Wraps given arguments so that they're spliced when creating message.
  Useful for conditionally splicing in extra message content, etc.:

    (signal! {:msg [(when <cond> (msg-splice [\"Username:\" \"Steve\"])) <...>]}) or
    (log!          [(when <cond> (msg-splice [\"Username:\" \"Steve\"]))])

      %> {:msg_ \"Username: Steve\"}"

  [args] (MsgSplice. args))

(let [;; xform (map #(if (nil? %) "nil" %))
      xform
      (fn [rf]
        (let [;; Protocol-based impln (extensible but ~20% slower)
              ;; rf* (fn rf* [acc in] (reduce-msg-arg in acc rf))
              rf*
              (fn rf* [acc in]
                (enc/cond
                  (instance? MsgSplice in) (reduce rf* acc (.-args ^MsgSplice in))
                  (instance? MsgSkip   in)             acc
                  (nil?                in)         (rf acc "nil")
                  :else                            (rf acc in)))]
          (fn
            ([      ] (rf))
            ([acc   ] (rf  acc))
            ([acc in] (rf* acc in)))))]

  (defn signal-msg
    "Returns string formed by joining all args with \" \" separator,
    rendering nils as \"nil\". Supports `msg-skip`, `msg-splice`.

    API intended to be usefully different to `str`:
      -        `str`: no   spacers, skip nils, no     splicing
      - `signal-msg`: auto spacers, show nils, opt-in splicing"

    {:tag #?(:clj 'String :cljs 'string)}
    [args] (enc/str-join " " xform args)))

(comment
  (enc/qb 2e6 ; [305.61 625.35]
    (str         "a" "b" "c" nil :kw)                         ; "abc:kw"
    (signal-msg ["a" "b" "c" nil :kw (msg-splice ["d" "e"])]) ; "a b c nil :kw d e"
    ))

#?(:clj
   (defn- parse-msg-form [msg-form]
     (when msg-form
       (enc/cond
         (string? msg-form) msg-form
         (vector? msg-form)
         (enc/cond
           (empty?           msg-form) nil
           :let [[m1 & more] msg-form]
           (and (string? m1) (nil? more)) m1
           :else `(delay (signal-msg ~msg-form)))

         ;; Auto delay-wrap (user should never delay-wrap!)
         ;; :else `(delay ~msg-form)

         ;; Leave user to delay-wrap when appropriate (document)
         :else msg-form))))

(defn default-trace-msg
  [form value error nsecs]
  (if error
    (str form " !> " (enc/ex-type error))
    (str form " => " value)))

(comment
  (default-trace-msg "(+ 1 2)" 3   nil               12345)
  (default-trace-msg "(+ 1 2)" nil (Exception. "Ex") 12345))

;;;; Tracing (optional flow tracking)

(enc/def* ^:dynamic *trace-parent* "?TraceParent" nil)
(defrecord TraceParent [id uid])

#?(:clj
   (defmacro with-tracing
     "Wraps `form` with tracing iff const boolean `trace?` is true."
     [trace? id uid form]
     (if trace?
       `(binding [*trace-parent* (TraceParent. ~id ~uid)] ~form)
       (do                                                 form))))

(comment
  [(enc/qb 1e6   (with-tracing true  :id1 :uid1 "form")) ; 257.5
   (macroexpand '(with-tracing false :id1 :uid1 "form"))
   (macroexpand '(with-tracing true  :id1 :uid1 "form"))])

;;;; Main types

(defrecord Signal
  ;; Telemere's main public data type, we avoid nesting and duplication
  [^long schema inst uid,
   location ns line column file #?(:clj thread),
   sample-rate, kind id level, ctx parent,
   data msg_ error run-form run-val,
   end-inst run-nsecs kvs]

  Object (toString [sig] (str "#" `Signal (into {} sig))))

(do     (enc/def-print-impl [sig Signal] (str "#" `Signal (pr-str (into {} sig)))))
#?(:clj (enc/def-print-dup  [sig Signal] (str "#" `Signal (pr-str (into {} sig))))) ; NB intentionally verbose, to support extra keys

(comment
  (def s1 (with-signal (signal! {:level :info, :my-k1 :my-v1})))
  (read-string (str    (assoc s1 :my-k2 :my-v2)))
  (read-string (pr-str (assoc s1 :my-k2 :my-v2)))
  (read-string (binding [*print-dup* true] (pr-str (assoc s1 :my-k2 :my-v2))))

  (defrecord MyRec [x])
  (read-string ; Non-verbose will fail on any extra keys
    (binding [*print-dup* true, *verbose-defrecords* false]
      (pr-str (assoc (MyRec. :x) :y :y)))))

(deftype #_defrecord WrappedSignal
  ;; Internal type to implement `sigs/IFilterableSignal`,
  ;; incl. lazy + cached `signal-value_` field.
  [ns kind id level signal-value_]
  sigs/IFilterableSignal
  (allow-signal? [_ sig-filter] (sig-filter ns kind id level))
  (signal-value  [_ handler-context]
    (let [sig-val @signal-value_]
      (or
        (when-let [handler-sample-rate
                   (when-let [^taoensso.encore.signals.HandlerContext hc handler-context]
                     (.-sample-rate hc))]

          (when (map? sig-val)
            ;; Replace call sample rate with combined (call * handler) sample rate
            (assoc    sig-val :sample-rate
              (*
                (double handler-sample-rate)
                (double (or (get sig-val :sample-rate) 1.0))))))
        sig-val))))

;;;; Handlers

(enc/defonce ^:dynamic *sig-spy*      "To support `with-signals`, etc." nil)
(enc/defonce ^:dynamic *sig-handlers* "?[<wrapped-handler-fn>]"         nil)

(defn force-msg-in-sig [sig]
  (if-not  (map? sig)
    sig
    (if-let [e (find sig :msg_)]
      (assoc sig :msg_ (force (val e)))
      (do    sig))))

#?(:clj
   (defmacro ^:public with-signal
     "Experimental.
     Executes given form, trapping errors. Returns the LAST signal created by form.
     Useful for tests/debugging.

     Options:
       `trap-signals?` (default: false)
         Should ALL signals created by form be trapped to prevent normal dispatch
         to registered handlers?

       `raw-msg?` (default: false)
         Should delayed `:msg_` in returned signal be retained as-is?
         Delay is otherwise replaced by realized string.

     See also `with-signals`."
     ([                       form] `(with-signal false false          ~form))
     ([         trap-signals? form] `(with-signal false ~trap-signals? ~form))
     ([raw-msg? trap-signals? form]
      `(let [sig_# (volatile! nil)]
         (binding [*sig-spy* [sig_# :last-only ~trap-signals?]]
           (enc/try* ~form (catch :all _#)))

         (if ~raw-msg?
           (do               @sig_#)
           (force-msg-in-sig @sig_#))))))

#?(:clj
   (defmacro ^:public with-signals
     "Experimental.
     Like `with-signal` but returns [[<form-value> <form-error>] [<signal1> ...]].
     Useful for tests/debugging."
     ([                        form] `(with-signals false false          ~form))
     ([          trap-signals? form] `(with-signals false ~trap-signals? ~form))
     ([raw-msgs? trap-signals? form]
      `(let [sigs_# (volatile! nil)
             form-result#
             (binding [*sig-spy* [sigs_# (not :last-only) ~trap-signals?]]
               (enc/try*
                 (do            [~form nil])
                 (catch :all t# [nil    t#])))

             sigs#
             (if ~raw-msgs?
               (do                    @sigs_#)
               (mapv force-msg-in-sig @sigs_#))]

         [form-result# (not-empty sigs#)]))))

(defn dispatch-signal!
  "Dispatches given signal to registered handlers, supports `with-signal/s`."
  [signal]
  (or
    (when-let [[v_ last-only? trap-signals?] *sig-spy*]
      (let [sv (sigs/signal-value signal nil)]
        (if last-only?
          (vreset! v_                  sv)
          (vswap!  v_ #(conj (or % []) sv))))
      (when trap-signals? :stop))

    (sigs/call-handlers! *sig-handlers* signal)))

;;;; Signal constructor

(deftype RunResult [value error ^long run-nsecs]
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [_] (if error (throw error) value))
  (#?(:clj invoke :cljs -invoke) [_ signal_]
    (if error
      (throw
        (ex-info "Signal `:run` form error"
          (enc/try*
            (do           {:taoensso.telemere/signal (force signal_)})
            (catch :all t {:taoensso.telemere/signal-error t}))
          error))
      value)))

(defn new-signal
  "Returns a new `Signal` with given opts."
  ^Signal
  ;; Note all dynamic vals passed as explicit args for better control
  [inst uid,
   location ns line column file #?(:clj thread :cljs _thread),
   sample-rate, kind id level, ctx parent,
   kvs data msg_,
   run-form run-result error]

  (let [signal
        (if-let [^RunResult run-result run-result]
          (let  [run-nsecs (.-run-nsecs run-result)
                 end-inst
                 #?(:clj  (.plusNanos ^java.time.Instant inst run-nsecs)
                    :cljs (js/Date. (+ (.getTime inst) (/ run-nsecs 1e6))))

                 run-err (.-error run-result)
                 run-val (.-value run-result)
                 msg_
                 (if (fn?  msg_) ; Undocumented, handy for `trace!`/`spy!`, etc.
                   (delay (msg_ run-form run-val run-err run-nsecs))
                   msg_)]

            (Signal. 1 inst uid,
              location ns line column file #?(:clj thread),
              sample-rate, kind id level, ctx parent,
              data msg_,
              run-err run-form run-val,
              end-inst run-nsecs kvs))

          (Signal. 1 inst uid,
            location ns line column file #?(:clj thread),
            sample-rate, kind id level, ctx parent,
            data msg_, error nil nil nil nil kvs))]

    (if kvs
      (reduce-kv assoc signal kvs)
      (do              signal))))

(comment
  (enc/qb 1e6 ; 66.8
    (new-signal
      nil nil nil nil nil nil nil nil nil nil
      nil nil nil nil nil nil nil nil nil nil)))

;;;; Signal API helpers

#?(:clj (defmacro signal-docstring [    rname] (enc/slurp-resource (str "signal-docstrings/" (name rname) ".txt"))))
#?(:clj (defmacro defhelp          [sym rname] `(enc/def* ~sym {:doc ~(eval `(signal-docstring ~rname))} "See docstring")))

#?(:clj
   (defn signal-arglists [macro-id]
     (case macro-id

       :signal! ; [opts] => allowed? / run result (value or throw)
       '([{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error run & kvs]}])

       :event! ; [id] [id level-or-opts] => allowed?
       '([id      ]
         [id level]
         [id
          {:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error #_run & kvs]}])

       :log! ; [msg] [level-or-opts msg] => allowed?
       '([      msg]
         [level msg]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error #_run & kvs]}
          msg])

       :error! ; [error] [id-or-opts error] => given error
       '([   error]
         [id error]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error #_run & kvs]}
          error])

       (:trace! :spy!) ; [form] [id-or-opts form] => run result (value or throw)
       '([   form]
         [id form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error run & kvs]}
          form])

       :catch->error! ; [form] [id-or-opts form] => run result (value or throw)
       '([   form]
         [id form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id, rethrow? catch-val,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error #_run & kvs]}
          form])

       :uncaught->error! ; [] [id-or-opts] => nil
       '([  ]
         [id]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location inst uid middleware,
            sample-rate kind ns id level when rate-limit,
            ctx parent trace?, do let data msg error #_run & kvs]}])

       (enc/unexpected-arg! macro-id))))

#?(:clj
   (defn signal-opts
     "Util to help write common signal wrapper macros."
     [context defaults main-key extra-key arg-order args]

     (enc/cond
       :let [context-name (str "`" (name context) "`")
             num-args (count args)
             bad-args!
             (fn [msg data]
               (throw
                 (ex-info (str "Invalid " context-name " args: " msg)
                   (conj
                     {:context context
                      :args    args}
                     data))))]

       (not (#{1 2} num-args))
       (bad-args! (str "wrong number of args (" num-args ")")
         {:actual num-args, :expected #{1 2}})

       :let [[main-arg extra-arg]
             (case arg-order
               :dsc          args  ; [main ...]
               :asc (reverse args) ; [... main]
               (enc/unexpected-arg!
                 arg-order))

             extra-arg?  (= num-args 2)
             extra-opts? (and extra-arg? (map? extra-arg))]

       :do
       (cond
         (map? main-arg)
         (bad-args! "single map arg is USUALLY a mistake, so isn't allowed. Please use 2 arg call if this is intentional." {})

         (and extra-opts? (contains? extra-arg main-key))
         (bad-args! (str "given opts should not contain `" main-key "`.") {}))

       extra-opts? (merge defaults {main-key main-arg}           extra-arg)
       extra-arg?  (merge defaults {main-key main-arg, extra-key extra-arg})
       :else       (merge defaults {main-key main-arg}))))

(comment (signal-opts `foo! {:level :info} :id :level :dsc [::my-id {:level :warn}]))

#?(:clj
   (defn signal-catch-opts
     "For use within `trace!` and `spy!`, etc."
     [main-opts]
     (let [catch-id-or-opts (get    main-opts :catch->error)
           main-opts        (dissoc main-opts :catch->error)
           catch-opts
           (when catch-id-or-opts
             (let [base ; Inherit some opts from main
                   (enc/assoc-some {}
                     :location (get main-opts :location)
                     :id       (get main-opts :id))]
               (cond
                 (true? catch-id-or-opts) (do   base)
                 (map?  catch-id-or-opts) (conj base catch-id-or-opts)
                 :else                    (conj base {:id catch-id-or-opts}))))]

       [main-opts catch-opts])))

(comment
  (signal-catch-opts {:id :main-id, :catch->error           true})
  (signal-catch-opts {:id :main-id, :catch->error      :error-id})
  (signal-catch-opts {:id :main-id, :catch->error {:id :error-id}}))

;;;; Signal macro

#?(:clj
   (defn thread-info
     "Returns {:keys [group name id]} for current thread."
     []
     (when-let [t (Thread/currentThread)]
       {:group (when-let [g (.getThreadGroup t)] (.getName g))
        :name  (.getName t)
        :id    (.getId   t)})))

(comment (enc/qb 1e6 (thread-info))) ; 44.49

#?(:clj
   (defmacro ^:public signal!
     "Generic low-level signal call, also aliased in Encore."
     {:doc      (signal-docstring :signal!)
      :arglists (signal-arglists  :signal!)}
     [opts]
     (have? map? opts) ; We require const map keys, but vals may require eval
     (let [defaults             (get    opts :defaults)
           opts (merge defaults (dissoc opts :defaults))
           clj? (not (:ns &env))
           {run-form :run} opts

           {:keys [#_expansion-id location elide? allow?]}
           (sigs/filterable-expansion
             {:macro-form &form
              :macro-env  &env
              :sf-arity   4
              :ct-sig-filter     ct-sig-filter
              :*rt-sig-filter* `*rt-sig-filter*}
             opts)]

       (if elide?
         run-form
         (let [{ns-form     :ns
                line-form   :line
                column-form :column
                file-form   :file} location

               {inst-form  :inst
                level-form :level
                kind-form  :kind
                id-form    :id} opts

               trace? (get opts :trace? (boolean run-form))
               _
               (when-not (contains? #{true false nil} trace?)
                 ;; Not much motivation to support runtime `trace?` form, but easy
                 ;; to add support later if desired
                 (enc/unexpected-arg! trace?
                   {:msg     "Expected constant (compile-time) `:trace?` boolean"
                    :context `with-tracing}))

               inst-form   (get opts :inst  :auto)
               inst-form   (if (= inst-form :auto) `(enc/now-inst*) inst-form)

               uid-form    (get opts :uid (when trace? :auto/uuid))
               uid-form    (parse-uid-form uid-form)

               thread-form (if clj? `(thread-info) nil)

               signal-delay-form
               (let [{do-form          :do
                      let-form         :let
                      msg-form         :msg
                      data-form        :data
                      error-form       :error
                      sample-rate-form :sample-rate} opts

                     let-form (or let-form '[])
                     msg-form (parse-msg-form msg-form)

                     ctx-form        (get opts :ctx                 `taoensso.telemere/*ctx*)
                     parent-form     (get opts :parent (when trace? `taoensso.telemere.impl/*trace-parent*))
                     middleware-form (get opts :middleware          `taoensso.telemere/*middleware*)

                     kvs-form
                     (not-empty
                       (dissoc opts
                         :elidable? :location :inst :uid :middleware,
                         :sample-rate :ns :kind :id :level :filter :when #_:rate-limit,
                         :ctx :parent #_:trace?, :do :let :data :msg :error :run
                         :elide? :allow? #_:expansion-id))]

                 ;; Compile-time validation
                 (when (and run-form error-form)
                   (throw ; Prevent ambiguity re: source of error
                     (ex-info "Signals cannot have both `:run` and `:error` opts at the same time"
                       {:run-form   run-form
                        :error-form error-form
                        :location   location
                        :other-opts (dissoc opts :run :error)})))

                 `(delay
                    ;; Delay (cache) shared by all handlers. Covers signal `:let` eval, signal construction,
                    ;; middleware (possibly expensive), etc. Throws here will be caught by handler.
                    ~do-form
                    (let [~@let-form ; Allow to throw, eval BEFORE data, msg, etc.
                          ~'__signal
                          (new-signal ~'__inst ~'__uid
                            ~location ~'__ns ~line-form ~column-form ~file-form ~'__thread,
                            ~sample-rate-form, ~'__kind ~'__id ~'__level, ~ctx-form ~parent-form,
                            ~kvs-form ~data-form ~msg-form,
                            '~run-form ~'__run-result ~error-form)]

                      ;; Final unwrapped signal value visible to users/handler-fns, allow to throw
                      (if-let [sig-middleware# ~middleware-form]
                        (sig-middleware# ~'__signal) ; Apply signal middleware, can throw
                        (do              ~'__signal)))))]

           ;; Could avoid double `run-form` expansion with a fn wrap (>0 cost)
           ;; (let [run-fn-form (when run-form `(fn [] (~run-form)))]
           ;;   `(let [~'run-fn-form ~run-fn-form]
           ;;      (if-not ~allow?
           ;;        (run-fn-form)
           ;;        (let [...]))))

           `(enc/if-not ~allow? ; Allow to throw at call
              ~run-form
              (let [~'__inst   ~inst-form   ; Allow to throw at call
                    ~'__level  ~level-form  ; ''
                    ~'__kind   ~kind-form   ; ''
                    ~'__id     ~id-form     ; ''
                    ~'__uid    ~uid-form    ; ''
                    ~'__ns     ~ns-form     ; ''
                    ~'__thread ~thread-form ; ''

                    ~'__run-result ; Non-throwing (traps)
                    ~(when run-form
                       `(let [t0# (enc/now-nano*)]
                          (with-tracing ~trace? ~'__id ~'__uid
                            (enc/try*
                              (do            (RunResult. ~run-form nil (- (enc/now-nano*) t0#)))
                              (catch :all t# (RunResult. nil       t#  (- (enc/now-nano*) t0#)))))))

                    signal_# ~signal-delay-form]

                (dispatch-signal! ; Runner preserves dynamic bindings when async.
                  ;; Unconditionally send same wrapped signal to all handlers. Each handler will
                  ;; use wrapper for handler filtering, unwrapping (realizing) only allowed signals.
                  (WrappedSignal. ~'__ns ~'__kind ~'__id ~'__level signal_#))

                (if    ~'__run-result
                  (do (~'__run-result signal_#))
                  true))))))))

(comment
  (with-signal  (signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))
  (macroexpand '(signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))

  (do
    (println "---")
    (sigs/with-handler *sig-handlers* "hf1" (fn hf1 [x] (println x)) {}
      (signal! {:level :info, :run "run"}))))

#?(:clj
   (defmacro signal-allowed?
     "Used only for intake (SLF4J, `clojure.tools.logging`, etc.)."
     {:arglists (signal-arglists :signal!)}
     [opts]
     (let [{:keys [#_expansion-id #_location elide? allow?]}
           (sigs/filterable-expansion
             {:macro-form &form
              :macro-env  &env
              :sf-arity   4
              :ct-sig-filter     ct-sig-filter
              :*rt-sig-filter* `*rt-sig-filter*}
             opts)]

       (and (not elide?) allow?))))

;;;; Intakes

#?(:clj
   (do
     (enc/defonce ^:private intake-checks_
       "{<source-id> (fn check [])}"
       (atom
         {:tools-logging (fn [] {:present? (enc/have-resource? "clojure/tools/logging.clj")})
          :slf4j         (fn [] {:present? (enc/compile-when org.slf4j.Logger true false)})}))

     (defn add-intake-check! [source-id check-fn] (swap! intake-checks_ assoc source-id check-fn))

     (defn ^:public check-intakes
       "Experimental, subject to change.
       Runs Telemere's registered intake checks and returns
       {<source-id> {:keys [sending->telemere? telemere-receiving? ...]}}.

       Useful for tests/debugging."
       []
       (enc/map-vals (fn [check-fn] (check-fn))
         @intake-checks_))

     (defn test-intake! [msg test-fn]
       (let [msg (str "Intake test: " msg " (" (enc/uuid-str) ")")
             signal
             (binding [*rt-sig-filter* nil] ; Without runtime filters
               (with-signal :raw :trap (test-fn msg)))]

         (= (force (get signal :msg_)) msg)))))
