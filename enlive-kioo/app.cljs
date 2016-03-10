(ns todomvc.app
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [secretary.macros :refer [defroute]]
                   [kioo.core :as kioo])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! chan]]
            [kioo.core :refer [content substitute do-> set-attr
                               add-class remove-class set-style
                               remove-style append set-class]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary]
            [todomvc.utils :refer [pluralize now guid store hidden]]
            [clojure.string :as string]
            [todomvc.item :as item])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

(def ENTER_KEY 13)

(def app-state (atom {:showing :all :todos []}))

;; =============================================================================
;; Routing

(defroute "/" [] (swap! app-state assoc :showing :all))

(defroute "/:filter" [filter] (swap! app-state assoc :showing (keyword filter)))

(def history (History.))

(events/listen history EventType/NAVIGATE
  (fn [e] (secretary/dispatch! (.-token e))))

(.setEnabled history true)

;; =============================================================================
;; Main and Footer Components

(declare toggle-all)

(defn visible? [todo filter]
  (case filter
    :all true
    :active (not (:completed todo))
    :completed (:completed todo)))

(defn main [{:keys [showing todos] :as app} owner opts]
  (om/component
   (kioo/component "todo-app.html" [:#main]
    {[:#main] (set-style :display (if (empty? todos) "none" ""))
     [:#toggle-all] (set-attr :onChange #(toggle-all % app)
                              :checked (every? :completed todos))
     [:#todo-list] (content
                    (om/build-all item/todo-item todos
                                  {:opts opts :key :id
                                   :fn (fn [todo]
                                         (cond-> todo
                                                 (= (:id todo) (:editing opts)) (assoc :editing true)
                                                 (not (visible? todo showing)) (assoc :hidden true)))}))})))


(defn make-clear-button [completed comm]
  (when (pos? completed)
    (kioo/component "todo-app.html" [:#clear-completed]
     {[:#clear-completed] (set-attr :onClick #(put! comm [:clear (now)]))
      [:.count] (content completed)})))

(defn footer [app owner opts]
  (let [{:keys [count completed comm]} opts
        clear-button (make-clear-button completed comm)
        sel (-> (zipmap [:all :active :completed] (repeat ""))
                (assoc (:showing app) "selected"))]
    (om/component
     (kioo/component "todo-app.html" [:#footer]
       {[:#footer] (set-style :display (if (empty? (:todos app)) "none" ""))
       [:strong]  (content count)
       [:#count-text] (content (str (pluralize count "item") " left"))
       [:#filt-all] (set-class (sel :all))
       [:#filt-act] (set-class (sel :active))
       [:#filt-comp] (set-class (sel :completed))
       [:#clear-completed] (substitute clear-button)}))))

;; =============================================================================
;; Todos

(defn toggle-all [e app]
  (let [checked (.. e -target -checked)]
    (om/transact! app :todos
      (fn [todos] (into [] (map #(assoc % :completed checked) todos))))))

(defn handle-new-todo-keydown [e app owner]
  (when (== (.-which e) ENTER_KEY)
    (let [new-field (om/get-node owner "newField")]
      (when-not (string/blank? (.. new-field -value trim))
        (om/transact! app :todos conj
          {:id (guid)
           :title (.-value new-field)
           :completed false})
        (set! (.-value new-field) "")))
    false))

(defn destroy-todo [app {:keys [id]}]
  (om/transact! app :todos
    (fn [todos] (into [] (remove #(= (:id %) id) todos)))))

(defn edit-todo [app {:keys [id]}] (om/update! app assoc :editing id))

(defn save-todos [app] (om/update! app dissoc :editing))

(defn cancel-action [app] (om/update! app dissoc :editing))

(defn clear-completed [app]
  (om/transact! app :todos
    (fn [todos] (into [] (remove :completed todos)))))

(defn handle-event [type app val]
  (case type
    :destroy (destroy-todo app val)
    :edit    (edit-todo app val)
    :save    (save-todos app)
    :clear   (clear-completed app)
    :cancel  (cancel-action app)
    nil))

(def render-start nil)

(defn todo-app [{:keys [todos] :as app} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[type value] (<! comm)]
                (if (#{:destroy :edit} type) 
                  (om/read value
                    (fn [todo]
                      (handle-event type app todo)))
                  (handle-event type app value)))))))
    om/IWillUpdate
    (will-update [_ _ _] (set! render-start (now)))
    om/IDidUpdate
    (did-update [_ _ _ _]
      (store "todos" todos)
      (let [ms (- (.valueOf (now)) (.valueOf render-start))]
        (set! (.-innerHTML (js/document.getElementById "message")) (str ms "ms"))))
    om/IRender
    (render [_]
      (let [active    (count (remove :completed todos))
            completed (- (count todos) active)
            comm      (om/get-state owner :comm)]
        (kioo/component "todo-app.html" [:#header]
          {[:#new-todo] (set-attr
                         :ref "newField"
                         :onKeyDown #(handle-new-todo-keydown % app owner))
           [:header] (append
                      (om/build main app {:opts {:comm comm :editing (:editing app)}})
                      (om/build footer app {:opts {:count active :completed completed :comm comm}}))})))))

(om/root app-state todo-app (.getElementById js/document "todoapp"))

(dom/render
  (kioo/component "info.html" {}) 
  (.getElementById js/document "info"))

;; =============================================================================
;; Benchmark Stuff

(aset js/window "benchmark1"
  (fn [e]
    (dotimes [_ 200]
      (swap! app-state update-in [:todos] conj
        {:id (guid) :title "foo" :completed false}))))

(aset js/window "benchmark2"
  (fn [e]
    (dotimes [_ 200]
      (swap! app-state update-in [:todos] conj
        {:id (guid) :title "foo" :completed false}))
    (dotimes [_ 5]
      (swap! app-state update-in [:todos]
        (fn [todos]
          (map #(assoc-in % [:completed] not) todos))))
    (swap! app-state update-in [:todos]
      (fn [todos]
        (into [] (remove :completed todos))))))
