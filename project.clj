(defproject org.clojars.leanpixel/om-fields "1.8.4"
  :description "Fancy input components for om"
  :url "https://github.com/leanpixel/om-fields"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [org.clojure/clojurescript "0.0-3058"]
                 [com.andrewmcveigh/cljs-time "0.3.2"]
                 [org.omcljs/om "0.8.8"]
                 [org.clojars.leanpixel/sugar "1.4.1-0"]]

  :plugins [[jamesnvc/lein-lesscss "1.3.4"]]

  :lesscss-paths ["resources/less"]
  :lesscss-output-path "resources/public/css/om-fields")
