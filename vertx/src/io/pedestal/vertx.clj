(ns io.pedestal.vertx
  (:require [clojure.stacktrace :as stacktrace]
            [io.pedestal.http :as http]
            [io.pedestal.http.request :as request]
            [io.pedestal.http.request.map :as request.map]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.log :as log]
            [io.pedestal.vertx.interceptor :as vertx.interceptor]
            [io.pedestal.vertx.request :as vertx.request])
  (:import (io.vertx.core Vertx Handler)
           (io.vertx.core.http HttpServer HttpServerRequest HttpServerResponse)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (io.vertx.core.buffer Buffer)))

(defprotocol IntoHandler
  (-handler [t] "Given a value, produce a Handler."))

(extend-protocol IntoHandler
  clojure.lang.Fn
  (-handler [f]
    (reify Handler
      (handle [_ event] (f event)))))

;; Server

(defn- listen
  "Invoked when the server is listening.
  Throws if the server fails to start listening."
  [result]
  (if (.succeeded result)
    (log/info :msg "Vert.x HTTP Server now listening.")
    (throw (ex-info "Vert.x HTTP Server failed to start listening." {}))))

(defn server
  "Provisions a new Vert.x HttpServer.
  Returns a map with the following keys: :server, :start-fn, :stop-fn.

  The service-map io.pedestal.vertx/handler value is used as the
  HttpServer's request handler."
  [service-map server-opts]
  (let [request-handler     (::request-handler service-map)
        listen-handler      (or (::listen-handler service-map)
                                listen)
        vertx               (::vertx service-map)
        {:keys [port]
         :or   {port 8080}} server-opts
        server              (.createHttpServer ^Vertx vertx)]
    {:server   server
     :start-fn (fn []
                 (.. ^HttpServer server
                     (requestHandler (-handler request-handler))
                     (listen port (-handler listen-handler)))

                 server)
     :stop-fn (fn []
                (log/info :msg "Stopping Vert.x HTTP Server.")
                (.close ^HttpServer server)
                server)}))

;; Provider

(defn- body-handler
  "Returns a request body handler fn.
   Responsible for:
   * building the context
   * executing interceptors."
  [interceptors request default-context]
  (fn [^Buffer buffer]
    (let [vertx   (::vertx default-context)
          body    (ByteArrayInputStream. (.getBytes buffer))
          context (merge default-context
                         {::vertx.interceptor/request (vertx.request/make-request-wrapper request body)})]
      (log/debug :in :request-handler-fn
                 :context context)
      (log/counter ::active-request-handler-calls 1)
      (try
        (let [final-context (interceptor.chain/execute context interceptors)]
          (log/debug :msg "Leaving request handler."
                     :final-context final-context))
        (catch Throwable t
          (log/meter ::base-request-handler-error)
          (log/error :msg "Request handler threw an exception"
                     :throwable t
                     :cause-trace (with-out-str (stacktrace/print-cause-trace t))))))))

(defn- request-handler
  "Returns a request processing handler fn."
  [interceptors default-context]
  (fn [^HttpServerRequest request]
    (.bodyHandler request (-handler (body-handler interceptors request default-context)))))

(defn provider
  "Initializes the service map with a Vert.x-compatible request handler (an
  instance of io.vertx.core.Handler) which processes requests in the context of
  an interceptor chain."
  [service-map]
  (let [vertx (Vertx/vertx)
        rh    (request-handler
               (into [vertx.interceptor/terminator-injector
                      vertx.interceptor/stylobate
                      vertx.interceptor/ring-response]
                     (::http/interceptors service-map))
               {::vertx vertx})]
    (assoc service-map
           ::vertx vertx
           ::request-handler rh)))

(comment
 ;; one approach for getting the body into an input stream.
 (import '(java.io ByteArrayInputStream))
 (import '(io.vertx.core.buffer Buffer))

 (def buf (Buffer/buffer "foo bar"))

 (def is (ByteArrayInputStream. (.getBytes buf)))

 (import '(io.vertx.core MultiMap))

 (def mm (MultiMap/caseInsensitiveMultiMap))

 (.add mm "Foo" "bar")
 (.add mm "Baz" "boo")

 (->> mm
      (map (fn [[k v]] (hash-map (.toLowerCase k) v)))
      (reduce merge {}))
 )
