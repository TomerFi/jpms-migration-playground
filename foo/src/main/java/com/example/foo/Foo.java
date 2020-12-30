package com.example.foo;

import com.example.bar.Bar;

public final class Foo {
  public boolean getFooTrue() {
    return Boolean.TRUE;
  }

  public boolean getBarTrue() {
    return new Bar().getBarTrue();
  }
}
