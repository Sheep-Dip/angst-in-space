(ns angst.library.abilities
	(:require [quil.core :as q]
			  [angst.library.utils :refer :all]
			  [angst.library.data :refer :all]
			  [angst.library.actions :refer :all]
        [angst.library.turn :refer :all]
        [angst.library.actionlog :as log]))

(defn add-effect
	[state effect timer]
	(update-in state [:constant-effects timer] #(vec (cons effect %))))

(def ability-map
  {:Petiska
   ; Move an unconnected fleet
  	{:effect #(-> % (assoc-in [:active-planet] :Petiska)
  							(add-effect :Petiska :phase-end))
		 :reqs []
     :message #(str "The " % " Empire uses Petiska to move an unconnected fleet.")
		 :target-effect #(-> %1 (set-planet-value %2 :moved 0)
		 						(begin-move %2)
		 						(assoc-in [:active-planet] false)
		 						(change-buttons [:end-phase] []))
		 :target-reqs [#(= (get-planet-ship-empire %1 %2) (:active %1))
		 			   #(> (-> %1 :planets %2 :ships) 0)]
     :target-message #(str "The " %1 " Empire uses Petiska to move a fleet from " %2 ".")}

   :Henz
   ; 2 resources -> 1 VP
   {:effect #(-> % (update-empire-value (:active %) :resources (fn [x] (- x 2)))
   							 (update-empire-value (:active %) :vp inc))
    :message #(str "The " % " Empire uses Henz to gain 1 VP, spending 2\u0398.")
		  :reqs [#(> (:resources (empire %)) 1)]}

   :Shoran
   ; Gain 2 resources for each ship you send into combat this turn
   {:effect #(add-effect % :Shoran :turn-end)
    :message #(str "The " % " Empire uses Shoran to gain 2\u0398 for each ship they send into combat for the remainder of the turn.")}

   :Kazo
   ; Choose a planet, paying 1 resource per ship on that planet. Ships may not be moved off of that planet until your next turn.
   {:effect #(-> % (assoc-in [:active-planet] :Kazo)
   				   (add-effect :Kazo (:active %)))
	  :reqs []
	  :target-effect #(-> %1 (update-empire-value (:active %1) :resources (fn [x] (- x (-> %1 :planets %2 :ships))))
	  						 (update-in [:effect-details] (fn [x] (merge x {:Kazo %2})))
 								 (assoc-in [:active-planet] false))
	  :target-reqs [#(>= (:resources (empire %1)) (-> %1 :planets %2 :ships))]
    :target-message #(str "The " %1 " Empire uses Kazo to prevent ships from moving off of " %2 " for the remainder of the round.")}

   :Echemmon
   ;Sets a planet to unused
 		{:effect #(assoc-in % [:active-planet] :Echemmon)
 	   :reqs []
 		 :target-effect #(-> %1 (assoc-in [:planets %2 :used] false)
 	    						(assoc-in [:active-planet] false))
 		 :target-reqs [#(planet-owned? %1 %2 (:active %1))]
     :target-message #(str "The " %1 " Empire uses Echemmon to set " %2 " to unused.")}

   :Odyssey
   ;10 resources -> gain 16 resources
   {:effect #(update-empire-value % (:active %) :resources (fn [x] (+ x 6)))
		:reqs [#(> (:resources (empire %1)) 10)]
    :message #(str "The " % " Empire uses Odyssey to spend 10\u0398 and gain 16 \u0398.")}

   :Chiu
   ;Project: When you start a project, place an extra progress on it for every two progress on Chiu
   {:effect #(start-project % :Chiu)}

   :Caia
   ;Project: 7 progress -> destroy any planet (can be recolonized)
   {:effect #(start-project % :Caia)
    :target-effect #(-> %1 (update-planet-value :Caia :progress (fn [x] (- x 7)))
   		  		   		   (update-in [:planets %2] (fn [x] (merge x {:colour "Black" :ship-colour "Black" :ships 0 :moved 0 :development -1})))
   		  				   (assoc-in [:active-planet] false))
    :target-message #(str "The " % " Empire uses the Caia Project to destroy " %2 "!")}

   :Xosa
   ;Project: 3 progress -> destroy 2 ships on any planet
   {:effect #(start-project % :Xosa)
    :target-effect #(-> %1 (update-planet-value :Xosa :progress (fn [x] (- x 3)))
   		  				   (update-planet-value %2 :ships (fn [x] (max 0 (- x 2))))
   		  				   (assoc-in [:active-planet] false))
    :target-message #(str "The " % " Empire uses the Xosa Project to destroy 2 ships on " %2 ".")}

   :Altu
   ; Gain 2 resources when you start a project or use a planet ability
   {:effect #(add-effect % :Altu :turn-end)
    :message #(str "The " % " Empire uses Altu to gain 2\u0398 whenever they start a project or use a planet ability this turn.")}

   :Valeria
   ; Remove a ship from one of your planets -> gain 6 resources
   {:effect #(assoc-in % [:active-planet] :Valeria)
    :target-effect #(-> %1 (update-planet-value %2 :ships dec)
    						(update-empire-value (:active %1) :resources (fn [x] (+ x 6)))
    						(assoc-in [:active-planet] false))
  	:target-reqs [#(> (-> %1 :planets %2 :ships) 0)
  				  #(planet-owned? %1 %2 (:active %1))]
    :target-message #(str "The " %1 " Empire uses Valeria to remove a ship from " %2 " and gain 6\u0398.")}

   :Uchino
   ; 3 resources -> build a ship on a connected planet you control
   {:effect #(-> % (update-empire-value (:active %) :resources (fn [x] (- x 3)))
 	 			   (assoc-in [:active-planet] :Uchino))
  	:reqs [#(> (:resources (empire %)) 2)]
  	:target-effect #(-> %1 (update-planet-value %2 :ships inc)
  						   (assoc-in [:active-planet] false))
  	:target-reqs [#(member? %2 (:connections ((:active-planet %1) (:planets %1))))
  			 	  #(planet-owned? %1 %2 (:active %1))]
    :target-message #(str "The " %1 " Empire uses Uchino to build a ship on " %2 ", paying 3\u0398.")}

   :Thalia
   ; Project: All your planets have +1 defense for every 3 progress
   {:effect #(start-project % :Thalia)}

   :Kanolta
   ;Moves any number of ships to itself
   {:effect #(-> % (assoc-in [:active-planet] :Kanolta)
        			(change-buttons [:end-phase] [:cancel-ability]))
   	:reqs []
    :message #(str "The " %1 " Empire uses Kanolta to move any number of ships to Kanolta.")
   	:target-effect #(-> %1 (update-planet-value %2 :ships dec)
   							(update-planet-value :Kanolta :ships inc))
   	:target-reqs [#(planet-owned? %1 %2 (:active %1))
   				   #(> (:ships (%2 (:planets %1))) 0)]
    :target-message #(str "The " %1 " Empire moves a ship from " %2 " to Kanolta.")}

   :Salman
   ;1 resource -> build one ship (can move this turn)
   {:effect #(-> % (update-empire-value (:active %) :resources dec)
   						   (update-planet-value :Salman :ships inc))
    :reqs [#(> (:resources (empire %)) 0)]
    :message #(str "The " % " Empire builds a ship on Salman, spending 1\u0398.")}

   :Zellner
   ;Project: all your planets less than # of progress spaces away have +1 defense
   {:effect #(start-project % :Zellner)}

   :Iago
   ;Copies the special ability of any uncolonized planet
   ;Cost: 3 resources
   {:effect #(-> % (update-empire-value (:active %) :resources (fn [x] (- x 3)))
   				   (assoc-in [:active-planet] :Iago))
  	:reqs [#(> (:resources (empire %)) 2)]
  	:target-effect #(assoc-in ((:effect (%2 ability-map)) %1) [:active-planet] false)
  	:target-reqs [#(= (:colour (%2 (:planets %1))) "Black")]
    :target-message #(str "The " %1 " Empire uses Iago to copy the ability of " %2 ".")}

   :Tomaso
   ;Reduces all travel costs by 2 for the turn
   {:effect #(update-in % [:constant-effects :turn-end] (fn [x] (cons :Tomaso x)))
    :message #(str "The " % " Empire uses Tomaso to reduce all travel costs by 2\u0398 for the remainder of the turn.")}

   :Tyson 
   ; Move a ship from Tyson to any planet not controlled by another player
   {:effect #(assoc-in % [:active-planet] :Tyson)
    :reqs [#(> (:ships (:Tyson (:planets %))) 0)]
  	:target-effect #(-> %1 (update-planet-value :Tyson :ships dec)
  						 	(update-planet-value %2 :ships inc)
  						 	(set-planet-value %2 :ship-colour (:colour (empire %1)))
  						 	(assoc-in [:active-planet] false))
  	:target-reqs [#(or (= (:colour (%2 (:planets %1))) "Black") (= (:colour (%2 (:planets %1))) (:colour (empire %1))))]
    :target-message #(str "The " %1 " Empire moves a ship from Tyson to " %2 ".")}

   :Walden
   ;Increases all active player's development levels by 1
   {:effect develop-all
    :reqs []
    :message #(str "The " % " Empire uses Walden to increase all their planets' development by 1.")}

   :Ryss
   ; 2 resources -> Choose a planet. That planet may not be used until your next turn.
   {:effect #(assoc-in % [:active-planet] :Ryss)
    :target-effect #(-> %1 (add-effect :Ryss (:active %1))
    					   (update-empire-value (:active %1) :resources (fn [x] (- x 2)))
    					   (update-in [:effect-details] (fn [x] (merge x {:Ryss %2})))
    					   (assoc-in [:active-planet] false))
    :target-message #(str "The " %1 " Empire uses Ryss to prevent " %2 " from being used until their next turn.")}

   :Fignon
   ;Produces three resources
   {:effect #(update-empire-value % (:active %) :resources (fn [x] (+ x 3)))
    :reqs []
    :message #(str "The " % " Empire uses Fignon to gain 3\u0398.")}

   :Algoa
   ;Builds 3 ships
   {:effect #(-> % (update-planet-value :Algoa :ships (fn [x] (+ x 3)))
   					(set-planet-value :Algoa :ship-colour (:colour (empire %)))
      				(update-planet-value :Algoa :moved (fn [x] (+ x 3)))
   			    	(update-empire-value (:active %) :resources (fn [x] (- x 8))))
   	:reqs [#(> (:resources (empire %)) 7)]
    :message #(str "The " % " Empire builds 3 ships on Algoa, spnding 8\u0398.")}

   :VanVogt
   ;Sends a ship from Van Vogt to colonize any uncolonized planet
   ;Cost: 2 + colonization cost
   {:effect #(-> % (update-empire-value (:active %) :resources (fn [x] (- x 5)))
   				    (assoc-in [:active-planet] :VanVogt))
  	:reqs [#(> (:resources (empire %)) 4)
  			#(> (:ships (:VanVogt (:planets %))))]
  	:target-effect #(-> %1 (update-planet-value :VanVogt :ships dec)
     							(set-planet-value %2 :colour (:colour (empire %1)))
                  (set-planet-value %2 :ship-colour (:colour (empire %1)))
                  (set-planet-value %2 :used true)
    	  					(assoc-in [:active-planet] false))
  	:target-reqs [#(= (:colour (%2 (:planets %1))) "Black")]
    :target-message #(str "The " % " Empire sends a ship from Van Vogt to " %2 ", paying 5\u0398 and colonizing it.")}

   :Jaid
   ;Whenever a planet gains progress, it gains an additional progress
   {:effect #(add-effect % :Jaid (:active %))
    :message #(str "The " % " Empire uses Jaid to add an additional progress to any projects that gain progress this turn.")}

   :Bhowmik 
   ;Project: 1 progress -> 1 resource per progress on Bhowmik, including the one spent
   {:effect #(start-project % :Bhowmik)}

   :Dengras
   ;Whenever a player attacks one of your planets, that player loses 4 resources
   {:effect #(add-effect % :Dengras (:active %))
    :message #(str "The " % " Empire uses Dengras to force any player attacking their planets to lose 4\u0398 for the remainder of the round.")}

   :Glushko
   ;Forces target player to lose 4 resources
   {:effect #(assoc-in % [:active-planet] :Glushko)
    :reqs []
    :target-effect #(-> %1 (update-empire-value (get-planet-empire %1 %2) :resources (fn [x] (max 0 (- x 4))))
    						(assoc-in [:active-planet] false))
    :target-reqs [#(not= (:colour (%2 (:planets %1))) "Black")]
    :target-message #(str "The " %1 " Empire uses Glushko to force the player controlling " %2 " to lose 4\u0398.")}

   :Marishka
   ;Whenever you conquer a planet this turn, place one ship there
   {:effect #(add-effect % :Marishka (:active %))
    :message #(str "The " % " Empire uses Marishka to place one ship on any planet they conquer for the remainder of the turn.")}

   :Entli ;Project: 5 progress -> build 3 ships on any planet you control
   {:effect #(start-project % :Entli)
    :target-effect #(-> %1 (update-planet-value :Xosa :progress (fn [x] (- x 5)))
   	  						 (update-planet-value %2 :ships (fn [x] (+ x 3)))
   	  						 (assoc-in [:active-planet] false))
   	:target-reqs [#(= (:colour (%2 (:planets %1))) (:colour (empire %1)))]
    :target-message #(str "The " %1 " Empire uses the Entli Project to build 3 ships on " %2 ".")}

   :Beek
   ;Copies the special ability of a planet you control
   		{:effect #(-> % (update-empire-value (:active %) :resources (fn [x] (- x 2)))
   						 (assoc-in [:active-planet] :Beek))
		 :reqs [#(> (:resources (empire %)) 1)]
		 :target-effect #((:effect (%2 ability-map)) %1)
		 :target-reqs [#(= (:colour (%2 (:planets %1))) (:colour (empire %1)))]
     :target-message #(str "The " %1 " Empire uses Beek to copy the ability of " %2 ".")}

   :Froya
   ; 6 resources -> all your planets have +1 defense
   {:effect #(-> % (add-effect :Froya (:active %))
   					(update-empire-value (:active %) :resources (fn [x] (- x 6))))
	  :reqs [#(> (:resources (empire %)) 6)]
    :message #(str "The " % " Empire uses Froya to give all their planets +1 defense until their next turn, paying 6\u0398.")}

   :Erasmus
   ;Project: X progress -> place X-1 ships on Erasmus
   {:effect #(start-project % :Erasmus)}

   :Brahms
   ; Increase all movement costs by 2 until your next turn
   {:effect #(add-effect % :Brahms (:active %))
    :message #(str "The " % " Empire uses Brahms to increase all movement costs by 2 until their next turn.")}

   :Lisst
   ;Builds 2 ships
   	{:effect #(-> % (update-planet-value :Lisst :ships (fn [x] (+ x 2)))
   					(set-planet-value :Lisst :ship-colour (:colour (empire %)))
   	    			(update-planet-value :Lisst :moved (fn [x] (+ x 2)))
   			    	(update-empire-value (:active %) :resources (fn [x] (- x 5))))
   	 :reqs [#(> (:resources (empire %)) 4)]
     :message #(str "The " % " Empire builds 2 ships on Lisst, paying 5\u0398.")}

   :Nussbaum
   ;Add 1 progress to a planet
   {:effect #(assoc-in % [:active-planet] :Nussbaum)
    :target-effect #(-> %1 (add-progress %2 1)
   							 (assoc-in [:active-planet] false))
   	:target-reqs [#(= (:colour (%2 (:planets %1))) (:colour (empire %1)))
   					#(-> %1 :planets %2 :project)]
    :target-message #(str "The " %1 " Empire uses Nussbaum to add 1 progress to " %2 ".")}

   :Path
   ;Choose a planet. That planet has +2 defense until the end of the round.
   {:effect #(assoc-in % [:active-planet] :Path)
   	:target-effect #(-> %1 (add-effect :Path (:active %1))
   	  					     (update-in [:effect-details] (fn [x] (merge x {:Path %2})))
   							 (assoc-in [:active-planet] false))
    :target-message #(str "The " %1 " Empire uses Path to give " %2 "+2 defense until their next turn.")}

   :Quinz
   ; 4 resources -> choose a planet. That planet has -1 defense this turn.
   {:effect #(assoc-in % [:active-planet] :Quinz)
   	:reqs [#(> (:resources (empire %)) 3)]
    :target-effect #(-> %1 (add-effect :Quinz (:active %1))
   	  					     (update-in [:effect-details] (fn [x] (merge x {:Quinz %2})))
   							 (assoc-in [:active-planet] false)
   							 (update-empire-value (:active %1) :resources (fn [x] (- x 4))))
	  :target-reqs [#(not (= (-> %1 :planets %2 :colour) "Black"))]
    :target-message #(str "The " %1 " Empire uses Quinz to give " %2 " -1 defense this turn, paying 4\u0398.")}

   :Byrd
   ;Project: Reduce all travel costs by 1 for each progress
   {:effect #(start-project % :Byrd)}

   :Yerba
   ;Colonization costs 0 resources this turn
   {:effect #(update-in % [:constant-effects :turn-end] (fn [x] (cons :Yerba x)))
    :message #(str "The " % " Empire uses Yerba to make colonizations cost 0\u0398 this turn.")}
   })


(def project-effects
    "For projects that do something when you click on them while active"
    {:Caia {:effect (fn [state progress]
                      (if (< progress 7)
                        (-> state (update-empire-value (:active state) :resources #(- % progress))
                                  (add-progress :Caia 1))
                        (assoc-in state [:active-planet] :Caia)))
            :reqs [(fn [state progress] (>= (:resources (empire state)) progress))]
            :message #(str "The " %1 " Empire places an additional progress on Caia, spending " (dec %2) "\u0398.")}
    :Bhowmik {:effect (fn [state progress]
                        (-> state (update-empire-value (:active state) :resources #(+ % progress))
                                  (update-planet-value :Bhowmik :progress dec)))
              :reqs [(fn [state progress] (> progress 0))]
              :message #(str "The " % " Empire uses the Bhowmik Project to gain " %2 "\u0398.")}
    :Xosa {:effect (fn [state progress] (assoc-in state [:active-planet] :Xosa))
           :reqs [(fn [state progress] (>= progress 3))]}
    :Entli {:effect (fn [state progress] (assoc-in state [:active-planet] :Entli))
            :reqs [(fn [state progress] (>= progress 5))]}
    :Erasmus {:effect (fn [state progress]
                        (-> state (update-planet-value :Erasmus :ships #(+ % (- progress 1)))
                                  (set-planet-value :Erasmus :progress 0)))
              :reqs [(fn [state progress] (>= progress 2))]
              :message #(str "The " % " Empire uses the Erasmus Project to build " (dec %2) " ships on Erasmus.")}})

(defn use-ability
  "Types: ability-map for normal abilities, project-effects for active projects"
  [state planet type]
  (let [ability (planet type)]
    (if (= type ability-map)
      (if (every? #(% state) (:reqs ability))
              (log/add-ability-entry ((:effect (planet type)) state) (:message ability) (:active state))
               state)
      (if (and ability   ;Checks if project has clickable action
           (every? #(% state (-> state :planets planet :progress)) (:reqs (planet type)))  ;Checks all reqs
           (= (-> state :planets planet :project) "active"))   ;Project must be active to use click ability
        (-> state
            (#((:effect ability) % (-> state :planets planet :progress)))
            (log/add-ability-entry (:message ability) (:active state) (-> state :planets planet :progress)))
        state))))

(defn target-effect
	"Performs active planet ability's effect on planet"
	[state planet]
	(if (every? #(% state planet) (:target-reqs ((:active-planet state) ability-map)))
			(log/add-ability-entry
          ((:target-effect ((:active-planet state) ability-map)) state planet)
          (:target-message ((:active-planet state) ability-map))
          (:active state)
          planet)
			state))