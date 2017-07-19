(ns io.pedestal.vertx.request
  (:require [io.pedestal.http.request :as request])
  (import (io.vertx.core.http HttpServerRequest)))

(defrecord RequestWrapper [request body]
  request/ContainerRequest
  (server-port [_] (.. ^HttpServerRequest request (localAddress) (port)))
  (server-name [_] (.. ^HttpServerRequest request (localAddress) (host)))
  (remote-addr [_] (.. ^HttpServerRequest request (remoteAddress) (host)))
  (uri [_] (.uri ^HttpServerRequest request))
  (query-string [_] (.query ^HttpServerRequest request))
  (scheme [_] (.scheme ^HttpServerRequest request))
  (request-method [_] (keyword (.. ^HttpServerRequest request (method) (name) (toLowerCase))))
  (protocol [_] (.. ^HttpServerRequest request (version) (name)))
  (headers [_]
    (->> (.headers ^HttpServerRequest request)
         (map (fn [[k v]] (hash-map (.toLowerCase k) v)))
         (reduce merge {})))
  (header [_ header-string] (.getHeader ^HttpServerRequest request header-string))
  (ssl-client-cert [_]
    ;;TODO!!
    )
  (body [_] body)
  (path-info [_] (.path ^HttpServerRequest request))
  (async-supported? [_]
    ;;TODO!!
    false)
  (async-started? [_]
    ;;TODO!!
    false))

(defn make-request-wrapper
  "Returns an instance of RequestWrapper."
  [request body]
  (RequestWrapper. request body))
