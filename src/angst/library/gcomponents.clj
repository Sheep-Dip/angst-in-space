(ns angst.library.gcomponents
	(:require [quil.core :as q]
           	  [quil.middleware :as m]
           	  [angst.library.utils :refer :all]
           	  [angst.library.data :refer :all]
           	  [angst.library.gdata :refer :all]))

(defn text-display
  "Consumes a map giving relative alignments and locations of text, and draws it at a given location"
  [text-map x y]
  (doseq [t (seq text-map)]
    (if (string? (first t)) (do
     (q/text-align (:align (second t)))
     (q/text-size (:size (second t)))
     (q/text (first t) (+ x (:x (second t))) (+ y (:y (second t)))))))
  (q/text-size 12))

(defn get-ip-message []
  (try (let [host-ip (slurp "hostaddress.txt")]
          (if (empty? host-ip)
            "Put host's IP address in hostaddress.txt to connect to a server"
            (str "Target host IP address: " host-ip " (edit hostaddress.txt to change)")))
    (catch Exception e "Create hostaddress.txt with host's IP address to connect to a server")))

(defn draw-menu [state]
  (q/background 0)
  (text-display {"Angst In Space" {:align :center :x (scalex 683) :y (scaley 100) :size 40}} 0 0))

(defn draw-distance
  [x1 y1 x2 y2]
  (let [distance (get-distance x1 y1 x2 y2)]
    (if (and (not= distance 0))
      (dotimes [n distance]
          (set-fill "Black")
          (q/ellipse (+ (scalex x1) (* n (/ (- (scalex x2) (scalex x1)) distance)))
                     (+ (scaley y1) (* n (/ (- (scaley y2) (scaley y1)) distance)))
                     5 5)))))

(defn draw-connections [state]
  (q/stroke-weight 1)
  (doseq [p (seq (:planets state))]
    (doseq [q (:connections (second p))]
      (q/line (scalex (:x (second p)))
              (scaley (:y (second p)))
              (scalex (:x (q (:planets state))))
              (scaley (:y (q (:planets state)))))
      (draw-distance (:x (second p))
                     (:y (second p))
                     (:x (q (:planets state)))
                     (:y (q (:planets state)))))))

(defn draw-ships [x y ships]
  (doseq [s ships]
      (q/ellipse (+ x (* -3 (count ships)) (* s 6) 3) (+ y 15) 5 5)))

(defn draw-planets [state]
  (doseq [p (seq (:planets state))]
    ;Set planet colour
    (set-fill (:colour (second p)))

    ;Set bold ring to white if planet is active player's and unused, orange if active project
    (if (and (= (:colour (second p)) (:colour (empire state))) (not (:used (second p))))
      (do
        (q/stroke 0 0 255)
        (q/stroke-weight 2)))
    (if (= (:project (second p)) "active")
      (do
        (q/stroke 25 255 255)
        (q/stroke-weight 2)))

    ;Draw planet circles
    (q/ellipse (scalex (:x (second p))) (scaley (:y (second p))) 21 21)

    ;Draw alert symbols
    (set-fill "Black")
    (q/stroke-weight 1)
    (q/text-size 15)
    (if (planet-alert? state (first p))
      (q/text-char \! (scalex (:x (second p))) (scaley (+ 5 (:y (second p))))))
    (q/text-size 12)
    (q/stroke 0 0 100)

    ;Name the planets
    (q/text-align :center)
    (set-fill "White")
    (q/stroke-weight 1)
    (q/text (:name (second p)) (scalex (:x (second p))) (scaley (- (:y (second p)) 15)))

    ;Draw the ships
    (set-fill (:ship-colour (second p)))
    (draw-ships (scalex (:x (second p))) (scaley (:y (second p))) (range (:ships (second p))))

    (q/stroke-weight 1)))

(defn draw-map [state]
	(do (draw-connections state)
		(draw-planets state)))

