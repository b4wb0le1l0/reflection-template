package org.example.classes;

import org.example.generator.Generatable;

@Generatable
public class Rectangle implements Shape {
    private double length;
    private double width;

    public Rectangle(double length, double width) {
        if (!Double.isFinite(length) || !Double.isFinite(width) || length <= 0 || width <= 0) {
            throw new IllegalArgumentException("Rectangle dimensions must be positive and finite");
        }
        this.length = length;
        this.width = width;
    }

    @Override
    public double getArea() {
        return length * width;
    }

    @Override
    public double getPerimeter() {
        return 2 * (length + width);
    }
}
