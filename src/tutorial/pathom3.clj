(ns tutorial.pathom3
  (:gen-class)
  (:require
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]))

;; 1. global resolver + minimal pathom

(pco/defresolver answer []
  {:answer-to-everything 42})

; Pathom3 convenience feature that allows the resolvers to be called like a function
(comment
  (answer))
  ;=> {:answer-with-dependency 42}

; equivalent resolver with meta-map 
(pco/defresolver answer-with-meta-map []
  {::pco/input []
   ::pco/output [:answer-to-everything]}
  {:answer-to-everything 42})

(comment
  (answer-with-meta-map))
  ;=> {:answer-with-dependency 42}

; registering the 2 resolvers into a Pathom environment
(def env (pci/register [answer answer-with-meta-map]))

; processing an EQL query of :answer-to-everything on `env` with an empty starting state
(comment
  (p.eql/process
    env
    {}
    [:answer-to-everything]))
  ;=> {:answer-to-everything 42}


;; 2. Resolver with input dependencies
(pco/defresolver answer-with-dependency [{:keys [answer-to-everything]}]
  {::pco/input [:answer-to-everything]
   ::pco/output [:answer-with-dependency]}
  {:answer-with-dependency answer-to-everything})

; testing locally
(comment
  (answer-with-dependency {:answer-to-everything 42}))
  ;=> {:answer-with-dependency 42}

(def env (pci/register [answer
                        answer-with-meta-map
                        answer-with-dependency]))

(comment
  (p.eql/process
    env
    {}
    [:answer-with-dependency]))
  ;=> {:answer-with-dependency 42}

; Process with starting state
(comment
  (p.eql/process
    env
    {:answer-to-everything 50}
    [:answer-with-dependency]))
  ;=> {:answer-with-dependency 50}

;; 3. Small example showing alias and joins
(pco/defresolver protein-g->calories [{:protein/keys [g]}]
  {::pco/input [:protein/g]
   ::pco/output [:protein/calories]}
  {:protein/calories (* 4 g)})

(pco/defresolver carbohydrates-g->calories [{:carbohydrates/keys [g]}]
  {::pco/input [:carbohydrates/g]
   ::pco/output [:carbohydrates/calories]}
  {:carbohydrates/calories (* 4 g)})

(pco/defresolver fats-g->calories [{:fats/keys [g]}]
  {::pco/input [:fats/g]
   ::pco/output [:fats/calories]}
  {:fats/calories (* 9 g)})

(comment
  (fats-g->calories {:fats/g 4}))
  ;=> {:fats/calories 36}

(def aliases [(pbir/alias-resolver :protein/calories :calories)
              (pbir/alias-resolver :carbohydrates/calories :calories)
              (pbir/alias-resolver :fats/calories :calories)])

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        aliases]))

(comment
  (p.eql/process
    env
    {:fats/g 10}
    [:fats/calories
     :calories]))
  ;=> {:fats/calories 90, :calories 90}

;; 4. Since each nutrient has a :calorie key, so they would conflict if they're on the same level
(pco/defresolver ingredient-calorie-sum [{:ingredient/keys [protein carbohydrates fats]}]
  {::pco/input [{:ingredient/protein [:calories]}
                {:ingredient/carbohydrates [:calories]}
                {:ingredient/fats [:calories]}]
   ::pco/output [:ingredient/calories]}
  {:ingredient/calories (reduce (fn [accum {:keys [calories]}]
                                  (+ accum calories))
                          0
                          [protein carbohydrates fats])})

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        aliases]))

(comment
  (p.eql/process
    env
    {:ingredient/protein {:protein/g 10}
     :ingredient/carbohydrates {:carbohydrates/g 5}
     :ingredient/fats {:fats/g 4}}
    [:ingredient/calories]))
  ;=> {:ingredient/calories 96}


;; 5. From here we can calculate the calories of multiple ingredients
(comment
  (p.eql/process
    env
    {:fish-and-chips {:ingredients [{:ingredient/protein {:protein/g 3.5}
                                     :ingredient/name :fish-battered
                                     :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                     :ingredient/fats {:fats/g 7}}
                                    {:ingredient/protein {:protein/g 3.5}
                                     :ingredient/name :fish-battered
                                     :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                     :ingredient/fats {:fats/g 7}}
                                    {:ingredient/protein {:protein/g 4}
                                     :ingredient/name :chips-medium
                                     :ingredient/carbohydrates {:carbohydrates/g 28}
                                     :ingredient/fats {:fats/g 14}}]}}
    [{:fish-and-chips [{:ingredients [:ingredient/calories]}]}]))
  ;=> {:fish-and-chips {:ingredients [{:ingredient/calories 107.0}
  ;                                   {:ingredient/calories 107.0}
  ;                                   {:ingredient/calories 254}]}]}]))


