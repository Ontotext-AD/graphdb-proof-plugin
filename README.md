# graphdb-proof-plugin

GraphDB Proof Plugin. Can be used to find which rules fired to derive particular statement

## Building the plugin

The plugin is a Maven project.

Run `mvn clean package` to build the plugin and execute the tests.

The built plugin can be found in the `target` directory:

- `proof-plugin-graphdb-plugin.zip`

## Installing the plugin

External plugins are installed under `lib/plugins` in the GraphDB distribution
directory. To install the plugin follow these steps:

1. Remove the directory containing another version of the plugin from `lib/plugins` (e.g. `proof-plugin`).
1. Unzip the built zip file in `lib/plugins`.
1. Restart GraphDB. 

## Motivation

Often there is a need to find out how a particular statement has been derived by the inferencer, e.g. which rule fired and which premises have been matched to produce that statement.

## Predicates Supported

Namespace of the plugin is <http://www.ontotext.com/proof/>, its internal name "proof"

It supports following predicates:
- **proof:explain**  - the subject will be bound to the state variable (a unique bnode in request scope) and the object is a list with 3 arguments, the subject, predicate and object of the statement to be explained.
When the subject is bound with the id of the state var, the other predicates can be used to fetch a part of the current solution (rulename, subject, predicate, object and context of the matching premise).
Upon re-evaluation, values from the next premise of the rule are used or we advance to the next solution to enumerate its premises for each of the rules that derive the statement.
For brevity of the results, a solution is checked whether it contains a premise that is equal to the source statement we explore and if so, that solution is skipped. That removes matches for self-supporting statements ( e.g when the same statement is also a premise of a rule that derives it).
- **proof:rule** - if the subject is bound to the state variable, then the current solution is accessed through the context and the object is bound to the rule name of the current solution as a Literal. If the source statement is explicit, the Literal "explicit" is bound to the object.
- **proof:subject** -  the subject is the state variable and the object is bound to the subject of the premise
- **proof:predicate** -  the subject is the state variable and the object is bound to the predicate of the premise
- **proof:object** -  the subject is the state variable and the object is bound to the object of the premise
- **proof:context** -  the subject is the state variable and the object is bound to the context of the premise (or onto:explicit/onto:implicit)

## Examples

### Example with **owl:inverseOf**

The example will investigate the relevant rule from that ruleset which looks like that in the source PIE file:

```
Id: owl_invOf

    a b c
    b <owl:inverseOf> d
  ------------------------------------
    c d a
```
Let's load following data into a repository that is configured with a ruleset supporting owl:inverseOf predicate (owl-horst for instance). It declare ```urn:childOf``` is inverse property of ```urn:hasChild``` and place a statement relating ```urn:John urn:childOf urn:Mary``` in a context named ```<urn:family>```:

```
insert data {
    <urn:childOf> owl:inverseOf <urn:hasChild> .
    graph <urn:family> {
        <urn:John> <urn:childOf> <urn:Mary>
    }
}
```

The next query explines which rule has been triggered to derive ```(<urn:Mary> <urn:hasChild> <urn:John>)``` statement.
The arguments to proof:explain predicate from the plugin are supplied by VALUES expression for brevity:

```
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
prefix proof: <http://www.ontotext.com/proof/>
select ?rule ?s ?p ?o ?context where {
    values (?subject ?predicate ?object) {(<urn:Mary> <urn:hasChild> <urn:John>)}
    ?ctx proof:explain (?subject ?predicate ?object) .
    ?ctx proof:rule ?rule .
    ?ctx proof:subject ?s .
    ?ctx proof:predicate ?p .
    ?ctx proof:object ?o .
    ?ctx proof:context ?context .
}
```

we are getting:


| |rule|s  |p  |o  |context|
|---|--- |---|---|---|---    |
|1|rule_owl_invOf|urn:childOf|owl:inverseOf|urn:hasChild|http://www.ontotext.com/explicit|
|2|rule_owl_invOf|urn:John|urn:childOf|urn:Mary|urn:family|

if we change the VALUES to:
```
values (?subject ?predicate ?object) {
  (<urn:John> <urn:childOf> <urn:Mary>)
}
```
we are getting:


| |rule|s  |p  |o  |context|
|---|--- |---|---|---|---    |
|1|explicit|urn:John|urn:childOf|urn:Mary|urn:family|


