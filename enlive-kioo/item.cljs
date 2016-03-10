(ns todomvc.item
  (:require [cljs.core.async :refer [>! put!]]
            [todomvc.utils :refer [now hidden]]
            [kioo.core :refer [content substitute do-> set-attr
                               add-class remove-class set-style
                               remove-style append set-class]]
            [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [kioo.core :as kioo]))

(def ESCAPE_KEY 27)
(def ENTER_KEY 13)

;; =============================================================================
;; Todo Item

;; -----------------------------------------------------------------------------
;; Event Handlers

(defn submit [e todo owner comm]
  (when-let [edit-text (om/get-state owner :edit-text)]
    (if-not (string/blank? (.trim edit-text))
      (do
        (om/update! todo assoc :title edit-text)
        (put! comm [:save todo]))
      (put! comm [:destroy todo])))
  false)

(defn edit [e todo owner comm]
  (let [node (om/get-node owner "editField")]
    (put! comm [:edit todo])
    (doto owner
      (om/set-state! :needs-focus true)
      (om/set-state! :edit-text (:title todo)))))

(defn key-down [e todo owner comm]
  (condp == (.-keyCode e)
    ESCAPE_KEY (do
                 (om/set-state! owner :edit-text (:title todo))
                 (put! comm [:cancel todo]))
    ENTER_KEY  (submit e todo owner comm)
    nil))

(defn change [e todo owner]
  (om/set-state! owner :edit-text (.. e -target -value)))

;; -----------------------------------------------------------------------------
;; Todo Item

(defn todo-item [todo owner {:keys [comm]}]
  (reify
    om/IInitState
    (init-state [_]
      {:edit-text (:title todo)})
    om/IDidUpdate
    (did-update [_ _ _ _]
      (when (and (:editing todo)
                 (om/get-state owner :needs-focus))
        (let [node (om/get-node owner "editField")
              len  (.. node -value -length)]
          (.focus node)
          (.setSelectionRange node len len))
        (om/set-state! owner :needs-focus nil)))
    om/IRender
    (render [_]
      (let [class (cond-> ""
                    (:completed todo) (str "completed")
                    (:editing todo)   (str "editing"))]
        (kioo/component "todo-app.html" [:#todo-list :li]
         {[:li] (do->
                 (set-class class)
                 (set-style :display (if (:hidden todo) "none" "")))
          [:.toggle] (set-attr :checked (and (:completed todo) "checked")
                               :onChange (fn [_] (om/transact! todo :completed #(not %))))
          [:label] (do->
                    (set-attr :onDoubleClick (om/bind edit todo owner comm))
                    (content (:title todo)))
          [:.destroy] (set-attr :onClick (fn [_] (put! comm [:destroy todo])))
          [:.edit] (set-attr :ref "editField" 
                             :value (om/get-state owner :edit-text)
                             :onBlur (om/bind submit todo owner comm)
                             :onChange (om/bind change todo owner comm)
                             :onKeyDown (om/bind key-down todo owner comm))})))))