(pco/defresolver ingredients-calorie-sum [{:dish/keys [ingredients]}]
  {::pco/input [{:dish/ingredients [:ingredient/calories]}]
   ::pco/output [:dish/calories]}
  {:dish/calories (reduce (fn [accum {:ingredient/keys [calories]}]
                            (+ accum calories))
                    0
                    ingredients)})

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        ingredients-calorie-sum
                        aliases]))

(comment
  (p.eql/process
    env
    {:menu/dishes [{:dish/ingredients [{:ingredient/protein {:protein/g 3.5}
                                        :ingredient/name :fish-battered
                                        :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                        :ingredient/fats {:fats/g 7}}
                                       {:ingredient/protein {:protein/g 3.5}
                                        :ingredient/name :fish-battered
                                        :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                        :ingredient/fats {:fats/g 7}}
                                       {:ingredient/protein {:protein/g 4}
                                        :ingredient/name :chips-medium
                                        :ingredient/carbohydrates {:carbohydrates/g 28}
                                        :ingredient/fats {:fats/g 14}}]
                    :dish/name :fish-and-chips}
                   {:dish/ingredients [{:ingredient/protein {:protein/g 13.7}
                                        :ingredient/name :pizza-base
                                        :ingredient/carbohydrates {:carbohydrates/g 83.9}
                                        :ingredient/fats {:fats/g 6.5}}
                                       {:ingredient/protein {:protein/g 2}
                                        :ingredient/name :tomato-sauce
                                        :ingredient/carbohydrates {:carbohydrates/g 13}
                                        :ingredient/fats {:fats/g 0}}
                                       {:ingredient/protein {:protein/g 25.96}
                                        :ingredient/name :cheese
                                        :ingredient/carbohydrates {:carbohydrates/g 3.83}
                                        :ingredient/fats {:fats/g 20.03}}
                                       {:ingredient/protein {:protein/g 12.08}
                                        :ingredient/name :pepperoni
                                        :ingredient/carbohydrates {:carbohydrates/g 1.21}
                                        :ingredient/fats {:fats/g 12.08}}]
                            :dish/name :pepperoni-pizza}]}
    [{:menu/dishes [:dish/calories]}]))
  ;=> {:menu/dishes [{:dish/calories 468.0}
  ;                  {:dish/calories 970.21}]}]))

;; now we can get the minimum calories between dishes
(pco/defresolver menu-min-calories [{:menu/keys [dishes]}]
  {::pco/input [{:menu/dishes [:dish/calories]}]
   ::pco/output [:menu/dishes-min-calories]}
  {:menu/dishes-min-calories (apply min (map :dish/calories dishes))})

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        ingredients-calorie-sum
                        menu-min-calories
                        aliases]))

(p.eql/process
  env
  {:menu/dishes [{:dish/ingredients [{:ingredient/protein {:protein/g 3.5}
                                      :ingredient/name :fish-battered
                                      :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                      :ingredient/fats {:fats/g 7}}
                                     {:ingredient/protein {:protein/g 3.5}
                                      :ingredient/name :fish-battered
                                      :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                      :ingredient/fats {:fats/g 7}}
                                     {:ingredient/protein {:protein/g 4}
                                      :ingredient/name :chips-medium
                                      :ingredient/carbohydrates {:carbohydrates/g 28}
                                      :ingredient/fats {:fats/g 14}}]
                  :dish/name :fish-and-chips}
                 {:dish/ingredients [{:ingredient/protein {:protein/g 13.7}
                                      :ingredient/name :pizza-base
                                      :ingredient/carbohydrates {:carbohydrates/g 83.9}
                                      :ingredient/fats {:fats/g 6.5}}
                                     {:ingredient/protein {:protein/g 2}
                                      :ingredient/name :tomato-sauce
                                      :ingredient/carbohydrates {:carbohydrates/g 13}
                                      :ingredient/fats {:fats/g 0}}
                                     {:ingredient/protein {:protein/g 25.96}
                                      :ingredient/name :cheese
                                      :ingredient/carbohydrates {:carbohydrates/g 3.83}
                                      :ingredient/fats {:fats/g 20.03}}
                                     {:ingredient/protein {:protein/g 12.08}
                                      :ingredient/name :pepperoni
                                      :ingredient/carbohydrates {:carbohydrates/g 1.21}
                                      :ingredient/fats {:fats/g 12.08}}]
                  :dish/name :pepperoni-pizza}]}
  [:menu/dishes-min-calories])

