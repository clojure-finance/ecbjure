(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.github.clojure-finance/ecbjure)
(def version "0.1.4")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :pom-data [[:description "Pure Clojure client for ECB historical FX data"]
                           [:url "https://github.com/clojure-finance/ecbjure"]
                           [:licenses
                            [:license
                             ["name" "Eclipse Public License 2.0"]
                             [:url "https://www.eclipse.org/legal/epl-2.0/"]]]

                           [:scm
                            [:url "https://github.com/clojure-finance/ecbjure"]
                            [:connection "scm:git:git://github.com/clojure-finance/ecbjure.git"]
                            [:developerConnection "scm:git:ssh://git@github.com/clojure-finance/ecbjure.git"]
                            [:tag (str "v" version)]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))
