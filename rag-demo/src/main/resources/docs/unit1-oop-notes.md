# Unit 1: Object-Oriented Programming in Java

## Classes and objects

A class is a blueprint that declares fields (state) and methods (behaviour). An object is an instance of a class created with `new`. Every object lives on the heap; variables hold references to objects, not the objects themselves.

## Encapsulation

Encapsulation hides an object's internal state behind methods. Fields are declared `private` and accessed through getters and setters or, better, through methods that express behaviour. Encapsulation lets a class change its internal representation without breaking callers.

---

## Interface vs abstract class

An interface declares a contract: abstract methods, plus `default` and `static` methods since Java 8 and `private` methods since Java 9. Interfaces cannot hold instance state; any fields are implicitly `public static final` constants. A class can implement many interfaces.

An abstract class can hold instance fields, constructors and both abstract and concrete methods. A class can extend only one abstract class, because Java has single inheritance of classes.

Rule of thumb: use an interface to define a capability that unrelated classes can share (for example `Comparable` or `Runnable`). Use an abstract class when closely related classes share state and implementation, such as a template method.

---

## Polymorphism

Polymorphism lets one reference type point to objects of different subclasses. The method that runs is chosen at runtime from the object's actual class (dynamic dispatch). Method overloading, by contrast, is resolved at compile time from the argument types.

## Records

A record (Java 16) is a concise, immutable data carrier: `record Point(int x, int y) {}`. The compiler generates the constructor, accessors, `equals`, `hashCode` and `toString`. Records are implicitly final and their fields are final.

## Sealed classes

A sealed class or interface (Java 17) restricts which classes may extend it with a `permits` clause. Combined with records and pattern matching in `switch`, sealed hierarchies let the compiler check that every case is handled.