next if we change the values to:
```
values (?subject ?predicate ?object) {
  (<urn:hasChild> owl:inverseOf <urn:childOf> )
}
```
The solution is:


| |rule|s  |p  |o  |context|
|---|--- |---|---|---|---    |
|1|rule_owl_invOf|owl:inverseOf|owl:inverseOf|owl:inverseOf|http://www.ontotext.com/implicit|
|2|rule_owl_invOf|urn:childOf|owl:inverseOf|urn:hasChild|http://www.ontotext.com/explicit|

As we can see ```(owl:inverseOf, owl:inverseOf owl:inverseOf)``` is implicit and we may investigate further by altering the VALUES to:
```
values (?subject ?predicate ?object) {
  (owl:inverseOf owl:inverseOf owl:inverseOf )
}
```


and we are getting:

| |rule|s  |p  |o  |context|
|---|--- |---|---|---|---    |
|1|rule_owl_invOfBySymProp|owl:inverseOf|rdf:type|owl:SymmetricProperty|http://www.ontotext.com/implicit|

the PIE code for the related rule is:
```
Id: owl_invOfBySymProp

      a <rdf:type> <owl:SymmetricProperty>
    ------------------------------------
      a <owl:inverseOf> a
```

if we track down the last premise, we can find that another rule support it
 (both rules and the premises are axioms and the plugin, at present, do not check if something is an axiom)

```
Id: owl_SymPropByInverse

      a <owl:inverseOf> a
    ------------------------------------
      a <rdf:type> <owl:SymmetricProperty>
```

## An extended example with ontology data

Another sample dataset may be used to further explore the internals of the inference engine.

lets add following data into the same repository:
```
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
insert data {
    <urn:Red> a <urn:Colour> .
    <urn:White> a <urn:Colour> .
    <has:color> a rdf:Property .
    <urn:WhiteThing> a owl:Restriction;
                     owl:onProperty <has:color>;
                     owl:hasValue <urn:White> .
    <urn:RedThing> a owl:Restriction;
                     owl:onProperty <has:color>;
                     owl:hasValue <urn:Red> .
    <has:component> a rdf:Property .
    <urn:Wine> a owl:Restriction;
                     owl:onProperty <has:component>;
                     owl:someValuesFrom <urn:Grape> .
    <urn:RedWine> owl:intersectionOf (<urn:RedThing> <urn:Wine>) .
    <urn:WhiteWine> owl:intersectionOf (<urn:WhiteThing> <urn:Wine>) .
    <urn:Beer> a owl:Restriction;
                     owl:onProperty <has:component>;
                     owl:someValuesFrom <urn:Malt> .
    <urn:PilsenerMalt> a <urn:Malt> .
    <urn:PaleMalt> a <urn:Malt> .
    <urn:WheatMalt> a <urn:Malt> .
    
    <urn:MerloGrape> a <urn:Grape> .
    <urn:CaberneGrape> a <urn:Grape> .
    <urn:MavrudGrape> a <urn:Grape> .
    
    <urn:Merlo> <has:component> <urn:MerloGrape> ;
                <has:color> <urn:Red> .
}
```
It is a simple beverage ontology that uses ```owl:hasValue```, ```owl:someValuesFrom``` and ```owl:intersectionOf``` to classify instances based on the values of some of the ontology properties.
We have:
- two colors ```Red``` and ```White```
- classes of ```WhiteThings``` and ```RedThigs``` for the items related with ```has:color``` property to ```White``` and ```Red``` colours
- classes ```Wine``` and ```Beer``` for the items related with ```has:component``` to instances of ```Grape``` and ```Malt``` classes
- few instances of ```Grape``` (```MerloGrape, CabernetGrape``` etc) and ```Malt``` (```PilsenerMalt, WheatMalt``` etc)
- classes ```RedWine``` and ```WhiteWine``` are declared as intersections of ```Wine``` with ```RedThings``` and ```WhiteThigs``` respectively for ```WhiteWine```
- and finally we introduce an instance ```Merlo``` which is related with ```has:component``` to ```MerloGrape``` and also has value to ```has:color``` as ```Red```

The expected inference is that ```Merlo``` is classified as ```RedWine``` because it is member of both ```RedThings``` (because of ```has:color``` is related to ```Red``` )
and ```Wine``` (because ```has:component``` points to an object that is member of ```Grape``` class).

If we evaluate:
```
describe <urn:Merlo>
```

