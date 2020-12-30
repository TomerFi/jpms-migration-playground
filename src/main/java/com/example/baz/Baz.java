package com.example.baz;

import com.example.foo.Foo;

public final class Baz {
  private Foo foo;

  public Baz() {
    foo = new Foo();
  }

  public boolean getBazTrue() {
    return Boolean.TRUE;
  }

  public boolean getFooTrue() {
    return foo.getFooTrue();
  }

  public boolean getBarTrue() {
    return foo.getBarTrue();
  }
}
