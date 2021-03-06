(ns chess3man-web.app
  (:require [reagent.core :as r]
            [reagent.ratom :as ra]
            [clj3manchess.engine.pos :as p]
            [clj3manchess.engine.color :as c]
            [clj3manchess.engine.vectors :as vec]
            [clj3manchess.engine.board :as b]
            [clj3manchess.engine.fig :as f]
            [clj3manchess.engine.state :as st]
            [clj3manchess.engine.move :as m]
            [schema.core :as s]
            [cljs.reader :refer [read-string]]
            [chess3man-web.play.game.board.squares :as sq :refer [paths]]
            [promesa.core :as pr]
            [clj3manchess.online.client :as a]))
            ;;[clj3manchess.engine.pos :refer [rank]]))

(enable-console-print!)

(print sq/all-ranks-and-files)

(def dark-color "#a65c13")
(def light-color "#f4d80b")

(defn give-normal-colors [even-dark] (zipmap sq/all-ranks-and-files
                                             (map #(if (= even-dark
                                                          (= (even? (first %))
                                                             (even? (second %))))
                                                     dark-color light-color)
                                                  sq/all-ranks-and-files)))

(defonce board-rtl true)
(def rot-for-color {:white 3 :gray (+ 3 8) :black (- 3 8)})

(def main-board-rot (r/atom 3))
(defn rot-board [col] (reset! main-board-rot (get rot-for-color col)))

(defn to-game-file [board-file] (if board-rtl (mod (- 24 (mod (- board-file @main-board-rot) 24)) 24)
                                    (mod (- board-file @main-board-rot) 24)))
(defn to-game-pos [pos] (when pos [(first pos) (to-game-file (second pos))]))

(defn to-board-file [game-file] (if board-rtl (to-game-file game-file)
                                    (mod (+ to-board-file @main-board-rot) 24)))
(defn to-board-pos [pos] (when pos [(first pos) (to-board-file (second pos))]))

(def main-board-colors (r/atom (give-normal-colors true)))

(defn set-normal-colors [even-dark] (reset! main-board-colors (give-normal-colors even-dark)))

(defn set-color [pos color] (swap! main-board-colors assoc pos color))

(defn main-board-color [pos] (r/cursor main-board-colors [pos]))

(def main-moats (r/atom [true true true]))

(def pionek-prefix "http://archiet.platinum.edu.pl/3manchess/res/pionki/Chess_")
(def pionek-typ {:bishop "b" :king "k" :knight "n" :pawn "p" :queen "q" :rook "r"})
(def pionek-kolor-list ["l" "g" "d"])
(def pionek-kolor-map {:white "l" :gray "g" :black "d"})
(def pionek-suffix "t60.png")
(s/defn pionek-url :- s/Str ([typ :- f/FigType kolor :- c/Color]
                             (str pionek-prefix (pionek-typ typ) (pionek-kolor-map kolor) pionek-suffix))
  ([fig :- (s/either f/Piece f/Pawn)] (pionek-url (:type fig) (:color fig))))

(def main-board-figs (r/atom (into {} (map (fn [x] {x {:type :queen
                                                       :color (nth c/colors (quot (p/file x) 8))}})
                                           p/all-pos))))

(def centerradius (r/atom 100))

(def size (r/atom 850))
(def half-size (/ @size 2))
(def half-size-10 (- half-size 10))
(def translate-string (str "translate(" half-size "," half-size ")"))
(def viewBox (str 0 " " 0 " " @size " " @size))

(def ranks-radiuses (sq/ranks-radiuses @centerradius half-size-10))

(def pionek-size (r/atom 60))

(def main-fig-images (r/atom []))