;; 6. Now we have an issue where each dish is isolated and does not have the knowledge of the menu and the min calories for the menu
(def menu-db
  {1 {:menu/id 1
      :menu/dishes [{:dish/ingredients [{:ingredient/protein {:protein/g 3.5}
                                         :ingredient/name :fish-battered
                                         :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                         :ingredient/fats {:fats/g 7}}
                                        {:ingredient/protein {:protein/g 3.5}
                                         :ingredient/name :fish-battered
                                         :ingredient/carbohydrates {:carbohydrates/g 7.5}
                                         :ingredient/fats {:fats/g 7}}
                                        {:ingredient/protein {:protein/g 4}
                                         :ingredient/name :chips-medium
                                         :ingredient/carbohydrates {:carbohydrates/g 28}
                                         :ingredient/fats {:fats/g 14}}]
                     :dish/name :fish-and-chips
                     :dish/menu {:menu/id 1}}
                    {:dish/ingredients [{:ingredient/protein {:protein/g 13.7}
                                         :ingredient/name :pizza-base
                                         :ingredient/carbohydrates {:carbohydrates/g 83.9}
                                         :ingredient/fats {:fats/g 6.5}}
                                        {:ingredient/protein {:protein/g 2}
                                         :ingredient/name :tomato-sauce
                                         :ingredient/carbohydrates {:carbohydrates/g 13}
                                         :ingredient/fats {:fats/g 0}}
                                        {:ingredient/protein {:protein/g 25.96}
                                         :ingredient/name :cheese
                                         :ingredient/carbohydrates {:carbohydrates/g 3.83}
                                         :ingredient/fats {:fats/g 20.03}}
                                        {:ingredient/protein {:protein/g 12.08}
                                         :ingredient/name :pepperoni
                                         :ingredient/carbohydrates {:carbohydrates/g 1.21}
                                         :ingredient/fats {:fats/g 12.08}}]
                     :dish/menu {:menu/id 1}
                     :dish/name :pepperoni-pizza}]}})

(pco/defresolver menu [{:menu/keys [id]}]
  {::pco/input [:menu/id]
   ::pco/output [:menu/id
                 {:menu/dishes [:dish/calories]}]}
  (get menu-db id))

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        ingredients-calorie-sum
                        menu-min-calories
                        menu
                        aliases]))

(comment
  (p.eql/process
    env
    {:menu/id 1}
    [:menu/dishes-min-calories]))
  ;=> {:menu/dishes-min-calories 468.0}

(pco/defresolver dish-min-calories [{{:menu/keys [dishes-min-calories]} :dish/menu}]
  {::pco/input [{:dish/menu [:menu/dishes-min-calories]}]
   ::pco/output [:dish/min-calories]}
  {:dish/min-calories dishes-min-calories})

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        ingredients-calorie-sum
                        menu-min-calories
                        menu
                        dish-min-calories
                        aliases]))

(comment
  (p.eql/process
    env
    {:dish/menu {:menu/dishes-min-calories 100}}
    [:dish/min-calories]))
  ;=> {:dish/min-calories 100}

; Get the min calories across all dishes in the menu
(p.eql/process
  env
  {:menu/id 1}
  [{:menu/dishes [:dish/min-calories]}])

;; 7. Get the score of the best dish in terms of calories, lowest being 100, and the others as a ratio of lowest-calorie/ingredients-calorie
(pco/defresolver dish-calorie-score-pc [{:dish/keys [calories min-calories]}]
  {::pco/input [:dish/calories
                :dish/min-calories]
   ::pco/output [:dish/calories-score-pc]}
  {:dish/calories-score-pc (* 100
                              (/ min-calories calories))})

(def env (pci/register [protein-g->calories
                        carbohydrates-g->calories
                        fats-g->calories
                        ingredient-calorie-sum
                        ingredients-calorie-sum
                        menu-min-calories
                        menu
                        dish-min-calories
                        dish-calorie-score-pc
                        aliases]))

(comment
  (p.eql/process
    env
    {:menu/id 1}
    [{:menu/dishes [:dish/calories-score-pc]}]))                    
  ;=> {:menu/dishes [{:dish/calories-score-pc 100.0}
  ;                  {:dish/calories-score-pc 48.23697962296822}]}]))
