(ns ^{:doc "Convenience API for constructing virtual DOM.

  This has ClojureScript wrappers for the various HTML elements.

  These all expect attributes as a ClojureScript map.

  Moreover, sub-element sequences need to be ClojureScript sequences of
  objects constructed using `keyed-dom'.

  Moreover, the `letdom' form constructing virtual DOM elements for
  easy reference in an event handler."}
  reacl2.dom
  (:require-macros [reacl2.dom :refer [defdom]])
  (:require [react :as react]
            [clojure.string :as string]
            [reacl2.core :refer [IHasDom get-dom]])
  (:refer-clojure :exclude (meta map time use set symbol)))

(defn- camelize
  "Camelcases a hyphenated string, for example:
  > (camelize \"background-color\")
  < \"backgroundColor\""
  [s]
  (string/replace s #"-(.)" (fn [[_ c]] (string/upper-case c))))

(defn- camelize-style-name
  "Camelcases a hyphenated CSS property name, for example:
  > (camelize-style-name \"background-color\")
  < \"backgroundColor\"
  > (camelize-style-name \"-moz-transition\")
  < \"MozTransition\"
  > (camelize-style-name \"-ms-transition\")
  < \"msTransition\"

  As Andi Smith suggests
  (http://www.andismith.com/blog/2012/02/modernizr-prefixed/), an `-ms` prefix
  is converted to lowercase `ms`."
  [s]
  (camelize (string/replace s #"^-ms" "ms-")))

(def ^:no-doc reacl->react-attribute-names
  #js {:accept "accept"
       :accept-charset "acceptCharset"
       :access-key "accessKey"
       :action "action"
       :allow-full-screen "allowFullScreen"
       :allow-transparency "allowTransparency"
       :alt "alt"
       :async "async"
       :auto-complete "autoComplete"
       :auto-focus "autoFocus"
       :auto-play "autoPlay"
       :auto-save "autoSave"
       :capture "capture"
       :cell-padding "cellPadding"
       :cell-spacing "cellSpacing"
       :challenge "challenge"
       :char-set "charSet"
       :checked "checked"
       :class-id "classID"
       :class "className"
       :color "color"
       :col-span "colSpan"
       :cols "cols"
       :content "content"
       :content-editable "contentEditable"
       :context-menu "contextMenu"
       :controls "controls"
       :coords "coords"
       :cross-origin "crossOrigin"
       :data "data"
       :date-time "dateTime"
       :default "default"
       :defer "defer"
       :dir "dir"
       :disabled "disabled"
       :download "download"
       :draggable "draggable"
       :enc-type "encType"
       :form "form"
       :form-action "formAction"
       :form-enc-type "formEncType"
       :form-method "formMethod"
       :form-no-validate "formNoValidate"
       :form-target "formTarget"
       :frame-border "frameBorder"
       :headers "headers"
       :height "height"
       :hidden "hidden"
       :high "high"
       :href "href"
       :href-lang "hrefLang"
       :input-mode "inputMode"
       :or "htmlFor"
       :http-equiv "httpEquiv"
       :icon "icon"
       :id "id"
       :key-params "keyParams"
       :key-type "keyType"
       :kind "kind"
       :label "label"
       :lang "lang"
       :list "list"
       :loop "loop"
       :low "low"
       :manifest "manifest"
       :margin-height "marginHeight"
       :margin-width "marginWidth"
       :min-length "minLength"
       :max "max"
       :max-length "maxLength"
       :media "media"
       :media-group "mediaGroup"
       :method "method"
       :min "min"
       :multiple "multiple"
       :muted "muted"
       :name "name"
       :no-validate "noValidate"
       :nonce "nonce"
       :open "open"
       :optimum "optimum"
       :pattern "pattern"
       :placeholder "placeholder"
       :poster "poster"
       :preload "preload"
       :radioGroup "radioGroup"
       :rel "rel"
       :referrer-policy "referrerPolicy"
       :readOnly  "rel"
       :required "required"
       :results "results"
       :reversed "reversed"
       :role "role"
       :rowSpan "rowSpan"
       :rows "rows"
       :sandbox "sandbox"
       :scope "scope"
       :scoped "scoped"
       :scrolling "scrolling"
       :seamless "seamless"
       :security "security"
       :selected "selected"
       :shape "shape"
       :size "size"
       :sizes "sizes"
       :span "span"
       :spell-check "spellCheck"
       :src "src"
       :src-doc "srcDoc"
       :src-set "srcSet"
       :src-lang "srcLang"
       :start "start"
       :step "step"
       :style "style"
       :summary "summary"
       :tab-index "tabIndex"
       :target "target"
       :title "title"
       :type "type"
       :use-map "useMap"
       :value "value"
       :width "width"
       :wmode "wmode"
       :wrap "wrap"
       :auto-capitalize "autoCapitalize"
       :auto-correct "autoCorrect"
       :property "property"
       :item-prop "itemProp"
       :item-scope "itemScope"
       :item-type "itemType"
       :item-ref "itemRef"
       :item-id "itemID"
       :uselectable "uselectable"
       :dangerously-set-inner-html "dangerouslySetInnerHTML"

       ;; SVG
       :clip-path "clipPath"
       :cx "cx"
       :cy "cy"
       :d "d"
       :dx "dx"
       :dy "dy"
       :fill "fill"
       :fill-opacity "fillOpacity"
       :font-family "fontFamily"
       :font-size "fontSize"
       :fx "fx"
       :fy "fy"
       :gradient-transform "gradientTransform"
       :gradient-units "gradientUnits"
       :marker-end "markerEnd"
       :marker-mid "markerMid"
       :marker-start "markerStart"
       :offset "offset"
       :opacity "opacity"
       :pattern-content-units "patternContentUnits"
       :pattern-units "patternUnits"
       :points "points"
       :preserve-aspect-ratio "preserveAspectRatio"
       :r "r"
       :rx "rx"
       :ry "ry"
       :spread-method "spreadMethod"
       :stop-color "stopColor"
       :stop-opacity "stopOpacity"
       :stroke "stroke"
       :stroke-dasharray "strokeDasharray"
       :stroke-linecap "strokeLinecap"
       :stroke-opacity "strokeOpacity"
       :stroke-width "strokeWidth"
       :text-anchor "textAnchor"
       :transform "transform"
       :version "version"
       :view-box "viewBox"
       :x1 "x1"
       :x2 "x2"
       :x "x"
       :y1 "y1"
       :y2 "y2"
       :y "y"
       :xlink-actuate "xlinkActuate"
       :xlink-arcrole "xlinkArcrole"
       :xlink-href "xlinkHref"
       :xlink-role "xlinkRole"
       :xlink-show "xlinkShow"
       :xlink-title "xlinkTitle"
       :xlink-type "xlinkType"
       :xml-base "xmlBase"
       :xml-lang "xmlLang"
       :xml-space "xmlSpace"
       :xmlns "xmlns"
       :xmlns-xlink "xmlnsXlink"

       ;; Event handlers
       :oncopy "onCopy"
       :oncopycapture "onCopyCapture"
       :oncut "onCut"
       :oncutcapture "onCutCapture"
       :onpaste "onPaste"
       :onpastecapture "onPasteCapture"
       :onkeydown "onKeyDown"
       :onkeydowncapture "onKeyDownCapture"
       :onkeypress "onKeyPress"
       :onkeypresscapture "onKeyPressCapture"
       :onkeyup "onKeyUp"
       :onkeyupcapture "onKeyUpCapture"
       :onfocus "onFocus"
       :onfocuscapture "onFocusCapture"
       :onblur "onBlur"
       :onblurcapture "onBlurCapture"
       :onchange "onChange"
       :onchangecapture "onChangeCapture"
       :oninput "onInput"
       :oninputcapture "onInputCapture"
       :onsubmit "onSubmit"
       :onsubmitcapture "onSubmitCapture"
       :onreset "onReset"
       :onresetcapture "onResetCapture"
       :onclick "onClick"
       :onclickcapture "onClickCapture"
       :oncontextmenu "onContextMenu"
       :oncontextmenucapture "onContextMenuCapture"
       :ondoubleclick "onDoubleClick"
       :ondoubleclickcapture "onDoubleClickCapture"
       :ondrag "onDrag"
       :ondragcapture "onDragCapture"
       :ondragend "onDragEnd"
       :ondragendcapture "onDragEndCapture"
       :ondragenter "onDragEnter"
       :ondragentercapture "onDragEnterCapture"
       :ondragexit "onDragExit"
       :ondragexitcapture "onDragExitCapture"
       :ondragleave "onDragLeave"
       :ondragleavecapture "onDragLeaveCapture"
       :ondragover "onDragOver"
       :ondragovercapture "onDragOverCapture"
       :ondragstart "onDragStart"
       :ondragstartcapture "onDragStartCapture"
       :ondrop "onDrop"
       :ondropcapture "onDropCapture"
       :onmousedown "onMouseDown"
       :onmousedowncapture "onMouseDownCapture"
       :onmouseenter "onMouseEnter"
       :onmouseleave "onMouseLeave"
       :onmousemove "onMouseMove"
       :onmousemovecapture "onMouseMoveCapture"
       :onmouseout "onMouseOut"
       :onmouseoutcapture "onMouseOutCapture"
       :onmouseover "onMouseOver"
       :onmouseovercapture "onMouseOverCapture"
       :onmouseup "onMouseUp"
       :onmouseupcapture "onMouseUpCapture"
       :ontouchcancel "onTouchCancel"
       :ontouchcancelcapture "onTouchCancelCapture"
       :ontouchend "onTouchEnd"
       :ontouchendcapture "onTouchEndCapture"
       :ontouchmove "onTouchMove"
       :ontouchmovecapture "onTouchMoveCapture"
       :ontouchstart "onTouchStart"
       :ontouchstartcapture "onTouchStartCapture"
       :onscroll "onScroll"
       :onscrollcapture "onScrollCapture"
       :onwheel "onWheel"
       :onwheelcapture "onWheelCapture"
       :onabort "onAbort"
       :oncanplay "onCanPlay"
       :oncanplaythrough "onCanPlayThrough"
       :ondurationchange "onDurationChange"
       :onemptied "onEmptied"
       :onencrypted "onEncrypted"
       :onended "onEnded"
       :onerror "onError"
       :onloadeddata "onLoadedData"
       :onloadedmetadata "onLoadedMetadata"
       :onloadstart "onLoadStart"
       :onpause "onPause"
       :onplay "onPlay"
       :onplaying "onPlaying"
       :onprogress "onProgress"
       :onratechange "onRateChange"
       :onseeked "onSeeked"
       :onseeking "onSeeking"
       :onstalled "onStalled"
       :onsuspend "onSuspend"
       :ontimeupdate "onTimeUpdate"
       :onvolumechange "onVolumeChange"
       :onwaiting "onWaiting"
       })

(def ^:private reacl->style-names
  "Cache for style names encountered."
  #js {})

(defn ^:no-doc reacl->react-style-name
  "Convert Reacl style name (keyword) to React name (string)."
  [reacl-name]
  (let [reacl-name (name reacl-name)]
    (or (aget reacl->style-names reacl-name)
        (let [react-name (camelize-style-name reacl-name)]
          (aset reacl->style-names reacl-name react-name)
          react-name))))

(defn- aset-style->react
  [obj style value]
  (aset obj (reacl->react-style-name style) value)
  obj)

(defn- styles->react
  "Convert a Clojure map with keyword keys to a JavaScript hashmap with string keys."
  [mp]
  (reduce-kv aset-style->react
             #js {}
             mp))

(defn- aset-attribute [obj k0 v0]
  (let [k (if-let [react-name (aget reacl->react-attribute-names (name k0))]
            react-name
            (name k0))
        v (case k0
            :style (styles->react v0)
            v0)]
    (aset obj k v)
    obj))

(defn- attributes
  "Convert attributes represented as a Clojure map to a React map.

  This knows about `:style`, and expects a Clojure map for the value."

  [mp]
  (reduce-kv aset-attribute
             #js {}
             mp))

(defprotocol ^:private IDomBinding
  (binding-get-dom [this])
  (binding-set-dom! [this v])
  (binding-get-ref [this])
  (binding-set-ref! [this v])
  (binding-get-literally? [this]))

(deftype ^{:doc "Composite object
  containing an atom containing DOM object and a name for referencing.
  This is needed for [[letdom]]."
           :private true}
    DomBinding
 [^{:unsynchronized-mutable true} dom ^{:unsynchronized-mutable true} ref literally?]
  IDomBinding
  (binding-get-dom [_] dom)
  (binding-set-dom! [this v] (set! dom v))
  (binding-get-ref [_] ref)
  (binding-set-ref! [this v] (set! ref v))
  (binding-get-literally? [_] literally?)
  IHasDom
  (-get-dom [_] dom))

(defn ^:no-doc make-dom-binding
  "Internal function for use by `letdom'.
   Make an empty DOM binding from a ref name.

  If `literally?` is not true, gensym the name."
  [n literally?]
  (DomBinding. nil 
               (react/createRef)
               literally?))

(defn dom-node
  "Get the real DOM node associated with a binding.

  Needs the component object."
  [this binding]
  (.-current (binding-get-ref binding)))

(defn- set-dom-key
  "Attach a key property to a DOM object."
  [dom key]
  (react/cloneElement dom #js {:key key}))

(defn keyed
  "Associate a key with a virtual DOM node."
  [key dom]
  (set-dom-key dom key))

(defn- normalize-arg
  "Normalize the argument to a DOM-constructing function.

  In particular, each [[IHasDom]] object is mapped to its DOM object.

  Also, sequences of [[KeyeDom]] sub-elements are mapped to their
  respective DOM elements."
  [arg]
  (cond
   (satisfies? IHasDom arg) (get-dom arg)

   (seq? arg) (to-array (cljs.core/map normalize-arg arg))

   ;; deprecated
   (array? arg) (to-array (cljs.core/map normalize-arg arg))

   :else arg))

(defn attributes?
  "Returns true if `v` will be treated as an attribute map in the
  first optional argument to the DOM-construction functions."
  [v]
  (and (map? v)
       (not (satisfies? IHasDom v))))

(defn ^:no-doc dom-function
  "Internal function for constructing wrappers for DOM-construction function."
  [n]
  (fn
    ([]
     (react/createElement n nil))
    ([maybe]
     (if (attributes? maybe)
       (react/createElement n (attributes maybe))
       (react/createElement n nil (normalize-arg maybe))))
    ([maybe a1]
     (if (attributes? maybe)
       (react/createElement n (attributes maybe) (normalize-arg a1))
       (react/createElement n nil (normalize-arg maybe) (normalize-arg a1))))
    ([maybe a1 a2]
     (if (attributes? maybe)
       (react/createElement n (attributes maybe) (normalize-arg a1) (normalize-arg a2))
       (react/createElement n nil (normalize-arg maybe) (normalize-arg a1) (normalize-arg a2))))
    ([maybe a1 a2 a3]
     (if (attributes? maybe)
       (react/createElement n (attributes maybe) (normalize-arg a1) (normalize-arg a2) (normalize-arg a3))
       (react/createElement n nil (normalize-arg maybe) (normalize-arg a1) (normalize-arg a2) (normalize-arg a3))))
    ([maybe a1 a2 a3 a4 & rest]
     (let [args (cljs.core/map normalize-arg rest)]
       (if (attributes? maybe)
         (apply react/createElement n (attributes maybe) (normalize-arg a1) (normalize-arg a2) (normalize-arg a3) (normalize-arg a4) args)
         (apply react/createElement n nil (normalize-arg maybe) (normalize-arg a1) (normalize-arg a2) (normalize-arg a3) (normalize-arg a4) args))))))

(defn ^:no-doc set-dom-binding!
  "Internal function for use by `letdom'.

  This sets the dom field of a DomBinding object, providing a :ref attribute."
  [dn dom]
  (if-let [ref (aget (.-props dom) "ref")]
    ;; it already has a ref, hopefully unique
    (do
      (assert (not (binding-get-literally? dn)))
      (binding-set-ref! dn ref)
      (binding-set-dom! dn dom))
    (binding-set-dom! dn
                      (react/cloneElement dom #js {:ref (binding-get-ref dn)}))))


(defn strict [c]
  "Element which enables StrictMode, see https://reactjs.org/docs/strict-mode.html"
  (react/createElement react/StrictMode nil c))

;; The following HTML elements are supported by react (http://facebook.github.io/react/docs/tags-and-attributes.html)
(defdom a)
(defdom abbr)
(defdom address)
(defdom area)
(defdom article)
(defdom aside)
(defdom audio)
(defdom b)
(defdom base)
(defdom bdi)
(defdom bdo)
(defdom big)
(defdom blockquote)
(defdom body)
(defdom br)
(defdom button)
(defdom canvas)
(defdom caption)
(defdom cite)
(defdom code)
(defdom col)
(defdom colgroup)
(defdom data)
(defdom datalist)
(defdom dd)
(defdom del)
(defdom details)
(defdom dfn)
(defdom div)
(defdom dl)
(defdom dt)
(defdom em)
(defdom embed)
(defdom fieldset)
(defdom figcaption)
(defdom figure)
(defdom footer)
(defdom form)
(defdom h1)
(defdom h2)
(defdom h3)
(defdom h4)
(defdom h5)
(defdom h6)
(defdom head)
(defdom header)
(defdom hr)
(defdom html)
(defdom i)
(defdom iframe)
(defdom img)
(defdom input)
(defdom ins)
(defdom kbd)
(defdom keygen)
(defdom label)
(defdom legend)
(defdom li)
(defdom link)
(defdom main)
(defdom map)
(defdom mark)
(defdom menu)
(defdom menuitem)
(defdom meta)
(defdom meter)
(defdom nav)
(defdom noscript)
(defdom object)
(defdom ol)
(defdom optgroup)
(defdom option)
(defdom output)
(defdom p)
(defdom param)
(defdom pre)
(defdom progress)
(defdom q)
(defdom rp)
(defdom rt)
(defdom ruby)
(defdom s)
(defdom samp)
(defdom script)
(defdom section)
(defdom select)
(defdom small)
(defdom source)
(defdom span)
(defdom strong)
(defdom style)
(defdom sub)
(defdom summary)
(defdom sup)
(defdom table)
(defdom tbody)
(defdom td)
(defdom textarea)
(defdom tfoot)
(defdom th)
(defdom thead)
(defdom time)
(defdom title)
(defdom tr)
(defdom track)
(defdom u)
(defdom ul)
(defdom var)
(defdom video)
(defdom wbr)
(defdom svg)
(defdom polygon)
(defdom line)
(defdom rect)
(defdom circle)
(defdom ellipse)
(defdom polyline)
(defdom text)
(defdom path)
(defdom defs)
(defdom clipPath)
(defdom g)
(defdom linearGradient)
(defdom radialGradient)
(defdom stop)
(defdom image)
(defdom animate)
(defdom animateColor)
(defdom animateMotion)
(defdom animateTransform)
(defdom set)
(defdom cursor)
(defdom desc)
(defdom feBlend)
(defdom feColorMatrix)
(defdom feComponentTransfer)
(defdom feComposite)
(defdom feConvolveMatrix)
(defdom feDiffuseLighting)
(defdom feDisplacementMap)
(defdom feDistantLight)
(defdom feFlood)
(defdom feFuncA)
(defdom feFuncB)
(defdom feFuncG)
(defdom feFuncR)
(defdom feGaussianBlur)
(defdom feImage)
(defdom feMerge)
(defdom feMergeNode)
(defdom feMorphology)
(defdom feOffset)
(defdom fePointLight)
(defdom feSpecularLighting)
(defdom feSpotLight)
(defdom feTile)
(defdom feTurbulence)
(defdom font)
(defdom marker)
(defdom mask)
(defdom metadata)
(defdom mpath)
(defdom pattern)
(defdom switch)
(defdom symbol)
(defdom textPath)
(defdom tspan)
(defdom use)
(defdom view)

