(ns angst.library.setup
	(:require [quil.core :as q]
			  [angst.library.utils :refer :all]
			  [angst.library.data :refer :all]
			  [angst.library.network :refer :all]))

(defn set-goals
	"Assigns each empire a unique goal"
	[state]
	(loop [rem-goals '("Immortals" "Warlords" "Conquistadores" "Imperialists" "Slavers")
		   rem-emps (map first (seq (:empire state)))
		   new-state state]
		(if (empty? rem-emps)
			  new-state
			(let [goal (rand-nth rem-goals)]
				(recur (remove #(= % goal) rem-goals)
					   (rest rem-emps)
					   (set-empire-value new-state (first rem-emps) :major goal))))))

(defn get-second-planet [rem-planets choices]
	"Chooses an unoccupied planet from choices"
	(if (empty? choices) nil
		(let [choice (rand-nth choices)]
		(if 
		  (member? choice rem-planets) choice
		  (recur rem-planets (remove #(= % choice) choices))))))

(defn rand-start-planets
	"Gives each player two connected planets at random"
	[state]
	(loop [rem-planets (map first (seq (:planets state)))
		   rem-emps (map first (seq (:empire state)))
		   new-state state]
		(if (empty? rem-emps)
			  new-state
			(let [planet1 (rand-nth rem-planets)
				  planet2 (get-second-planet rem-planets (-> new-state :planets planet1 :connections))
				  colour (:colour ((first rem-emps) (:empire state)))]
				(recur (remove #(or (= % planet1) (= % planet2)) rem-planets)
					   (rest rem-emps)
					   (-> new-state
					   		(update-in [:planets planet1] #(merge % {:colour colour :ship-colour colour :ships 1 :development 3}))
					   		(update-in [:planets planet2] #(merge % {:colour colour :ship-colour colour :ships 1 :development 0}))))))))

(defn fixed-start-planets
	"Sets up a balanced-ish map based on the number of players"
	[state]
	(let [setups
			 {2 [[:Echemmon :Altu] [:VanVogt :Jaid]]
			  3 [[:Brahms :Uchino] [:Path :Lisst] [:Bhowmik :Dengras]]
			  4 [[:Odyssey :Uchino] [:Path :Quinz] [:Erasmus :Iago] [:Bhowmik :Walden]]
			  5 [[:Erasmus :Froya] [:Path :Byrd] [:Chiu :Henz] [:Bhowmik :Walden] [:Valeria :Uchino]]}

		  align-planets 
		  	(fn [x y] {(first y) (merge ((first y) (:planets state)) {:colour (:colour (second x))
		  																		 :ship-colour (:colour (second x))
		  																		 :ships 1
		  																		 :development 3})
					   (second y) (merge ((second y) (:planets state)) {:colour (:colour (second x))
																	 :ship-colour (:colour (second x))
																	 :ships 1
																	 :development 0})})]

		(update-in state [:planets] #(reduce merge % (map align-planets (vec (:empire state)) (setups (count (:empire state))))))))

(comment (defn get-next-player-map
	([empires] (get-next-player-map empires 0 (count empires) {}))
	([empires i end m]
		(if (= i end) m
			(get-next-player-map empires (inc i) end (merge m {(empires i) (empires (mod (inc i) end))}))))))

(defn get-next-player-map
	[empires]
	(loop [i 0 end (count empires) curr-map {}]
		(if (= i end) curr-map
			(recur (inc i) end (merge curr-map {(get empires i) (get empires (mod (inc i) end))})))))

(defn create-empire-data
	"Consumes a collection of keyword-string pairs and produces a map with starting empire data."
	[empires]
	(loop [empire-data {}
		   i 0]
		(if (< i (count empires))
			(let [emp-name (second (get empires i))
				  emp-key (first (get empires i))]
				  (recur (assoc-in empire-data [emp-key] {:name emp-name :colour (get colours i) :resources 8 :vp 0 :major ""}) (inc i)))
			empire-data)))

(defn set-players
	[state empires]
	(if (= (:online-state state) :host)
		(let [empire-names (vec (map #(vector (keyword (clojure.string/replace % " " "")) %) empires))]
			(merge state {:empire (create-empire-data empire-names)
						  :active (first (last empire-names))
						  :next-player-map (get-next-player-map (vec (map first empire-names)))}))
		(merge state {:empire (select-keys all-empires empires)
					  :active (last empires)
					  :next-player-map (get-next-player-map (vec empires))})))

(defn get-planets
	[state]
	(let [planet-map (-> all-planets
						(select-keys (keys (planet-maps (count (:empire state)))))
						(#(reduce-kv (fn [m k v] (update-in m [k] (fn [x] (merge x (hash-map :connections v))))) % (planet-maps (count (:empire state))))))]

	(if (= (count (:empire state)) 5)
		(assoc-in state [:planets] all-planets)
		(assoc-in state [:planets] planet-map))))

(defn game-settings
	"Sets up the planets/objectives according to settings"
	[state]
	(if (member? "rand-start" (:options state))
		(if (member? "goals" (:options state))
			  (rand-start-planets (set-goals state))
			(rand-start-planets state))
		(if (member? "goals" (:options state))
			  (fixed-start-planets (set-goals state))
			(fixed-start-planets state))))

(defn online-setup [state]
	(if (= (:online-state state) :host)
		(-> state
			(update-in [:extra-update-data] #(into % [:components :active-component :active-text-input :buttons]))
			(update-in [:online-alerts] #(assoc-in % [:new-game] nil)))
		state))

(defn new-game
	"Launches a new game according to settings"
	[state]
	(-> state
		(merge init-state)
		(set-players (:empires state))
		(get-planets)
		(game-settings)
		(online-setup)))

(defn setup []
  ; Set frame rate to 20 frames per second.
  (q/frame-rate 20)
  ; Set color mode to HSB (HSV) instead of default RGB.
  (q/color-mode :hsb)
  ; Set line color to grey
  (q/stroke 100)
  ;Set text preferences
  (q/text-align :center)
  (q/text-size 12)
  ; Turn off server
  (.stop host-server)
  ; Initialize state
  setup-state)