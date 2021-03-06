package backend.resource;


import backend.resource.serialization.SerializableLabel;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import org.eclipse.egit.github.core.Label;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class TurboLabel implements Comparable<TurboLabel> {

    public static final String EXCLUSIVE_DELIMITER = ".";
    public static final String NONEXCLUSIVE_DELIMITER = "-";

    private void ______SERIALIZED_FIELDS______() {
    }

    private final String actualName;
    private final String colour;

    private void ______TRANSIENT_FIELDS______() {
    }

    private final String repoId;

    private void ______CONSTRUCTORS______() {
    }

    public TurboLabel(String repoId, String name) {
        this.actualName = name;
        this.colour = "ffffff";
        this.repoId = repoId;
    }

    public TurboLabel(String repoId, String colour, String name) {
        this.actualName = name;
        this.colour = colour;
        this.repoId = repoId;
    }

    public static TurboLabel nonexclusive(String repoId, String group, String name) {
        return new TurboLabel(repoId, joinWith(group, name, false));
    }

    public static TurboLabel exclusive(String repoId, String group, String name) {
        return new TurboLabel(repoId, joinWith(group, name, true));
    }

    public TurboLabel(String repoId, Label label) {
        this.actualName = label.getName();
        this.colour = label.getColor();
        this.repoId = repoId;
    }

    public TurboLabel(String repoId, SerializableLabel label) {
        this.actualName = label.getActualName();
        this.colour = label.getColour();
        this.repoId = repoId;
    }

    private void ______METHODS______() {
    }

    public static Optional<String> getDelimiter(String name) {

        // Escaping due to constants not being valid regexes
        Pattern p = Pattern.compile(String.format("^[^\\%s\\%s]+(\\%s|\\%s)",
            EXCLUSIVE_DELIMITER,
            NONEXCLUSIVE_DELIMITER,
            EXCLUSIVE_DELIMITER,
            NONEXCLUSIVE_DELIMITER));
        Matcher m = p.matcher(name);

        if (m.find()) {
            return Optional.of(m.group(1));
        } else {
            return Optional.empty();
        }
    }

    private static String joinWith(String group, String name, boolean exclusive) {
        return group + (exclusive ? EXCLUSIVE_DELIMITER : NONEXCLUSIVE_DELIMITER) + name;
    }

    public boolean isExclusive() {
        return getDelimiter(actualName).isPresent() && getDelimiter(actualName).get().equals(EXCLUSIVE_DELIMITER);
    }

    public Optional<String> getGroup() {
        if (getDelimiter(actualName).isPresent()) {
            String delimiter = getDelimiter(actualName).get();
            // Escaping due to constants not being valid regexes
            String[] segments = actualName.split("\\" + delimiter);
            assert segments.length >= 1;
            if (segments.length == 1) {
                if (actualName.endsWith(delimiter)) {
                    // group.
                    return Optional.of(segments[0]);
                } else {
                    // .name
                    return Optional.empty();
                }
            } else {
                // group.name
                assert segments.length == 2;
                return Optional.of(segments[0]);
            }
        } else {
            // name
            return Optional.empty();
        }
    }

    public String getName() {
        if (getDelimiter(actualName).isPresent()) {
            String delimiter = getDelimiter(actualName).get();
            // Escaping due to constants not being valid regexes
            String[] segments = actualName.split("\\" + delimiter);
            assert segments.length >= 1;
            if (segments.length == 1) {
                if (actualName.endsWith(delimiter)) {
                    // group.
                    return "";
                } else {
                    // .name
                    return segments[0];
                }
            } else {
                // group.name
                assert segments.length == 2;
                return segments[1];
            }
        } else {
            // name
            return actualName;
        }
    }

    public String getStyle() {
        String colour = getColour();
        int r = Integer.parseInt(colour.substring(0, 2), 16);
        int g = Integer.parseInt(colour.substring(2, 4), 16);
        int b = Integer.parseInt(colour.substring(4, 6), 16);
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        boolean bright = luminance > 128;
        return "-fx-background-color: #" + getColour() + "; -fx-text-fill: " + (bright ? "black;" : "white;");
    }

    public Node getNode() {
        javafx.scene.control.Label node = new javafx.scene.control.Label(getName());
        node.getStyleClass().add("labels");
        node.setStyle(getStyle());
        if (getGroup().isPresent()) {
            Tooltip groupTooltip = new Tooltip(getGroup().get());
            node.setTooltip(groupTooltip);
        }
        return node;
    }

    @Override
    public String toString() {
        return actualName;
    }

    private void ______BOILERPLATE______() {
    }

    public String getRepoId() {
        return repoId;
    }
    public String getColour() {
        return colour;
    }
    public String getActualName() {
        return actualName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TurboLabel that = (TurboLabel) o;
        return actualName.equals(that.actualName) && colour.equals(that.colour);
    }

    @Override
    public int hashCode() {
        int result = actualName.hashCode();
        result = 31 * result + colour.hashCode();
        return result;
    }

    @Override
    public int compareTo(TurboLabel o) {
        return actualName.compareTo(o.getActualName());
    }
}
