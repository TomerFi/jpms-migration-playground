package com.example.baz;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class BazTest {
  private Baz sut;

  @BeforeEach
  void initialize() {
    sut = new Baz();
  }

  @Test
  void baz_should_return_true() {
    assertTrue(sut.getBazTrue());
  }

  @Test
  void foo_should_return_true() {
    assertTrue(sut.getFooTrue());
  }

  @Test
  void bar_should_throw_exception() {
    assertThrows(NoClassDefFoundError.class, () -> sut.getBarTrue(), "com.example.bar.Bar");
  }
}
