(ns examples.products.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :refer [make-ref deref-dom set-dom-binding! make-dom-binding]]
            [reacl.dom :as dom :include-macros true]))

(enable-console-print!)

(defn product-category-row
  [props]
  (dom/tr
   {:key props}
   (dom/th
    {:colSpan "2"}
    props)))      ; FIXME: really should access this through a lens

(defn product-row
  [product]
  (let [name (if (:stocked product)
                 (:name product)
                 (dom/span
                  {:style #js {:color "red"}} ; whoa! FIXME
                  (:name product)))]
      (dom/tr
       {:key (:name product)}
       (dom/td name)
       (dom/td (str (:price product))))))

(def product-table
  (reacl/class products [filter-text in-stock-only]
   render
   (fn []
     (let [rows
           (mapcat (fn [product last-product]
                     (if (or (= (. (:name product) indexOf filter-text)
                                -1)
                             (and (not (:stocked product))
                                  in-stock-only))
                       []
                       (let [prefix
                             (if (not= (:category product)
                                       (:category last-product))
                               [(product-category-row (:category product))]
                               [])]
                         (concat prefix [(product-row product)]))))
                   products (cons nil products))]
       (dom/table
        (dom/thead
         (dom/tr
          (dom/th "Name")
          (dom/th "Price")))
        (dom/tbody (to-array rows)))))))

(def search-bar
  (reacl/class app-state [filter-text in-stock-only on-user-input]
   render
   (fn [& {:keys [dom-node]}]
     (dom/letdom
      [textbox (dom/input
                {:type "text"
                 :placeholder "Search..."
                 :value filter-text
                 :onChange (fn [e]
                             (on-user-input
                              (.-value (dom-node textbox))
                              (.-checked (dom-node checkbox))))})
       checkbox (dom/input
                 {:type "checkbox"
                  :value in-stock-only
                  :onChange (fn [e]
                              (on-user-input
                               (.-value (dom-node textbox))
                               (.-checked (dom-node checkbox))))})]
       (dom/form
        textbox
        (dom/p
         checkbox
         "Only show products in stock"))))))

(def filterable-product-table
  (reacl/class products []
   render
   (fn [& {:keys [instantiate local-state]}]
     (dom/div
      (instantiate search-bar
                   (:filter-text local-state)
                   (:in-stock-only local-state)
                   handle-user-input)
      (instantiate product-table
                   (:filter-text local-state)
                   (:in-stock-only local-state))))
   initial-state
   {:filter-text ""
    :in-stock-only false}

   handle-user-input
   (reacl/event-handler
    (fn [filter-text in-stock-only]
      (reacl/return :local-state
                    {:filter-text filter-text
                     :in-stock-only in-stock-only})))))

(def products
  [{:category "Sporting Goods" :price "$49.99" :stocked true :name "Football"}
   {:category "Sporting Goods" :price "$9.99" :stocked true :name "Baseball"}
   {:category "Sporting Goods" :price "$29.99" :stocked false :name "Basketball"}
   {:category "Electronics" :price "$99.99" :stocked true :name "iPod Touch"}
   {:category "Electronics" :price "$399.99" :stocked false :name "iPhone 5"}
   {:category "Electronics" :price "$199.99" :stocked true :name "Nexus 7"}])

(js/React.renderComponent
 (reacl/instantiate-toplevel filterable-product-table products)
 (.getElementById js/document "content"))
