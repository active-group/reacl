(ns examples.products.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(enable-console-print!)

(defn product-category-row [cat]
  (dom/tr
   (dom/th
    {:colSpan "2"}
    cat)))

(defn product-row [product]
  (let [name (if (:stocked product)
               (:name product)
               (dom/span
                {:style {:color "red"}}
                (:name product)))]
    (dom/tr
     (dom/td name)
     (dom/td (str (:price product))))))

(reacl/defclass product-table this [products filter-text in-stock-only?]
  render
  (let [rows
        (mapcat (fn [product last-product]
                  (let [cat (:category product)]
                    (if (or (= (. (:name product) indexOf filter-text)
                               -1)
                            (and (not (:stocked product))
                                 in-stock-only?))
                      []
                      (let [prefix
                            (if (not= cat
                                      (:category last-product))
                              [(dom/keyed cat (product-category-row cat))]
                              [])]
                        (concat prefix [(dom/keyed (:name product) (product-row product))])))))
                products (cons nil products))]
    (dom/table
     (dom/thead
      (dom/tr
       (dom/th "Name")
       (dom/th "Price")))
     (dom/tbody rows))))

(reacl/defclass input-text this value [attrs]
  render
  (dom/input (-> (merge {:type "text"} attrs)
                 (assoc :value value)
                 (assoc :onchange
                        (fn [e]
                          (reacl/send-message! this (.. e -target -value))))))
  handle-message
  (fn [new-text]
    (reacl/return :app-state new-text)))

(reacl/defclass input-checkbox this value [attrs]
  render
  (dom/input (-> (merge {:type "checkbox"} attrs)
                 (assoc :value value)
                 (assoc :onchange
                        (fn [e]
                          (reacl/send-message! this (.. e -target -checked))))))
  handle-message
  (fn [new-checked]
    (reacl/return :app-state new-checked)))

(defrecord SearchParams
    [filter-text
     in-stock-only?])

(reacl/defclass search-bar this params []
  render
  (dom/form {}
            (input-text (reacl/bind this :filter-text)
                        {:placeholder "Search..."})
            (dom/p (input-checkbox (reacl/bind this :in-stock-only?))
                   "Only show products in stock")))

(reacl/defclass filterable-product-table
  this [products]

  local-state [search-params (SearchParams. "" false)]

  render
  (dom/div
   (search-bar (reacl/bind-local this))
   (product-table products
                  (:filter-text search-params)
                  (:in-stock-only? search-params))))

(def products
  [{:category "Sporting Goods" :price "$49.99" :stocked true :name "Football"}
   {:category "Sporting Goods" :price "$9.99" :stocked true :name "Baseball"}
   {:category "Sporting Goods" :price "$29.99" :stocked false :name "Basketball"}
   {:category "Electronics" :price "$99.99" :stocked true :name "iPod Touch"}
   {:category "Electronics" :price "$399.99" :stocked false :name "iPhone 5"}
   {:category "Electronics" :price "$199.99" :stocked true :name "Nexus 7"}])

(reacl/render-component
 (.getElementById js/document "app-products")
 filterable-product-table products)