we get a Turtle document like:
```
<urn:Merlo> a rdfs:Resource, <urn:RedThing>, <urn:RedWine>,<urn:Wine>;
        <has:color> <urn:Red>;
        <has:component> <urn:MerloGrape> .
```

So the inferencer derived correctly that ```Merlo``` is a member of ```RedWine```.

Let’s explore how the inferencer derive that:

First, we will add some hepler javascript functions to combine the results in more compact form as literals formed by the local names of the IRI components in the statements:

Let introduce a ```js:lname()``` function which retunr the local name of an IRI:
```
PREFIX jsfn:<http://www.ontotext.com/js#>
INSERT DATA {
    [] jsfn:register '''
    function lname(value) {
        if(value instanceof org.eclipse.rdf4j.model.IRI)
            return value.getLocalName();
        else
            return ""+value;
    }
'''
}
```

and reuse it to create ```js:stmt()```  that concatenates few more items into a convenient literal:
```
PREFIX jsfn:<http://www.ontotext.com/js#>
INSERT DATA {
    [] jsfn:register '''
    function stmt(s, p, o, c) {
        return '('+lname(s)+', '+lname(p)+', '+lname(o)+(c?', '+lname(c):'')+')';
    }
'''
}
```
We will also need some way to refer to a BNode using its label, because SPARQL always generate unique BNodes during evaluation of a query:
```
PREFIX jsfn:<http://www.ontotext.com/js#>
INSERT DATA {
    [] jsfn:register '''
    function _bnode(value) {
        return org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance().createBNode(value);
    }
'''
}
```
Now lets see how the ```(urn:Merlo rdf:type urn:RedWine)``` has been derived (note the use of ```js:stmt()``` function in the projection of the query):
The query will use a subselect to provide bindings for ```?subject, ?predicate and ?object``` variables as a convenient way to easily add more statements to be explained by the plugin further.

```
PREFIX jsfn:<http://www.ontotext.com/js#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
prefix proof: <http://www.ontotext.com/proof/>
select(jsfn:stmt(?subject,?predicate,?object) as ?stmt) ?rule (jsfn:stmt(?s,?p,?o,?context) as ?premise)
where {
    {
        select ?subject ?predicate ?object {
            values (?subject ?predicate ?object) {
                    (<urn:Merlo> rdf:type <urn:RedWine>)
            }
        }
    }
    ?ctx proof:explain (?subject ?predicate ?object) .
    ?ctx proof:rule ?rule .
    ?ctx proof:subject ?s .
    ?ctx proof:predicate ?p .
    ?ctx proof:object ?o .
    ?ctx proof:context ?context .
}
```
The result is:


| |stmt|rule|premise|
|---|---|---|---|
|1|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(RedWine, intersectionOf, \_:node1, explicit)|
|2|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|**(Merlo, \_allTypes, \_:node1, implicit)**|


The first premise is explicit and came from the definition of ```RedWine``` class is an ```owl:intersection``` of some rdf list (\_:node1) that hold the classes that form the intersection.
The second premise relates ```Merlo``` with some 'weird' predicate to the node from the intersection node. The inference is derived after applying the following rule:
```
    Id: owl_typeByIntersect_1

      a <onto:_allTypes> b
      c <owl:intersectionOf> b
    ------------------------------------
      a <rdf:type> c
```
where 'a' is bound to ```Merlo``` and 'c' to ```RedWine```

Let’s add ```(Merlo, \_allTypes, \_:node1)``` statement to the list of statements in the subselect that we used in the query. We change the subselect to use a UNION, where for the second part, the ?object is bound to the right bnode which we created by using the helper ```js:_bnode()``` function and providing the id as a literal :
```
select ?subject ?predicate ?object {
    {
        values (?subject ?predicate ?object) {
            (<urn:Merlo> rdf:type <urn:RedWine>)
        }
    } union {
        bind (jsfn:_bnode('node1') as ?object)
        values (?subject ?predicate) {
            (<urn:Merlo> <http://www.ontotext.com/_allTypes>)}
    }
}
```

let’s evaluate it, the result is:

| |stmt|rule|premise|
|---|---|---|---|
|1|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(RedWine, intersectionOf, \_:node1, explicit)|
|2|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1| Merlo, \_allTypes, \_:node1, implicit)|
|3|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, first, RedThing, explicit)|
|4|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, rest, \_:node2, explicit)|
|5|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|**(Merlo, \_allTypes, \_:node2, implicit)**|
|6|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|**(Merlo, type, RedThing, implicit)**|