(defn get-message [state]
  (cond 
  	(and (not= (:online-state state) :offline) (not= (:online-name state) (:name (empire state))))
  		(str "Waiting for the " (:name (empire state)) " Empire")
    (:ship-move (:effects state))
                  (str "Moving " (get-plural (:ships (:ship-move (:effects state))) "ship" "ships") " from "
                  	(:name ((:planet (:ship-move (:effects state))) (:planets state))))
    (= (:phase state) 0)
       (if (:active-planet state)
           (str "Select target for " (:name ((:active-planet state) (:planets state))))
           "Click on planets to use their abilities")
    (= (:phase state) 1)
       "Click on planets to gain resources"
    (= (:phase state) 2)
       (if (:active-planet state)
           (str "Commanding with " (:name ((:active-planet state) (:planets state))) "\nChoose a connected planet to move ships from")
           "Choose a planet to command with")
    (= (:phase state) 3)
        "Click on planets to build ships"
    (= (:phase state) 4)
       "Choose planets to colonize"
    :else "Something's wrong..."))

(defn draw-planet-info [planet]
  (if (>= (:development planet) 0)
        (do
          (set-fill "Black")
          (q/ellipse (+ (scalex 1066) 98 (* 15 (:development planet))) 70 18 18)      
          (set-fill "White")))
  (text-display {(:name planet) {:align :center :x (scalex 150) :y 20 :size 18}
             (str "Ships: " (:ships planet)) {:align :left :x 20 :y 50 :size 12}
             (str "Production:  " (clojure.string/join "  " (:production planet))) {:align :left :x 20 :y 75 :size 12}
             (str (split-text-lines (:ability planet) (scalex 260))) {:align :left :x 20 :y 125 :size 12}
             (if (:project planet) (str "Project: " (if (= (:project planet) "inactive") "Inactive" (str (:progress planet) "\u00a7"))))
             (if (:project planet) {:align :left :x 20 :y 100 :size 12})}
            (scalex 1066) 0))

(defn draw-text-list [text-lines x y]
	(loop [i 0 lines 0]
		(if (< i (count text-lines))
			(do
				(q/text (get text-lines i) (scalex x) (scaley (+ y (* lines 20))))
				(recur (inc i) (+ lines (count (clojure.string/split-lines (get text-lines i)))))))))

(defn draw-action-log [action-log]
	(q/text-align :left)
	(set-fill "White")
	(draw-text-list action-log (+ infobar-right-edge 10) message-log-start)
	(q/text-align :center))

(defn draw-infobar [state]
  (set-fill "White")
  (let [right-edge (scalex infobar-right-edge)]
  ;vertical line
  (q/line right-edge 0 right-edge (q/height))
  ;horizontal partitions
  (q/line right-edge (scaley planet-info-bottom) (q/width) (scaley planet-info-bottom))
  (q/line right-edge empire-display-top (q/width) empire-display-top)
  (q/line right-edge (scaley empire-display-bottom) (q/width) (scaley empire-display-bottom))
  ;planet info
  (if @planet-info-display
    (draw-planet-info (@planet-info-display (:planets state))))

  ;Empire info
  (let [my-empire (if (= (:online-state state) :offline)
  						(empire state) 
  						((:online-name-key state) (:empire state)))]
  (text-display {(str (:name my-empire) " Empire") {:align :center :x (scalex 150) :y 20 :size 18}
                 (:major my-empire) {:align :center :x (scalex 150) :y 42 :size 12}
                 (str "Resources: " (:resources my-empire)) {:align :left :x 20 :y 60 :size 12}
                 (str "Victory Points:" (:vp my-empire)) {:align :left :x 20 :y 80 :size 12}
                 (str "Upkeep: " (get upkeep (get-num-planets state my-empire))) {:align :left :x 20 :y 100 :size 12}}
                 right-edge
                 (scaley empire-display-top))))

  ;Message above button
  (let [message (get-message state)]
  	(text-display {message {:align :center :x 0 :y 0 :size 12}}
  				  (scalex infobar-center-x)
  				  (scaley button-message-y)))
  ;Action log
  (draw-action-log (:action-log state)))

(defn draw-modal [state width height text fontsize]
	(set-fill "Dark Grey")
	(q/rect-mode :center)
	(q/rect (/ (q/width) 2) (/ (q/height) 2) width height 10)
	(q/rect-mode :corner)
	(set-fill "White")
	(text-display {text {:align :center :x 0 :y (+ (- (/ height 2)) 25) :size fontsize}} (/ (q/width) 2) (/ (q/height) 2)))

(defn draw-game-menu [state]
	(draw-modal state 300 350 "Options" 24))
	
	(comment (set-fill "Dark Grey")
	(q/rect-mode :center)
	(q/rect (/ (q/width) 2) (/ (q/height) 2) 300 350 10)
	(q/rect-mode :corner)
	(set-fill "White")
	(text-display {"Options" {:align :center :x 0 :y -125 :size 24}} (/ (q/width) 2) (/ (q/height) 2)))

