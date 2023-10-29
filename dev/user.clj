(ns user)

(defonce DEBUG (atom nil))

(add-tap #(reset! DEBUG %))
