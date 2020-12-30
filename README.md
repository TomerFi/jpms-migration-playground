# JPMS Migration Playground

This repository was created for playing around with migrating non-modular projects to modular ones.</br>
To be more precise,</br>
I'm trying to mimic a situation where I have a project depending on a non-modular local jar,</br>
which in itself, depends on a non-existing jar, which is of no use to my project.

It may sound like a very specific end-case,</br>
But... I bumped into this situation at work,
so I figured I'll give it a try.

## Walkthrough

[bar](/bar) is the missing artifact, used by [foo](/foo).</br>
It is configured to be deployed to [foo's lib folder](/foo/lib) as a standard non-modular jar.

[foo](/foo) is the artifact needed by my project, [baz](/).</br>
It is configured to be deployed to [my project, baz's lib folder](/lib) as a standard non-modular jar.</br>
I've used the [flatten plugin](https://www.mojohaus.org/flatten-maven-plugin/) to strip [foo's pom](/foo/pom.xml)
from its dependencies,</br>
so that [bar](/bar) will not be known at compile time to whomever uses [foo](/foo).

Basically, [baz](/) depends on [foo](/foo), without depending on [bar](/bar) and the code compiles succesfully.</br>
It will, however, throw a `NoClassDefFoundError` exception if we were to try an access [bar](/bar)'s classes.</br>
This is demonstrated in [BazTest.java](/src/test/java/com/example/baz/BazTest.java).

The easiest solution is of course working my project as a non-modular one. Demonstrated in [this commit](add-commit-link).

Another solution is to work my project as a modular one, making sure `foo` is on the module-path,</br>
implicitly making it an `automatic-module` by requiring it by its unstable name.</br>
Demonstrated in [this commit](add-commit-link).

The extreme solution, which is the one I'm playing around with in this repository.</br>
Is to _fix_ `foo`'s jar, making it a modular one, this is easily accomplished using the [moditect plugin](https://github.com/moditect/moditect).</br>
But it can be tricky since we don't have, nor do we need, `bar`.

For testing I'm using the [junit-platform plugin](https://github.com/sormuras/junit-platform-maven-plugin).</br>
This repository is actually created as part of an [issue](https://github.com/sormuras/junit-platform-maven-plugin/issues/54) I'm trying to work out.
