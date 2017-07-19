(ns io.pedestal.vertx.interceptor
  (:require [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.http.request.map :as request.map]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-response])
  (:import (io.vertx.core.http HttpServerRequest HttpServerResponse)
           (io.vertx.core.buffer Buffer)
           (java.io ByteArrayInputStream ByteArrayOutputStream)))

(def terminator-injector
  (interceptor/interceptor {:name  ::terminator-injector
                            :enter (fn [ctx]
                                     (interceptor.chain/terminate-when
                                      ctx
                                      #(ring-response/response? (:response %))))}))

(defn- add-content-length
  [req]
  (if-let [content-length (get-in req [:headers "content-length"])]
    (assoc! req :content-length (Integer/parseInt content-length))
    req))

(defn- add-content-type
  [req]
  (assoc! req :content-type (get-in req [:headers "content-type"])))

(defn- add-character-encoding
  [req ^HttpServerRequest vertx-req]
  ;; TODO: implement this
  req)

(defn- add-ssl-client-cert
  [req ^HttpServerRequest vertx-req]
  ;; TODO: implement this
  req)

(defn- request-map
  [^HttpServerRequest vertx-req]
  (let [req (request.map/base-request-map vertx-req)]
    (-> req
        transient
        add-content-length
        add-content-type
        (add-character-encoding vertx-req)
        (add-ssl-client-cert vertx-req)
        (assoc! :vertx-request vertx-req)
        persistent!)))

(defn- enter-stylobate
  [ctx]
  ;; TODO: convert Vert.x HttpRequest to a request map
  (assoc ctx :request (request-map (::request ctx))))

(defn- leave-stylobate
  [ctx]
  ;; TODO: What should be done here?
  ctx)

(defn- error-stylobate
  [ctx ex]
  (log/error :msg "error-stylobate triggered"
             :exception ex
             :context ctx)
  (leave-stylobate ctx))

(def stylobate
  "A Vert.x specific impl of io.pedestal.http.impl.servlet-interceptor/stylobate"
  (interceptor/interceptor {:name  ::stylobate
                            :enter enter-stylobate
                            :leave leave-stylobate
                            :error error-stylobate}))

(defprotocol WriteableBody
  (default-content-type [body])
  (buffer [body]))

(extend-protocol WriteableBody
  (class (byte-array 0))
  (default-content-type [_] "application/octet-stream")
  (buffer [byte-array]
    (Buffer/buffer byte-array))

  String
  (default-content-type [_] "text/plain")
  (buffer [string]
    (Buffer/buffer string))

  clojure.lang.IPersistentCollection
  (default-content-type [_] "application/edn")
  (buffer [c]
    (Buffer/buffer (pr-str c)))

  clojure.lang.Fn
  (default-content-type [_] nil)
  (buffer [f]
    (let [output-stream (ByteArrayOutputStream.)]
      (f output-stream)
      (Buffer/buffer (.toByteArray output-stream))))

  nil
  (default-content-type [_] nil)
  (buffer [_] nil))

(defn- set-response
  [^HttpServerResponse vertx-resp response]
  (when-not (.closed vertx-resp)
    (.setStatusCode vertx-resp (:status response))
    (doseq [[h v] (:headers response)]
      ;; putHeader supports string and iterable values
      (.putHeader vertx-resp h v))
    (when-not (get-in response [:headers "Content-Type"])
      (.putHeader vertx-resp "Content-Type" (-> response :body default-content-type)))))

(defn- send-response
  [ctx]
  (let [vertx-req  (get-in ctx [::request :request])
        vertx-resp (.response ^HttpServerRequest vertx-req)
        resp       (:response ctx)
        body       (:body resp)]
    (set-response vertx-resp resp)
    ;; TODO: check if closed.
    ;; TODO: set an exception handler
    ;; TODO: support async
    (.end vertx-resp (buffer body))))

(defn- send-error
  [ctx message]
  (log/info :msg "sending error" :message message)
  (send-response (assoc ctx :response {:status 500 :body message})))

(defn- leave-ring-response
  [{{body :body :as response} :response :as ctx}]
  (log/debug :in :leave-ring-response :response response)
  (cond
    (nil? response) (do
                      (send-error ctx "Internal server error: no response")
                      ctx)
    ;; TODO: implement async support
    true            (do (send-response ctx)
                        ctx)))

(defn- error-ring-response
  [ctx ex]
  (log/error :msg "error-ring-response triggered"
             :exception ex
             :context ctx)
  (send-error ctx "Internal server error: exception")
  ctx)

(def ring-response
  "A Vert.x specific impl of io.pedestal.http.impl.servlet-interceptor/ring-response"
  (interceptor/interceptor {:name  ::ring-response
                            :leave leave-ring-response
                            :error error-ring-response}))