(defn draw-save-confirm [state]
	(draw-modal state 400 200 "Save successful!" 18))
	
	(comment (set-fill "Dark Grey")
	(q/rect-mode :center)
	(q/rect (/ (q/width) 2) (/ (q/height) 2) 400 200 10)
	(q/rect-mode :corner)
	(set-fill "White")
	(text-display {"Save successful!" {:align :center :x 0 :y -75 :size 18}} (/ (q/width) 2) (/ (q/height) 2)))

(defn draw-could-not-connect [state]
	(draw-modal state 400 200 "Could not connect." 18))

	(comment (set-fill "Dark Grey")
	(q/rect-mode :center)
	(q/rect (/ (q/width) 2) (/ (q/height) 2) 400 200 10)
	(q/rect-mode :corner)
	(set-fill "White")
	(text-display {"Could not connect." {:align :center :x 0 :y -75 :size 18}} (/ (q/width) 2) (/ (q/height) 2)))

(defn draw-setup-local [state])

(defn draw-setup-online [state]
	;player list

	(q/text-size 18)
	(q/text "Players:" (scalex 200) (scaley 200))
	(draw-text-list (vec (:empires state)) 200 230)
	(q/text-size 12)

	;chatlog
	(set-fill "Black")
	(q/rect-mode :corner)
	(q/rect infobar-right-edge (- message-log-start 15) infobar-width (+ message-log-height 20) 0)
	(draw-action-log (:action-log state)))

(defn draw-choose-name [state]
	(draw-modal state 400 200 "Choose your name:" 18))

(defn draw-set-host-ip [state]
	(draw-modal state 400 200 "Host IP Address:" 18))

(defn draw-connection-lost [state]
	(draw-modal state 400 200 "Connection Lost :(\nAttempting to reconnect" 18))

(def components
	{:main-menu
		{:draw-fn draw-menu
		 :active '()
		 :buttons [:setup-local-game :setup-online-game :join-server]
		 :text-input nil}

	 :setup-local
	 	{:draw-fn draw-setup-local
	 	 :active '()
	 	 :buttons [:setup-back :load-save :start-local-game :choose-sheep :choose-gopher
	 	 		   :choose-muskox :choose-llama :choose-flamingo :opt-rand-start :opt-objectives]
	 	 :text-input nil}

	 :setup-host
	 	{:draw-fn draw-setup-online
	 	 :active '()
	 	 :buttons [:setup-back :load-save :start-online-game :opt-rand-start :opt-objectives]
	 	 :text-input :chat-input}

	 :setup-client

	 	{:draw-fn draw-setup-online
	 	 :active '()
	 	 :buttons [:setup-back]
	 	 :text-input :chat-input}

	 :choose-name
	 	{:draw-fn draw-choose-name
	 	 :active '(:choose-name)
	 	 :buttons [:accept-choose-name]
	 	 :text-input :name-input}

	 :set-host-ip
	 	{:draw-fn draw-set-host-ip
	 	 :active '(:set-host-ip)
	 	 :buttons [:accept-set-ip]
	 	 :text-input :ip-input}

	 :could-not-connect-message
	 	{:draw-fn draw-could-not-connect
		 :active '(:could-not-connect-message)
		 :buttons [:accept-could-not-connect]
		 :text-input nil}

	 :map
	 	{:draw-fn draw-map
 	     :active '()
 	     :buttons [:game-menu]
 	     :text-input nil}

	 :infobar
	 	{:draw-fn draw-infobar
	     :active '()
	     :buttons [:end-phase]
	     :hidden-buttons [:cancel-move :done-command :cancel-ability]
	     :text-input :chat-input}

	 :game-menu
	 	{:draw-fn draw-game-menu
		 :active '(:game-menu)
		 :buttons [:game-menu-back :game-save :game-load :game-quit]
		 :text-input nil}

	 :save-success
	 	{:draw-fn draw-save-confirm
		 :active '(:save-success)
		 :buttons [:accept-save-success]
		 :text-input nil}

	 :connection-lost
	    {:draw-fn draw-connection-lost
	     :active '(:connection-lost)
	     :buttons [:accept-connection-lost]
	     :text-input nil}})