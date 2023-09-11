import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

class Main {

    public static void main(String[] args) throws Exception {
        // Create previous and current objects
        Entry previous = new Entry(
                "James",
                new Subscription("ACTIVE", "no", new Abc("a", "b", "c")),
                List.of("Interior/ExteriorWash", "Same"),
                List.of(new Vehicle("v_1", "My Car"), new Vehicle("v_2", "Miss Car"), new Vehicle("v_3", "A Car"))
        );
        Entry current = new Entry(
                "Jim",
                new Subscription("EXPIRED", "yes", new Abc("b", "b", "b")),
                List.of("Same", "Oil Change", "Tire replacement"),
                List.of(new Vehicle("v_1", "23 Ferrari 296 GTS"), new Vehicle("v_3", "Another Car"))
        );

        // Get the list of changes
        List<ChangeType> changes = new DiffTool().diff(previous, current);

        // Print the changes
        changes.forEach(System.out::println);
    }

}

@Retention(RetentionPolicy.RUNTIME)
@interface AuditKey {
}

record Abc(String first, String second, String third) {
}

record Subscription(String status, String question, Abc abc) {
}

record Vehicle(@AuditKey String id, String displayName) {
}

record Entry(String firstName, Subscription subscription, List<String> services, List<Vehicle> vehicles) {
}

interface ChangeType {
}

record PropertyUpdate(String property, String previous, String current) implements ChangeType {
    @Override
    public String toString() {
        return """
                {"property": "%s", "previous": "%s", "current": "%s"}"""
                .formatted(property, previous, current);
    }
}

record ListUpdate(String property, List<String> removed, List<String> added) implements ChangeType {
    @Override
    public String toString() {
        return """
                {"property": "%s", "added": [%s], "removed": [%s]}"""
                .formatted(property,
                        added.stream().map("\"%s\""::formatted).collect(joining(", ")),
                        removed.stream().map("\"%s\""::formatted).collect(joining(", ")));
    }
}

class DiffTool {

    private final StringBuilder propertyName = new StringBuilder();

    public List<ChangeType> diff(Object previousObj, Object currentObj) throws Exception {
        List<ChangeType> changes = new ArrayList<>();
        if (previousObj != null || currentObj != null) {
            if (previousObj == null || !previousObj.getClass().equals(currentObj.getClass())) {
                throw new IllegalArgumentException("Objects are not of the same type or one is null");
            }

            for (Field field : previousObj.getClass().getDeclaredFields()) {
                field.setAccessible(true);

                Object previous = field.get(previousObj);
                Object current = field.get(currentObj);

                if (!previous.equals(current)) {
                    if (!propertyName.isEmpty()) propertyName.append(".");
                    propertyName.append(field.getName());

                    if (String.class.isAssignableFrom(previous.getClass())) { // string property (example 1)
                        String property = propertyName.toString();

                        // code block needed only for list of objects (example 4)
                        Optional<Field> idFieldOptional = Arrays.stream(previousObj.getClass().getDeclaredFields())
                                .filter(f -> f.isAnnotationPresent(AuditKey.class) || "id".equals(f.getName()))
                                .findFirst();
                        if (idFieldOptional.isPresent()) {
                            Field idField = idFieldOptional.get();
                            idField.setAccessible(true);
                            property = property.replace(".", "[" + idField.get(previousObj) + "].");
                        }

                        changes.add(new PropertyUpdate(property, String.valueOf(previous), String.valueOf(current)));
                        propertyName.setLength(Math.max(0, property.lastIndexOf(".")));
                    } else if (List.class.isAssignableFrom(previous.getClass())) {
                        ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                        Class<?> actualTypeArguments = (Class<?>) genericType.getActualTypeArguments()[0];

                        if (String.class.isAssignableFrom(actualTypeArguments)) { // list of strings (example 3)
                            @SuppressWarnings("unchecked") // is actually checked in code
                            List<String> previousList = (List<String>) previous;
                            @SuppressWarnings("unchecked") // is actually checked in code
                            List<String> currentList = (List<String>) current;

                            List<String> added = new ArrayList<>(currentList);
                            added.removeAll(previousList);

                            List<String> removed = new ArrayList<>(previousList);
                            removed.removeAll(currentList);

                            changes.add(new ListUpdate(propertyName.toString(), removed, added));
                            propertyName.setLength(Math.max(0, propertyName.lastIndexOf(".")));
                        } else { // list of objects (example 4)
                            List<?> previousList = (List<?>) previous;
                            List<?> currentList = (List<?>) current;

                            for (Object previousItem : previousList) {
                                Field idField = Arrays.stream(previousItem.getClass().getDeclaredFields())
                                        .filter(f -> f.isAnnotationPresent(AuditKey.class) || "id".equals(f.getName()))
                                        .findFirst()
                                        .orElseThrow(() -> new IllegalStateException("Missing AuditKey or id property"));
                                idField.setAccessible(true);
                                Object id = idField.get(previousItem);

                                for (Object currentItem : currentList) {
                                    if (id.equals(idField.get(currentItem))) {
                                        changes.addAll(diff(previousItem, currentItem));
                                        propertyName.setLength(Math.max(0, propertyName.lastIndexOf(".")));
                                    }
                                }
                            }
                        }
                    } else { // nested strings (example 2)
                        changes.addAll(diff(previous, current));
                        propertyName.setLength(Math.max(0, propertyName.lastIndexOf(".")));
                    }
                }
            }
        }
        return changes;
    }

}
