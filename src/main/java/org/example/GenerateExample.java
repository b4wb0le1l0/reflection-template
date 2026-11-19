package org.example;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.example.classes.*;
import org.example.generator.Generator;

public class GenerateExample {
    enum Color { RED, GREEN, BLUE }

    public static void main(String[] args) {
        Generator generator = new Generator();
        System.out.println("Integer: " + generator.generateValueOfType(Integer.class));
        System.out.println("String: " + generator.generateValueOfType(String.class));
        System.out.println("Enum: " + generator.generateValueOfType(Color.class));
        System.out.println("Array: " + Arrays.toString((int[]) generator.generateValueOfType(int[].class)));
        System.out.println("List: " + generator.generateValueOfType(List.class));
        System.out.println("Map: " + generator.generateValueOfType(Map.class));
        System.out.println("Example: " + generator.generateValueOfType(Example.class));
        Shape shape = (Shape) generator.generateValueOfType(Shape.class);
        System.out.println("Shape: " + shape.getClass().getSimpleName()
                + ", area: " + shape.getArea() + ", perimeter: " + shape.getPerimeter());
        Triangle triangle = (Triangle) generator.generateValueOfType(Triangle.class);
        System.out.println("Triangle area: " + triangle.getArea());
        Cart cart = (Cart) generator.generateValueOfType(Cart.class);
        for (Product product : cart.getItems()) {
            System.out.println("Product: " + product.getName() + ", price: " + product.getPrice());
        }
        BinaryTreeNode tree = (BinaryTreeNode) generator.generateValueOfType(BinaryTreeNode.class);
        System.out.println("Tree root: " + tree.getData());
    }
}
