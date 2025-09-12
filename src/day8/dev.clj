(ns day8.dev
  (:require
   [clojure.string :as str]
   [babashka.process :refer [shell]]
   [babashka.fs :as fs]))

(defn trim-v [s] (apply str (drop-while #{\v} s)))

(defn release-tag? [s] (re-find (re-pattern "^v\\d+\\.\\d+\\.\\d+$") s))

(defn client-dir []
  (let [wd                 (first (take-while (fn [s] (not (str/starts-with? s "-")))
                                              *command-line-args*))
        opts               (cond-> {:out :string} wd (assoc :dir wd))
        dir                (-> opts
                               (shell "git rev-parse --show-toplevel")
                               :out
                               str/trim)
        assume-client-dir? (fs/exists? (fs/file dir "client" "deps.edn"))
        dir                (cond-> dir
                             assume-client-dir? (str "/client"))]
       dir))

  (defn git-latest-hash! [& {:keys [dir]}]
     (->> "git log -1 --format=%h"
          (shell {:out :string :dir dir})
          :out
          str/trim))

(defn git-release-tag! [& {:keys [dir]}]
     (->> (shell {:out :string
                  :dir dir}
                 "git tag -l --sort=v:refname")
          :out
          str/split-lines
          (filter release-tag?)
          last
          str/trim))

(defn git-release-hash! [& {:keys [dir] :as opts}]
     (->> (git-release-tag! opts)
          (str "git rev-parse --short ")
          (shell {:out :string :dir dir})
          :out
          str/trim))

(defn git-app-version! [& {:as opts}]
  (let [r-hash (git-release-hash! opts)
        l-hash (git-latest-hash! opts)]
       (-> (trim-v (git-release-tag! opts))
           (cond-> (not= r-hash l-hash) (str "--" l-hash))
           (str/replace "\n" "")
           str/trim)))

(defmacro app-version [] (git-app-version!))
