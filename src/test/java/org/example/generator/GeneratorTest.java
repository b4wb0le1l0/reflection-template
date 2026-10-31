package org.example.generator;

import org.example.classes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class GeneratorTest {
    private final Generator generator = new Generator(42);

    enum Color { RED, GREEN, BLUE }
    enum Empty {}

    @Generatable
    public static class Person {
        final String name;
        final int age;
        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Generatable
    public static class Directory {
        final Map<String, Integer> entries;
        public Directory(Map<String, Integer> entries) { this.entries = entries; }
    }

    @Generatable(implementations = Dog.class)
    public abstract static class Animal {}
    @Generatable
    public static class Dog extends Animal { public Dog() {} }

    public static class Unmarked { public Unmarked() {} }
    @Generatable
    public static class PrivateOnly { private PrivateOnly() {} }
    @Generatable
    public interface NoImplementation {}
    @Generatable(implementations = Person.class)
    public interface WrongImplementation {}
    @Generatable(implementations = UnmarkedChild.class)
    public interface MissingMarker {}
    public static class UnmarkedChild implements MissingMarker { public UnmarkedChild() {} }

    @Generatable
    public static class Loop { public Loop(Loop child) {} }
    @Generatable
    public static class Impossible {
        public Impossible(int value) { throw new IllegalArgumentException("Always rejected"); }
    }
    @Generatable
    public static class Crash {
        public Crash() { throw new IllegalStateException("Constructor bug"); }
    }

    @Test
    void primitivesAndWrappers() {
        Class<?>[] primitives = {byte.class, short.class, int.class, long.class,
                float.class, double.class, boolean.class, char.class};
        Class<?>[] wrappers = {Byte.class, Short.class, Integer.class, Long.class,
                Float.class, Double.class, Boolean.class, Character.class};
        for (int i = 0; i < primitives.length; i++) {
            assertInstanceOf(wrappers[i], generator.generateValueOfType(primitives[i]));
            assertInstanceOf(wrappers[i], generator.generateValueOfType(wrappers[i]));
        }
    }

    @Test
    void valuesAreNotConstant() {
        Set<Object> numbers = new HashSet<>();
        Set<Object> strings = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            int number = (int) generator.generateValueOfType(int.class);
            String text = (String) generator.generateValueOfType(String.class);
            assertTrue(number >= 1 && number <= 100);
            assertTrue(text.matches("[a-z]{3,10}"));
            numbers.add(number);
            strings.add(text);
        }
        assertTrue(numbers.size() > 1);
        assertTrue(strings.size() > 1);
    }

    @Test
    void enumsAndArrays() {
        assertInstanceOf(Color.class, generator.generateValueOfType(Color.class));
        for (Class<?> type : new Class<?>[]{int[].class, String[].class, Color[].class, Rectangle[].class}) {
            Object array = generator.generateValueOfType(type);
            assertEquals(type, array.getClass());
            int length = java.lang.reflect.Array.getLength(array);
            assertTrue(length >= 1 && length <= 3);
            for (int i = 0; i < length; i++) {
                assertNotNull(java.lang.reflect.Array.get(array, i));
            }
        }
    }

    @Test
    void collectionsContainGeneratedValues() {
        List<?> list = (List<?>) generator.generateValueOfType(List.class);
        assertFalse(list.isEmpty());
        for (Object item : list) assertInstanceOf(String.class, item);
        Map<?, ?> map = (Map<?, ?>) generator.generateValueOfType(Map.class);
        assertFalse(map.isEmpty());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            assertInstanceOf(String.class, entry.getKey());
            assertInstanceOf(Integer.class, entry.getValue());
        }
        Cart cart = (Cart) generator.generateValueOfType(Cart.class);
        assertFalse(cart.getItems().isEmpty());
        for (Product product : cart.getItems()) {
            assertFalse(product.getName().isBlank());
            assertTrue(product.getPrice() > 0);
        }
        Directory directory = (Directory) generator.generateValueOfType(Directory.class);
        assertFalse(directory.entries.isEmpty());
        for (Map.Entry<String, Integer> entry : directory.entries.entrySet()) {
            assertNotNull(entry.getKey());
            assertTrue(entry.getValue() > 0);
        }
    }

    @Test
    void newClassesAndAbstractTypes() {
        Person person = (Person) generator.generateValueOfType(Person.class);
        assertFalse(person.name.isBlank());
        assertTrue(person.age > 0);
        assertInstanceOf(Example.class, generator.generateValueOfType(Example.class));
        assertInstanceOf(Dog.class, generator.generateValueOfType(Animal.class));
        for (int i = 0; i < 30; i++) {
            Shape shape = (Shape) generator.generateValueOfType(Shape.class);
            assertTrue(shape instanceof Rectangle || shape instanceof Triangle);
            assertTrue(shape.getArea() > 0);
        }
    }

    @Test
    void domainConstraints() {
        for (int i = 0; i < 100; i++) {
            Rectangle rectangle = (Rectangle) generator.generateValueOfType(Rectangle.class);
            Triangle triangle = (Triangle) generator.generateValueOfType(Triangle.class);
            assertTrue(rectangle.getArea() > 0);
            assertTrue(rectangle.getPerimeter() > 0);
            assertTrue(Double.isFinite(triangle.getArea()) && triangle.getArea() > 0);
            assertTrue(triangle.getPerimeter() > 0);
        }
        assertThrows(IllegalArgumentException.class, () -> new Rectangle(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Triangle(1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new Triangle(-1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Product("", 2));
    }

    @Test
    void recursiveTreeEndsAtLeaves() {
        BinaryTreeNode root = (BinaryTreeNode) generator.generateValueOfType(BinaryTreeNode.class);
        assertEquals(4, checkTree(root));
    }

    private int checkTree(BinaryTreeNode node) {
        if (node == null) return 0;
        assertNotNull(node.getData());
        int left = checkTree(node.getLeft());
        int right = checkTree(node.getRight());
        assertEquals(left, right);
        return 1 + left;
    }

    @Test
    void unsupportedRequestsExplainFailure() {
        for (Class<?> type : new Class<?>[]{Unmarked.class, PrivateOnly.class,
                NoImplementation.class, WrongImplementation.class, MissingMarker.class,
                Loop.class, Empty.class, void.class, Set.class}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> generator.generateValueOfType(type));
            assertFalse(error.getMessage().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> generator.generateValueOfType(null));
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> generator.generateValueOfType(Impossible.class));
        assertTrue(rejected.getMessage().contains("100 attempts"));
        assertInstanceOf(IllegalArgumentException.class, rejected.getCause());
        IllegalArgumentException crash = assertThrows(IllegalArgumentException.class,
                () -> generator.generateValueOfType(Crash.class));
        assertInstanceOf(IllegalStateException.class, crash.getCause());
    }
}
