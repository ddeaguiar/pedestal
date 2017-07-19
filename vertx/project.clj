(defproject io.pedestal/pedestal.vertx "0.5.3-SNAPSHOT"
  :description "Vertx adapter for Pedestal HTTP Service."
  :url "https://github.com/pedestal/pedestal"
  :scm "https://github.com/pedestal/pedestal"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; because we have to for now...
                 [io.pedestal/pedestal.jetty "0.5.2"]

                 [io.pedestal/pedestal.service "0.5.2"]
                 [io.vertx/vertx-core "3.4.1"]])
