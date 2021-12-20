# Supertoto


# Micro-todo
+ valider rmr avec initialisation & 2 layers fonctionne, en live
+ bugs quand on passe à 0.2 : investiguer
+ finaliser version-doc
+ finaliser version
- ajouter nouveau repo git mzero-game
- y mettre mzero
- cleaner mzero-game : enlever le player m0, adapter le readme et la doc
- cleaner mzero : enlever tout sauf le player m0, adapter le readme et la doc
- sauvegarde du repo: non pas besoin de l'adapter car mzero reste le truc coeur
- enjoy fast tests

# Todo code
+ faire la passe forward en clj-torch
 + test similaire à seq-forward-pass-with-b qui tourne en pytorch
 + créer le protocole pour avoir 2 impls (avec la neanderthal fonctionnelle)
 + ajouter l'impl pytorch
 + test via m00 player des 2 impls
 + doc ok?

+ ajout de raw-outputs & r-o-grads dans layers (prep backprop)
  + s -> activation threshold, moved to be used in both neanderthal & torch impl
  + next-direction moved from motoneurons to m00
  + forward pass speced to return motoneuron values
	
+ ajouter le calcul de la backprop dans neanderthal-impl
  + migration de l'initialisation des layers dans ann.clj
  + changement signature forward-pass (plus de layers qui est un détail d'impl pour neanderthal)

+ feat: ajouter la backprop à torch-impl / bcp de fixes sur ndt-impl

+ feat: manger un fruit déclenche une backprop
+ coder une initialisation basique
+ valider rmr avec initialisation & 2 layers fonctionne
+ finaliser version-doc

## Version suivante - xp-ready
- permettre entrainement sur plusieurs games, reset tous les 1000 coups
- métrique de FEM : nombre de mouvements réussis par game
  - plotter
  - métrique de vitesse également
- permettre entrainement GPU
- faire 1 ou 2 grosses XP
- garantir une exploration de jeu décente
  - soit motoception = déplacement (au lieu de volonté), soit mur donne un renf neg
	- soit les 2
	- coder une première initialisation potentiellement pertinente
- faire marcher le FEM
- màj doc, arbre, version doc

## Version N+2 - multi-step-fem-learning
- Ajouter couche temporelle
- faire marcher le FEM multi-step

## Big picture
- coder le renfo squelette NPC

# Todo planning
+ Deep dive implem multi-step fem
  - notamment inclure t-1 dans les sens? vs juste pour le renf?
	- qu'est-ce qui marche? si juste pour le renf vas-y et tu mettras dans t-1 plus tard (structure)

x (opt) Grooming rapide v0.0.4
- outliner le big plan (grosse maille, imprécis, t'embête pas trop)
- faire le plan quarterly
  - Se donner les futurs objectifs après FEM, steps suffisamment petites !
  - envisager chaque option dans le contexte de fin de FEM et d'objectif suivant

# Plan
Ça m'arrange vachement si j'ai trouvé un truc de ouf d'ici la fin de l'année. Imagine !

- June
  - v0.0.3 - rs-ready
  - v0.0.4 - xp-ready (this week)
  - v0.0.5 - multistep-fem-learning (next week)
- July : 
- Sept : 
- Q4 : 

