package com.example.foo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FooTest {
  @Test
  void foo_should_return_true() {
    assertTrue(new Foo().getFooTrue());
  }

  @Test
  void bar_should_return_true() {
    assertTrue(new Foo().getBarTrue());
  }
}
