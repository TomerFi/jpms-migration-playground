---
title: JPMS Migration Playground
published: true
description: Playing around with modularizing monolithic jars.
tags: ["java", "programming", "jpms", "jigsaw"]
---

## Playing around with modularizing monolithic jars

This [repository][0] was created for playing around with modularizing monolithic jars.

To be more precise,</br>
I was trying to mimic a situation where I have a project depending on a monolithic jar from a local dependency, which in itself, depends on a non-existing dependency, which is of no use to my project.

*My goal was to modularize the monolithic jar from the local dependency disregarding the non-existing dependency.*

It might sound like a very specific end-case,</br>
But... I bumped into this situation at work,</br>
so I figured I'll give it a try. :smirk:

Join me, if nothing else, we'll get a better understanding of `JPMS`.
:nerd_face:

## Project Walkthrough

[bar][1] is the missing artifact, used by [foo][2].</br>
It is configured to be deployed to [foo's lib folder][3] as a monolithic jar.

[foo][2] is the artifact needed by my project, [baz][0].</br>
It is configured to be deployed to [my project, baz's lib folder][4] as a monolithic jar.</br>
I've used the [flatten plugin][5] to strip [foo's pom][6] from its dependencies,</br>
so that [bar][1] will not be known at compile time to whomever uses [foo][2].

Basically, [baz][0] depends on [foo][2], without depending on [bar][1] and the code compiles successfully. :grin:</br>
It will, however, throw a `NoClassDefFoundError` exception if we were to try an access [bar][1]'s classes.</br>
This is demonstrated in [BazTest.java][7]

### First Solution

The easiest solution is of course working on my project without leveraging `JPMS`. :neutral_face:

Demonstrated in [this commit][8].

### Second Solution

Another solution is to incorporate `JPMS` in my project, making sure `foo` is on the modulepath, implicitly making it an `automatic module` by explicitly requiring it by its unstable name. :dizzy_face:

Demonstrated in [this commit][9].

Note that when compiling, the compiler will inform me of the usage of an `automatic module`:

```shell
[INFO] Required filename-based automodules detected: [foo-0.0.1.jar]. Please don't publish this project to a public artifact repository!
```

This will not suffice, I won't be able to make the best of `JPMS`.</br>
Features like `jlink` don't work with `automatic modules`. :disappointed:

### Third Solution

The next solution, which is the one I'm writing about.</br>
Is to modularize `foo`'s jar, this is easily accomplished using the [moditect plugin][10].</br>
But it can be tricky since I don't have, nor do I need, `bar`, and I prefer doing most of the work in build time and not manually.

For testing I'm using the [junit-platform plugin][11].</br>

Let's dive in. :muscle:

## Modularizing a monolithic jar

For clarification:

- Our project in the works is [baz][0].
- The monolithic jar [foo][2], is given to us (without the source files).
- The monolithic jar [bar][1], is not given to us, nor do we need it.

Our goal is to modularize the monolithic jar `foo`, so `baz` can truly leverage `JPMS` features.

### Create a module-info descriptor

First things first, let's create a `module-info` descriptor for `foo`.</br>
This can be accomplished in two ways.

From the command line (note the `--ignore-missing-deps` as we don't have `bar`):

```shell
jdeps --generate-module-info .\target\descs --ignore-missing-deps .\lib\com\example\foo\0.0.1\foo-0.0.1.jar
```

This will create `.\target\descs\foo\module-info.java`:

```java
module foo {
    exports com.example.foo;
}
```

The same can also be accomplished in build time, using the `moditect` plugin.</br>
Better yet, we can now give the module a stable name.</br>
By convention, let's name it `com.example.foo`:

```xml
<plugin>
    <groupId>org.moditect</groupId>
    <artifactId>moditect-maven-plugin</artifactId>
    <version>1.0.0.RC1</version>
    <executions>
        <execution>
            <id>generate-module-info</id>
            <phase>initialize</phase>
            <goals>
                <goal>generate-module-info</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.build.directory}/descs</outputDirectory>
                <modules>
                    <module>
                        <artifact>
                            <groupId>com.example</groupId>
                            <artifactId>foo</artifactId>
                            <version>0.0.1</version>
                        </artifact>
                        <moduleInfo>
                            <name>com.example.foo</name>
                        </moduleInfo>
                    </module>
                </modules>
                <jdepsExtraArgs>--ignore-missing-deps</jdepsExtraArgs>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Create a modularized jar from the monolithic one

Now that we have our `module-info` descriptor ready,</br>
We can create a new modularized jar with it.

This is easily accomplished with the `moditect` plugin:

```xml
<plugin>
    <groupId>org.moditect</groupId>
    <artifactId>moditect-maven-plugin</artifactId>
    <version>1.0.0.RC1</version>
    <executions>
        <execution>
            <id>add-module-info</id>
            <phase>initialize</phase>
            <goals>
                <goal>add-module-info</goal>
            </goals>
            <configuration>
                <modules>
                    <module>
                        <artifact>
                            <groupId>com.example</groupId>
                            <artifactId>foo</artifactId>
                        </artifact>
                        <moduleInfoFile>${project.build.directory}/descs/com.example.foo/module-info.java</moduleInfoFile>
                    </module>
                </modules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This will create a modified version `foo-0.0.1.jar` in `target\modules`,</br>
The modified version will of course include the following `module-info` descriptor, and will qualify as a `named module`.

```java
module com.example.foo {
    exports com.example.foo;
}
```

### Instruct our project to use the modularized jar

Simply update the `requires` statement in the source files of the `baz` project.</br>
From `foo`, the `automatic module` *unstable* name.</br>
To `com.example.foo`, the `named module` *stable* name.

```java
// original using an automatic module
module com.example.baz {
  requires foo;
}
// modified using the new named module
module com.example.baz {
  requires com.example.foo;
}
```

A modification is also required in the test sources descriptor:

```java
// original using an automatic module
open module com.example.baz {
  requires foo;
}
// modified using the new named module
open module com.example.baz {
  requires com.example.foo;

  requires org.junit.jupiter.api;
}
```

Note that for the test descriptor, we needed to add a `requires` directive for reading `org.junit.jupiter.api`.

The reason we didn't need to do so before, is because as an `automatic module`, `foo` could read all the other modules from the modulepath.

Meaning, it bridged between the modules `com.example.baz` and `org.junit.jupiter.api`, so we didn't need to explicitly make `com.example.baz` read `org.junit.jupiter.api`.

Now that `com.example.foo` is a legit `named module`, `com.example.baz` needs to explicitly require `org.junit.jupiter.api`.

Failing to do so will result in a compilation error:

```shell
[ERROR] .../src/test/java/com/example/baz/BazTest.java:[3,32] package org.junit.jupiter.api is not visible
```

### Add the new module to the modulepath

Now, this is were it gets tricky.</br>
Basically one would just configure the compiler to include the new module:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>--upgrade-module-path</arg>
            <arg>${project.build.directory}/modules</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

This, unfortunately, will not work in this case. :worried:

The original modulepath is constructed from the classpath, every monolithic jar is treated as part of the `unnamed module`.</br>
This means, our jar will exist twice on the modulepath, once inside the `unnamed module`, and once as a `named module` named `com.example.foo`.

When trying to compile the test classes that access the module, we'll get the obvious error:

```shell
[ERROR] .../src/test/java/module-info.java:[1,6] module com.example.baz reads package com.example.foo from both com.example.foo and foo
```

This is where things get complicated!

To avoid the above error, one cannot just `upgrade-module-path`.</br>
One needs to reconstruct the modulepath from scratch.

First, we need to get the classpath content.

#### Retrieve the classpath

We can use the [dependency plugin][12] to create a temporary file represnting the classpath:

```xml
<plugin>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
    <execution>
        <id>create-classpath-file</id>
        <goals>
            <goal>build-classpath</goal>
        </goals>
        <configuration>
            <outputFile>${project.build.directory}/fixedClasspath.txt</outputFile>
            <excludeArtifactIds>foo</excludeArtifactIds><!-- every aritifact recreated with moditect should be listed here -->
        </configuration>
    </execution>
    </executions>
</plugin>
```

We now have the `target\fixedClasspath.txt` file with the content of the classpath excluding the monolithic `foo` artifact.</br>

#### Create a fixed modulepath

We can accomplish this by leveraging the [gmavenplus plugin][13] to execute a small groovy script.</br>
To better accommodate both *Windows* and *Non-Windows* os families, we'll use the [os plugin][14] to create the `os.detected.name`.

```xml
<extension>
    <groupId>kr.motd.maven</groupId>
    <artifactId>os-maven-plugin</artifactId>
    <version>1.6.2</version>
</extension>
```

```xml
<plugin>
    <groupId>org.codehaus.gmavenplus</groupId>
    <artifactId>gmavenplus-plugin</artifactId>
    <version>1.12.0</version>
    <dependencies>
        <dependency>
            <groupId>org.codehaus.groovy</groupId>
            <artifactId>groovy-ant</artifactId>
            <version>3.0.7</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
    <executions>
        <execution>
        <phase>process-sources</phase>
        <goals>
            <goal>execute</goal>
        </goals>
        <configuration>
            <scripts>
                <script><![CDATA[
                    def delimiter = project.properties['os.detected.name'] == 'windows' ? ';' : ':'
                    def file = new File("$project.build.directory/fixedClasspath.txt")
                    project.properties.setProperty 'modulePath', file.text + delimiter + "$project.build.directory/modules"
                ]]></script>
            </scripts>
        </configuration>
        </execution>
    </executions>
</plugin>
```

This will result in a new property named `modulePath` containing the fixed classpath concatenated with the target modules directory.

#### Configure the compiler with the new modulepath

Simply do:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>--module-path</arg>
            <arg>${modulePath}</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

#### Configure junit-platform plugin to see the new module

The [junit-platform plugin][11] requires some tweaking so it can see the new module:

```xml
<plugin>
    <groupId>de.sormuras.junit</groupId>
    <artifactId>junit-platform-maven-plugin</artifactId>
    <version>1.1.0</version>
    <extensions>true</extensions>
    <configuration>
        <executor>JAVA</executor>
        <tweaks>
            <additionalTestPathElements>
                <element>${project.build.directory}/modules/foo-0.0.1.jar</element>
            </additionalTestPathElements>
            <dependencyExcludes>
                <exclude>com.example:foo</exclude>
            </dependencyExcludes>
        </tweaks>
    </configuration>
</plugin>
```

In a similar manner to the way the compiler plugin was patched,</br>
In a much simpler way, we told `junit-platform plugin` to exclude the original monolithic `foo` and add the modularized one.

That's it.</br>
Everything now compiles and all the tests pass.</br>
:grinning:

The key added value here is that now,</br>
Both the project `baz` and the local dependency `foo` are `named modules`,</br>
And, we are still missing `bar` of course.</br>
Better yet, we can also use `moditect` to make `foo` stop exposing packages related to `bar` so we won't be able to access them.</br>
:sunglasses:

I had fun playing around with `JPMS`,</br>
I hope you did too.

You can check out the code for this playground in [Github][0].

**:wave: See you in the next blog post :wave:**

[0]: https://github.com/TomerFi/jpms-migration-playground
[1]: https://github.com/TomerFi/jpms-migration-playground/tree/master/bar
[2]: https://github.com/TomerFi/jpms-migration-playground/tree/master/foo
[3]: https://github.com/TomerFi/jpms-migration-playground/tree/master/foo/lib/com/example/bar
[4]: https://github.com/TomerFi/jpms-migration-playground/tree/master/lib/com/example/foo
[5]: https://www.mojohaus.org/flatten-maven-plugin/
[6]: https://github.com/TomerFi/jpms-migration-playground/blob/master/foo/.flattened-pom.xml
[7]: https://github.com/TomerFi/jpms-migration-playground/blob/master/src/test/java/com/example/baz/BazTest.java
[8]: https://github.com/TomerFi/jpms-migration-playground/tree/07ed726f752545f5c72a0d853ebe1feb47e18320
[9]: https://github.com/TomerFi/jpms-migration-playground/tree/7755eec38d055dd98a003747d1dbc5d37a4f799d
[10]: https://github.com/moditect/moditect
[11]: https://github.com/sormuras/junit-platform-maven-plugin
[12]: https://maven.apache.org/plugins/maven-dependency-plugin
[13]: https://groovy.github.io/GMavenPlus
[14]: https://github.com/trustin/os-maven-plugin
