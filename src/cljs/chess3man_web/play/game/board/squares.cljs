(ns chess3man-web.play.game.board.squares
  (:require [cljs.spec :as s]
            [reagent.core :as r]))
;;                                        ;[clj3manchess.engine.pos :refer [rank]]))
(defn rank [pos] (first pos))
(defn file [pos] (last pos))

(def all-ranks-and-files
  (->> (range 6)
       (map (fn [rank] (->> (range 24)
                            (map (fn [file] [rank,file])))))
       (apply concat)
       (into [])))

(defonce pi (aget js/Math "PI"))

(defn sin [x] (.sin js/Math x))

(defn cos [x] (.cos js/Math x))

(defn file-angle [file] (/ (* file pi) 12))

(defn path-id [pos] (str "r" (first pos) "f" (second pos)))

(defn segment-starting-coor [file radius] (map #(* radius %) (let [a (file-angle file)] [(sin a) (cos a)])))

(defn paths-data [start-file radius] (take 24 (iterate (fn [given] (let [now (inc (first given))]
                                                                     [now (last given)
                                                                      (segment-starting-coor (inc now) radius)]))
                                                       [start-file
                                                        (segment-starting-coor start-file radius)
                                                        (segment-starting-coor (inc start-file) radius)])))

(defn paths-data-strings [start-file radius] (map (fn [x] [(first x)
                                                           (str "M" (first (second x)) "," (second (second x))
                                                                " A" radius "," radius " 0 0 0 "
                                                                (first (last x)) "," (second (last x)))])
                                                  (paths-data start-file radius)))

(defn paths-rank [start-file radius rank stroke-width pos-to-color]
  (map (fn [x] [:path {:d (second x) :fill "none"
                       :id (path-id [rank (first x)])
                       :key (path-id [rank (first x)])
                       :stroke-width stroke-width
                       :stroke @(pos-to-color [rank (mod (first x) 24)])}])
       (paths-data-strings start-file radius)))

(defn paths
  [start-file center-radius outer-radius pos-to-color]
  (let [inter-radius (- outer-radius center-radius)
        stroke-width (/ inter-radius 6)
        half-width (/ stroke-width 2)]
    (vec (map #(paths-rank start-file (+ center-radius half-width (* stroke-width %)) (- 5 %)
                           stroke-width pos-to-color) (range 6)))))
