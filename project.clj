(defproject org.clojars.leanpixel/om-fields "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/clojurescript "0.0-2280"]

                 [com.andrewmcveigh/cljs-time "0.2.4"]
                 [om "0.7.1"]])

  :plugins [[jamesnvc/lein-lesscss "1.3.4"]]

  :lesscss-paths ["resources/less"]
  :lesscss-output-path "resources/public/css/om-fields"

  :cljsbuild {:foreign-libs [{:file "js/sugar-1.4.1-date.dev.js"
                              :provides ["sugar.date"]}]})