(defn main-fig-images-fn [] (reset! main-fig-images
                                    (map (fn [arr] [:image
                                                    (let [_ (println arr)
                                                          _ (println (to-board-pos (first arr)))
                                                          {:keys [x y]}
                                                          (->> arr
                                                               first
                                                               to-board-pos
                                                               (sq/whatxy ranks-radiuses))
                                                          xlinkhref (pionek-url (second arr))
                                                          half-pionek-size (/ @pionek-size 2)]
                                                      {:x (- x half-pionek-size)
                                                       :y (- y half-pionek-size)
                                                       :xlinkHref xlinkhref
                                                       :on-click #(sq/click (to-board-pos (first arr)))})])
                                         (into [] @main-board-figs))))
(def main-fig-images-fn-react (ra/run! (main-fig-images-fn) @main-board-figs))

(defn main-board-pos [pos] (r/cursor main-board-figs [pos]))
(defn main-get [pos] @(main-board-pos pos))
(defn main-put [pos what] (swap! main-board-figs b/put-onto-map-board pos what))
(s/defn main-set [b :- b/Board] (reset! main-board-figs (b/fill-map-board b)))
(def state (r/atom {}))
(def moats (r/cursor state [:moats]))
(defn set-state [s] (do (when (contains? s :board) (main-set (:board s)))
                        (reset! state s)))
(def state-id (r/atom nil))

(defn afters-exist? ([from to] (if (contains? @state :board)
                                 (let [afters (m/generate-afters-set {:from from :to to :before @state})
                                       afters (filter #(or (= % :no-promotion)
                                                           (map? %)) afters)] (not (empty? afters)))))
  ([from] #(afters-exist? from %)))
(defn ourvftpgen [from] (filter (afters-exist? from) (get m/AMFT from)))
(def prev-click (r/atom nil))
(def lighted (atom false))
(defn lightup [from] (when-not @lighted (do (reset! lighted true) (println "lightup" from)
                          (set-color (to-board-pos from) "#55f")
                          (let [lis (ourvftpgen from)
                                _ (when lis (reset! lighted lis))
                                lis (map to-board-pos lis)
                                _ (println lis)] (vec (for [x lis] (set-color x "#0b0")))))))
(defn dimdown [] (do (reset! prev-click nil)
                   (when @lighted (do (println "dimmed")
                                     (set-normal-colors true) (reset! lighted false)))))
(def oper (atom false))
(defn clear-clicks-on-state-change [] (do (reset! oper true)
                                        (reset! prev-click nil)
                                        (reset! sq/clicked nil)
                                        (reset! oper false)))
(def state-id-entry (r/atom nil))
(defn action [from to] (when @lighted (if (not= from to) (do (println "action" from to @lighted)
                                                             (let [lis @lighted]
                                                               (if (or (not (coll? lis))
                                                                       (some #(= % to) lis))
                                                                 (pr/then (a/post-move @state-id {:from from :to to})
                                                                          (fn [{x :id :as xx}]
                                                                            (do
                                                                              (println xx)
                                                                              (pr/then (a/get-just-move-by-id x)
                                                                                      (fn [{x :aftergame :as xx}]
                                                                                        (do
                                                                                          (println "tojestafter" xx)
                                                                                          (when x
                                                                                            (do (reset! state-id-entry x)
                                                                                                (reset! state-id x)
                                                                                                (pr/then
                                                                                                 (a/get-gameplay-by-id x)
                                                                                                 set-state)))))))))))
                                           (clear-clicks-on-state-change) (dimdown)) (dimdown))))
(def doubleclick (ra/run! (when-not @oper (if-not @prev-click (when @sq/clicked (do (reset! prev-click @sq/clicked)
                                                                     (lightup (to-game-pos @prev-click))))
                                   (if @sq/clicked (action (to-game-pos @prev-click) (to-game-pos @sq/clicked))
                                           (dimdown)))) @sq/clicked))
(defn the-paths [] (vec (into [:g {:transform translate-string}] (paths ranks-radiuses main-board-color))))
(defn some-component []
  [:div
   [:div
    [:input {:id "stateentry"
             :name "stateentry"
             :type "number"
             :required "true"
             :value @state-id-entry
             :on-change #(reset! state-id-entry (-> % .-target .-value))}]
    [:button {:id "stateload"
              :name "stateload"
              :type "submit"
              :on-click #(pr/then (a/get-gameplay-by-id @state-id-entry) set-state)} "Załaduj"]
    [:br]
    [:button {:id "newgame"
              :name "newgame"
              :type "submit"
              :on-click #(do (println "newgameclick")
                           (pr/then (a/newgame) (fn [{x :id :as xx}]
                                                 (do
                                                   (println xx)
                                                   (when x
                                                     (do (reset! state-id-entry x)
                                                         (reset! state-id x)
                                                         (pr/then (a/get-gameplay-by-id x) set-state)))))))} "Nowa gra"]
    (str (when (contains? @state :board) (ourvftpgen [1 0])))]
   [:svg {:width @size :height @size :viewBox viewBox}
    (-> (the-paths)
        (into [[:text {:x 0 :y 0} "A kliknięte" (str (to-game-pos @sq/clicked))]])
        (into @main-fig-images)
        (into (into [:g {:transform translate-string}] (sq/creeks ranks-radiuses)))
        (into (into [:g {:transform translate-string}] (vec (let [mts (sq/moats ranks-radiuses)]
                                                              (map mts @moats))))))]
   [:div {:style {:float :right}} "Kliknięte " (str @sq/clicked) [:br]
    "A wcześniej" (str @prev-click) "A reszta stanu" (str (dissoc @state :board))
    ]])

(defn init []
  (r/render-component [some-component]
                      (.getElementById js/document "container"))
  ;;(set-state (assoc st/newgame :moats #{:white}))
  ;;(set-color [4 5] "#020")
  (rot-board :white))