we see that ```(Merlo, _allTypes, _:node1)``` is derived by rule ```owl_typeByIntersect_3```:
```
Id: owl_typeByIntersect_3

      a <rdf:first> b
      d <rdf:type> b
      a <rdf:rest> c
      d <onto:_allTypes> c
    ------------------------------------
      d <onto:_allTypes> a
```
where we have 2 explicit and two inferred statements matching the premises ```(Merlo, _allTypes, _:node2)``` and ```(Merlo, type, RedThing)```

let’s add to the list ```(Merlo, type, RedThing)``` first, the subselect is changed to:
```
select ?subject ?predicate ?object {
            {
                values (?subject ?predicate ?object) {
                    (<urn:Merlo> rdf:type <urn:RedWine>)
                    (<urn:Merlo> rdf:type <urn:RedThing>)
                }
            } union {
                bind (jsfn:_bnode('node1') as ?object)
                values (?subject ?predicate) {
                    (<urn:Merlo> <http://www.ontotext.com/_allTypes>)}
            }
    }
```
the result is:




| |stmt|rule|premise|
|---|---|---|---|
|1|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(RedWine, intersectionOf, \_:node1, explicit)|
|2|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(Merlo, \_allTypes, \_:node1, implicit)|
|3|(Merlo, type, RedThing)|rule_owl_typeByHasVal|**(RedThing, onProperty, color, explicit)**|
|4|(Merlo, type, RedThing)|rule_owl_typeByHasVal|**(Merlo, color, Red, explicit)**|
|5|(Merlo, type, RedThing)|rule_owl_typeByHasVal|**(RedThing, hasValue, Red, explicit)**|
|6|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, first, RedThing, explicit)|
|7|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, rest, \_:node2, explicit)|
|8|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, \_allTypes, \_:node2, implicit)|
|9|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, type, RedThing, implicit)|

we see that the ```(Merlo, type, RedThing)```  is derived by matching rule ```owl_typeByHasVal``` with all explicit premises:
```
Id: owl_typeByHasVal

      a <owl:onProperty> b
      a <owl:hasValue> c
      d b c
    ------------------------------------
      d <rdf:type> a
```
where 'a' is bound to ```RedThing``` and 'd' to ```Merlo``` (along the rest)

Let’s add the other implicit statement that matched the ```owl_typeByInterset_3``` rule ```(Merlo, _allTypes, _:node2)```

For that we add another argument to the UNION in the subselect by introducing the \_:node2 using the same ```js_bnode()``` function.
The subselect looks like that:
```
        select ?subject ?predicate ?object {
            {
                values (?subject ?predicate ?object) {
                    (<urn:Merlo> rdf:type <urn:RedWine>)
                    (<urn:Merlo> rdf:type <urn:RedThing>)
                }
            } union {
                bind (jsfn:_bnode('node1') as ?object)
                values (?subject ?predicate) {
                    (<urn:Merlo> <http://www.ontotext.com/_allTypes>) }
            } union {
                bind (jsfn:_bnode('node2') as ?object)
                values (?subject ?predicate) {
                    (<urn:Merlo> <http://www.ontotext.com/_allTypes>) }
            }
    }
```
if we evaluate it:

| |stmt|rule|premise|
|---|---|---|---|
|1|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(RedWine, intersectionOf, \_:node1, explicit)|
|2|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1| Merlo, \_allTypes, \_:node1, implicit)|
|3|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(RedThing, onProperty, color, explicit)|
|4|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(Merlo, color, Red, explicit)|
|5|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(RedThing, hasValue, Red, explicit)|
|6|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, first, RedThing, explicit)|
|7|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, rest, \_:node2, explicit)|
|8|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, \_allTypes, \_:node2, implicit)|
|9|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, type, RedThing, implicit)|
|10|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|(\_:node2, first, Wine, explicit)|
|11|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|(\_:node2, rest, nil, explicit)|
|12|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|**(Merlo, type, Wine, implicit)**|


The statement ```(Merlo, _allTypes, _:node2)``` was derived by ```owl_typeByIntersect_2``` and the only implicit statement matching as premise is ```(Merlo, type, Wine)```

