# JPMS Migration Playground

This repository was created for playing around with migrating non-modular projects to modular ones.</br>
To be more precise,</br>
I'm trying to mimic a situation where I have a project depending on a non-modular local jar,</br>
which in itself, depends on a non-existing jar, which is of no use to my project.

It might sound like a very specific end-case,</br>
But... I bumped into this situation at work,</br>
so I figured I'll give it a try.

- [Walkthrough](#walkthrough)
- [Converting a non-modular dependency to a modular one](#converting-a-non-modular-dependency-to-a-modular-one)
  - [Create a module-info descriptor](#create-a-module-info-descriptor)
  - [Create a modular jar from the non-modular one](#create-a-modular-jar-from-the-non-modular-one)
  - [Instructing the project descriptors to use the modular jar](#instructing-the-project-descriptors-to-use-the-modular-jar)
  - [Add the new module to the modulepath](#add-the-new-module-to-the-modulepath)
    - [Retrieve the classpath](#retrieve-the-classpath)
    - [Create a fixed modulepath](#create-a-fixed-modulepath)
    - [Configure the compiler with the new modulepath](#configure-the-compiler-with-the-new-modulepath)
    - [Configure junit-platform plugin to see the new module](#configure-junit-platform-plugin-to-see-the-new-module)
- [Current status](#current-status)

## Walkthrough

[bar](/bar) is the missing artifact, used by [foo](/foo).</br>
It is configured to be deployed to [foo's lib folder](/foo/lib) as a standard non-modular jar.

[foo](/foo) is the artifact needed by my project, [baz](https://github.com/TomerFi/jpms-migration-playground).</br>
It is configured to be deployed to [my project, baz's lib folder](/lib) as a standard non-modular jar.</br>
I've used the [flatten plugin](https://www.mojohaus.org/flatten-maven-plugin/) to strip [foo's pom](/foo/pom.xml)
from its dependencies,</br>
so that [bar](/bar) will not be known at compile time to whomever uses [foo](/foo).

Basically, [baz](https://github.com/TomerFi/jpms-migration-playground) depends on [foo](/foo), without depending on [bar](/bar) and the code compiles succesfully.</br>
It will, however, throw a `NoClassDefFoundError` exception if we were to try an access [bar](/bar)'s classes.</br>
This is demonstrated in [BazTest.java](/src/test/java/com/example/baz/BazTest.java).

The easiest solution is of course working my project as a non-modular one. Demonstrated in [this commit](https://github.com/TomerFi/jpms-migration-playground/tree/07ed726f752545f5c72a0d853ebe1feb47e18320).

Another solution is to work my project as a modular one, making sure `foo` is on the module-path,</br>
implicitly making it an `automatic-module` by requiring it by its unstable name.</br>
Demonstrated in [this commit](https://github.com/TomerFi/jpms-migration-playground/tree/7755eec38d055dd98a003747d1dbc5d37a4f799d).</br>

Note that when compiling, the compiler informs us of the usage of the `automatic module`:

```shell
[INFO] Required filename-based automodules detected: [foo-0.0.1.jar]. Please don't publish this project to a public artifact repository!
```

The extreme solution, which is the one I'm playing around with in this repository.</br>
Is to _fix_ `foo`'s jar, making it a modular one, this is easily accomplished using the [moditect plugin](https://github.com/moditect/moditect).</br>
But it can be tricky since we don't have, nor do we need, `bar`.

For testing I'm using the [junit-platform plugin](https://github.com/sormuras/junit-platform-maven-plugin).</br>
This repository is actually created as part of an [issue](https://github.com/sormuras/junit-platform-maven-plugin/issues/54) I'm trying to work out.

## Converting a non-modular dependency to a modular one

For clarification:

- Our project in the works is [baz](https://github.com/TomerFi/jpms-migration-playground).
- The non-modular jar [foo](/foo), is given to us (without the source files).
- The non-modular jar [bar](/bar), is not given to us, nor do we need it.

Our goal is to convert the non-modular jar for `foo`, to a modular one.

### Create a module-info descriptor

First things first, let's create a `module-info` descriptor for `foo`.</br>
This can be accomplished in two ways.

From the command line (note the `--ignore-missing-deps` as we don't have `bar`):

```shell
jdeps --generate-module-info .\target\descriptors --ignore-missing-deps .\lib\com\example\foo\0.0.1\foo-0.0.1.jar
```

This will create `.\target\descriptors\foo\module-info.java`:

```java
module foo {
    exports com.example.foo;
}
```

The same can also be accomplished in build time, using the `moditect` plugin.</br>
Even better, we can now give the module a stable name.</br>
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

### Create a modular jar from the non-modular one

Now that we have our `module-info` descriptor ready,</br>
We can create a new modular jar with it.

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
The modified version will of course include the `module-info` descriptor,</br>
and will qualify as a `named module`.

### Instructing the project descriptors to use the modular jar

Simply update the `requires` statement in the source files.</br>
From `foo`, the automatic module unstable name.</br>
To `com.example.foo`, the named module stable name.

```java
// original
module com.example.baz {
  requires foo;
}
// modified
module com.example.baz {
  requires com.example.foo;
}
```

A modification is also required in the test sources descriptor:

```java
// original
open module com.example.baz {
  requires foo;
}
// modified
open module com.example.baz {
  requires com.example.foo;

  requires org.junit.jupiter.api;
}
```

Note that for the test descriptor, we need to add a `requires` for reading `org.junit.jupiter.api`.

The reason we didn't need to do so before, is because `foo` was an `automatic module`,</br>
It can read all other modules from the modulepath including the `unnamed module`.

Meaning it bridged between the `named modules` `com.example.baz` and `org.junit.jupiter.api`,</br>
so we didn't need to explicitly read it.</br>
Now that `com.example.foo` is a legit `named module` we need to explicitly make `com.example.baz` read `org.junit.jupiter.api`.</br>
Failing to do so will result in a compilation error:

```shell
[ERROR] .../src/test/java/com/example/baz/BazTest.java:[3,32] package org.junit.jupiter.api is not visible
```

### Add the new module to the modulepath

Now, this is were it becomes tricky.</br>
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

This, unfortunately, will not work in this case.</br>
The original modulepath was constructed from the classpath,</br>
Every non-modular jar is treated as part of the `unnamed module`.</br>
This means, our jar will exist twice on the modulepath, once inside the `unnamed module`,</br>
and once as a `named module` named `com.example.foo`.</br>

When trying to compile the test classes that access the module,</br>
we'll get the obvious error:

```shell
[ERROR] .../src/test/java/module-info.java:[1,6] module com.example.baz reads package com.example.foo from both com.example.foo and foo
```

This is where things get complicated!</br>

To avoid the above error, one cannot just `upgrade-module-path`.</br>
One needs to reconstruct the modulepath from scratch.

First, we need to get the classpath content.

#### Retrieve the classpath

We can use the [dependency plugin](https://maven.apache.org/plugins/maven-dependency-plugin/)
to create a temporary file represnting the classpath:

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

We now have the `target\fixedClasspath.txt` file with the content of the classpath excluding the non-modular `foo` artifact.</br>

#### Create a fixed modulepath

We can use the [gmavenplus plugin](https://groovy.github.io/GMavenPlus/) to execute a small groovy script:

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
                    def file = new File("$project.build.directory/fixedClasspath.txt")
                    project.properties.setProperty 'modulePath', file.text + ';' + "$project.build.directory/modules"
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

The [junit-platform plugin](https://github.com/sormuras/junit-platform-maven-plugin)
requires some tweaking so it can see the new module:

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
        </tweaks>
    </configuration>
</plugin>
```

## Current status

This is where I got stuck!</br>
For my work project, I got a different error:

```shell
java.lang.module.FindException: Error reading module X
Caused by: java.lang.module.InvalidModuleDescriptorException: Package Z not found in module
```

I might have misconfigured my work project.

For this repository, I'm getting a diffrent error:

```shell
[ERROR] java.lang.module.ResolutionException: Modules com.example.foo and foo export package com.example.foo to module org.junit.platform.commons
```

If I understand this correctly,</br>
I need to somehow remove the `foo` jar from the classpath of the plugin,</br>
so it won't find its way to the modulepath's `unnamed module`.</br>

Similar to what I did for the compiler plugin.
