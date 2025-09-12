(ns day8.dev
  (:require
   [clojure.string :as str]
   [babashka.process :refer [shell]]
   [babashka.fs :as fs]
   #?@(:bb []
       :clj [[shadow.cljs.devtools.api :as shadow]
             [shadow.cljs.devtools.server :as server]])))

(defn trim-v [s] (apply str (drop-while #{\v} s)))

(defn release-tag? [s] (re-find (re-pattern "^v\\d+\\.\\d+\\.\\d+$") s))

(defn git-latest-hash! [& {:keys [dir]}]
  (some->> "git log -1 --format=%h"
           (shell {:out :string :dir dir})
           :out
           str/trim))

(defn git-release-tag! [& {:keys [dir]}]
  (some->> "git tag -l --sort=v:refname"
           (shell {:out :string :dir dir})
           :out
           str/split-lines
           (filter release-tag?)
           last
           str/trim))

(defn git-release-hash! [& {:keys [dir] :as opts}]
     (some->> (git-release-tag! opts)
              (str "git rev-parse --short ")
              (shell {:out :string :dir dir})
              :out
              str/trim))

(defn git-app-version! [& {:as opts}]
  (let [r-hash (git-release-hash! opts)
        l-hash (git-latest-hash! opts)]
    (-> (git-release-tag! opts)
        (or "0.0.0")
        trim-v
        (cond-> (not= r-hash l-hash) (str "--" l-hash))
        (str/replace "\n" "")
        str/trim)))

(defmacro app-version [] (git-app-version!))

(defn version-dotfile! []
  (let [f (fs/file ".version")]
    (when-not (fs/exists? f)
      (fs/create-file f)
      (fs/delete-on-exit f))
    (fs/update-file f git-app-version!)))

#?(:bb nil
   :clj (defn cljs-repl
          "Connects to a given build-id. Defaults to `:app`."
          ([]
           (cljs-repl :app))
          ([build-id]
           (server/start!)
           (shadow/watch build-id)
           (shadow/nrepl-select build-id))))
