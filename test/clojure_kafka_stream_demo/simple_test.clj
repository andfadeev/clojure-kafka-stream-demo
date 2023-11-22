(ns clojure-kafka-stream-demo.simple-test
  (:require [jackdaw.test :as jdt]
            [clojure.test :refer :all]
            [clojure-kafka-stream-demo.core :as core]
            [jackdaw.test.commands :as cmd]
            [jackdaw.serdes :as js]
            [jackdaw.test :refer [test-machine]]
            [jackdaw.test.fixtures :refer [topic-fixture]]))

(defn topic-config
  [topic-name]
  {:topic-name topic-name
   :key-serde (js/edn-serde)
   :value-serde (js/edn-serde)
   :partition-count 1
   :replication-factor 1})

(def topics
  {:input-topic (topic-config (str "input-topic-" (random-uuid)))
   :output-topic
   (topic-config (str "output-topic-" (random-uuid)))})

(use-fixtures
  :once
  (topic-fixture
    {"bootstrap.servers" (core/kafka-bootstrap-servers)}
    topics))

(deftest simple-kafka-stream-test
  (with-open [machine (test-machine
                        (jdt/kafka-transport
                          {"bootstrap.servers" (core/kafka-bootstrap-servers)
                           "group.id" "simple-kafka-stream-test-machine"}
                          topics))]

    ;; just a simplification for the demo, usually it will be started as part of the test system, via component or integrant
    (with-open [kafka-stream (core/start-kafka-stream!
                               (partial core/build-simple-kafka-stream-topology topics))]
      (let [write (cmd/write! :input-topic {:name "world"}
                              {:partition 0})
            watch (cmd/watch
                    (fn [journal]
                      (->> (get-in journal [:topics :output-topic])
                           (first)))
                    {:timeout 5000})
            {:keys [results]} (jdt/run-test machine [write watch])
            [_ watch-result] results]

        (is (= {:headers {}
                :key nil
                :offset 0
                :partition 0
                :topic :output-topic
                :value "Hello world"}

               (get-in watch-result [:result :info])))))))