The owl_typeByIntersect_2 rule looks like:
```
    Id: owl_typeByIntersect_2

      a <rdf:first> b
      a <rdf:rest> <rdf:nil>
      c <rdf:type> b
    ------------------------------------
      c <onto:_allTypes> a
```
where 'c' is bound to ```Merlo``` and 'b' to ```Wine```

Let’s add the ```(Merlo, type, Wine)``` to the subselect we used to explore as another UNION using VALUES
```
        select ?subject ?predicate ?object {
            {
                values (?subject ?predicate ?object) {
                    (<urn:Merlo> rdf:type <urn:RedWine>)
                    (<urn:Merlo> rdf:type <urn:RedThing>)
                }
            } union {
                bind (jsfn:_bnode('node1') as ?object)
                values (?subject ?predicate) {
                    (<urn:Merlo> <http://www.ontotext.com/_allTypes>)}
            } union {
        bind (jsfn:_bnode('node2') as ?object)
                values (?subject ?predicate) {
                    (<urn:Merlo> <http://www.ontotext.com/_allTypes>)}
            } union {
                values (?subject ?predicate ?object) {
                    (<urn:Merlo> rdf:type <urn:Wine>)
                }
            }
    }
```
and evaluate the query again, the results were enriched with solutions 13-16:


| |stmt|rule|premise|
|---|---|---|---|
|1|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1|(RedWine, intersectionOf, \_:node1, explicit)|
|2|(Merlo, type, RedWine)|rule_owl_typeByIntersect_1| Merlo, \_allTypes, \_:node1, implicit)|
|3|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(RedThing, onProperty, color, explicit)|
|4|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(Merlo, color, Red, explicit)|
|5|(Merlo, type, RedThing)|rule_owl_typeByHasVal|(RedThing, hasValue, Red, explicit)|
|6|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, first, RedThing, explicit)|
|7|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(\_:node1, rest, \_:node2, explicit)|
|8|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, \_allTypes, \_:node2, implicit)|
|9|(Merlo, \_allTypes, \_:node1)|rule_owl_typeByIntersect_3|(Merlo, type, RedThing, implicit)|
|10|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|(\_:node2, first, Wine, explicit)|
|11|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|(\_:node2, rest, nil, explicit)|
|12|(Merlo, \_allTypes, \_:node2)|rule_owl_typeByIntersect_2|(Merlo, type, Wine, implicit)|
|13|(Merlo, type, Wine)|rule_owl_typeBySomeVal|(Wine, onProperty, component, explicit)|
|14|(Merlo, type, Wine)|rule_owl_typeBySomeVal|(Wine, someValuesFrom, Grape, explicit)|
|15|(Merlo, type, Wine)|rule_owl_typeBySomeVal|(Merlo, component, MerloGrape, explicit)|
|16|(Merlo, type, Wine)|rule_owl_typeBySomeVal|(MerloGrape, type, Grape, explicit)|

These come from rule owl_typeBySomeVal where all premises matching it were explicit
The rule looks like:
```
    Id: owl_typeBySomeVal

      a <rdf:type> b
      c <owl:onProperty> d
      c <owl:someValuesFrom> b
      e d a
    ------------------------------------
      e <rdf:type> c
```      
where 'e' is bound to ```Merlo```, 'd' to ```has:component```, 'a' to ```MerloGrape```, 'b' to ```Grape``` etc.
 
So the chain is rather obscure but we ended with a proof to how the inferencer derived ```(<urn:Merlo> rdf:type <urn:RedWine>)```:

- ```(Merlo, type, Wine)```  was derived by rule ```owl_typeBySomeVal``` from all explicit statements
- ```(Merlo, type, RedThing)``` was derived by ```rule owl_typeByHasVal``` from explicit statements
- ```(Merlo, _allTypes, _:node2)``` was derived by rule ```owl_typeByIntersect_2``` with combination of some explicit and the inferred ```(Merlo, type, Wine)```
- ```(Merlo, _allTypes, _:node1)``` was derived by rule ```owl_typeByIntersect_3``` with combination of explicit and inferred ```(Merlo, type, RedThing)``` and ```(Merlo, _allTypes, _:node2)```
- and finally the ```(Merlo, type, RedWine)``` was derived by ```owl_typeByIntersect_1``` from explicit ```(RedWine, intersectionOf, _:node1)``` and inferred ```(Merlo, _allTypes, _:node1)```


