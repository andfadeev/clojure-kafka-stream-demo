(ns clojure-kafka-stream-demo.word-count-test
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

(let [topics {:input-topic (topic-config (str "input-topic-" (random-uuid)))
              :output-topic (topic-config (str "output-topic-" (random-uuid)))}]

  (use-fixtures
    :once
    (topic-fixture
      {"bootstrap.servers" (core/kafka-bootstrap-servers)}
      topics))


  (deftest word-count-kafka-stream-test
    (with-open [machine (test-machine
                          (jdt/kafka-transport
                            {"bootstrap.servers" (core/kafka-bootstrap-servers)
                             "group.id" (str "test-machine-" (random-uuid))}
                            topics))]

      ;; just a simplification for the demo, usually it will be started as part of the test system, via component or integrant
      (with-open [kafka-stream (core/start-kafka-stream!
                                 (partial core/build-word-count-kafka-stream-topology topics))]
        (let [write-1 (cmd/write! :input-topic {:line "a b c"}
                                  {:key (random-uuid)
                                   :partition 0})
              write-2 (cmd/write! :input-topic {:line "x y z a b"}
                                  {:key (random-uuid)
                                   :partition 0})
              watch (cmd/watch (fn [journal]
                                 (let [output (->> (get-in journal [:topics :output-topic])
                                                   (remove nil?))]
                                   (when (= 8 (count output))
                                     output))

                                 )
                      {:timeout 10000})
              {:keys [results journal]} (jdt/run-test machine [write-1 write-2 watch])
              [_ _ watch-result] results]

          (is (= [{:key "a"
                   :value 1}
                  {:key "b"
                   :value 1}
                  {:key "c"
                   :value 1}
                  {:key "x"
                   :value 1}
                  {:key "y"
                   :value 1}
                  {:key "z"
                   :value 1}
                  {:key "a"
                   :value 2}
                  {:key "b"
                   :value 2}]
                 (vec (map (fn [m]
                             (select-keys m [:key :value])) (get-in watch-result [:result :info]))))))))))