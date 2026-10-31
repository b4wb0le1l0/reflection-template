package org.example.classes;

import org.example.generator.Generatable;

@Generatable(implementations = {Rectangle.class, Triangle.class})
public interface Shape {
    double getArea();
    double getPerimeter();
}
