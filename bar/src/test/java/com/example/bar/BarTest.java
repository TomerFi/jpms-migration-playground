package com.example.bar;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BarTest {
  @Test
  void bar_should_return_true() {
    assertTrue(new Bar().getBarTrue());
  }
}
