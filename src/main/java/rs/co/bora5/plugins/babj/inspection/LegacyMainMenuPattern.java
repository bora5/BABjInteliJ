package rs.co.bora5.plugins.babj.inspection;

import com.intellij.psi.PsiMethod;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the standard BAB legacy FavTab/visibility-switch menu pattern. */
final class LegacyMainMenuPattern {

    private static final Pattern FAV_TAB = Pattern.compile(
            "(?:Tab|var)\\s+(\\w+)\\s*=\\s*new\\s+FavTab\\s*\\(\\s*"
                    + "(\"(?:\\\\.|[^\"\\\\])*\")\\s*,\\s*([\\w.$]+\\.class)\\s*\\)\\s*;",
            Pattern.DOTALL);
    private static final Pattern LABEL = Pattern.compile(
            "H6\\s+(\\w+)\\s*=\\s*new\\s+H6\\s*\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")\\s*\\)\\s*;");
    private static final Pattern ICON = Pattern.compile(
            "(\\w+)\\.addComponentAsFirst\\s*\\(\\s*(LineAwesomeIcon\\.\\w+)\\.create\\(\\)\\s*\\)\\s*;");
    private static final Pattern SECTION_TAB = Pattern.compile(
            "Tab\\s+(\\w+)\\s*=\\s*new\\s+Tab\\s*\\(\\s*(\\w+)\\s*\\)\\s*;");
    private static final Pattern ADD = Pattern.compile("\\btabs\\.add\\s*\\(\\s*(\\w+)\\s*\\)\\s*;");
    private static final Pattern ROUTE = Pattern.compile(
            "routes\\.add\\s*\\(\\s*([\\w.$]+\\.class)\\s*\\)\\s*;");
    private static final Pattern VISIBILITY = Pattern.compile(
            "(\\w+)\\.setVisible\\s*\\(\\s*(true|false)\\s*\\)\\s*;");
    private static final Pattern CASE = Pattern.compile("^case\\s+([^:]+):$");

    private LegacyMainMenuPattern() {
    }

    static Match detect(PsiMethod method) {
        if (!"createMenuItems".equals(method.getName()) || method.getBody() == null) {
            return null;
        }
        String body = method.getBody().getText();
        if (!body.contains("new FavTab") || !body.contains("tabs.add")) {
            return null;
        }
        if (!isStandardBody(body)) {
            return null;
        }

        Map<String, Item> items = new LinkedHashMap<>();
        Matcher favMatcher = FAV_TAB.matcher(body);
        while (favMatcher.find()) {
            items.put(favMatcher.group(1), new Item(favMatcher.group(2), favMatcher.group(3)));
        }
        if (items.isEmpty()) {
            return null;
        }
        Set<String> itemTargets = new HashSet<>();
        items.values().forEach(item -> itemTargets.add(item.target()));
        Matcher routeMatcher = ROUTE.matcher(body);
        while (routeMatcher.find()) {
            String target = routeMatcher.group(1);
            if (!itemTargets.contains(target) && !target.endsWith("PortalView.class")) {
                return null;
            }
        }

        Map<String, Label> labels = new HashMap<>();
        Matcher labelMatcher = LABEL.matcher(body);
        while (labelMatcher.find()) {
            labels.put(labelMatcher.group(1), new Label(labelMatcher.group(2), null));
        }
        Matcher iconMatcher = ICON.matcher(body);
        while (iconMatcher.find()) {
            Label label = labels.get(iconMatcher.group(1));
            if (label != null) {
                labels.put(iconMatcher.group(1), new Label(label.caption(), iconMatcher.group(2)));
            }
        }

        Map<String, String> sectionLabels = new HashMap<>();
        Matcher sectionMatcher = SECTION_TAB.matcher(body);
        while (sectionMatcher.find()) {
            sectionLabels.put(sectionMatcher.group(1), sectionMatcher.group(2));
        }

        Set<String> initiallyHidden = new HashSet<>();
        Map<String, LinkedHashSet<String>> roles = new HashMap<>();
        String currentRole = null;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.strip();
            Matcher caseMatcher = CASE.matcher(line);
            if (caseMatcher.matches()) {
                currentRole = caseMatcher.group(1).strip();
                continue;
            }
            if (line.startsWith("default:")) {
                currentRole = null;
                continue;
            }
            Matcher visibilityMatcher = VISIBILITY.matcher(line);
            while (visibilityMatcher.find()) {
                String variable = visibilityMatcher.group(1);
                if ("false".equals(visibilityMatcher.group(2))) {
                    initiallyHidden.add(variable);
                } else if (currentRole != null && items.containsKey(variable)) {
                    roles.computeIfAbsent(variable, key -> new LinkedHashSet<>()).add(currentRole);
                }
            }
        }

        List<Section> sections = new ArrayList<>();
        Map<String, Section> sectionsByVariable = new LinkedHashMap<>();
        Map<String, String> itemSections = new HashMap<>();
        Section currentSection = null;
        Matcher addMatcher = ADD.matcher(body);
        while (addMatcher.find()) {
            String variable = addMatcher.group(1);
            String labelVariable = sectionLabels.get(variable);
            if (labelVariable != null) {
                Label label = labels.get(labelVariable);
                if (label == null || label.icon() == null) {
                    return null;
                }
                currentSection = sectionsByVariable.computeIfAbsent(variable,
                        key -> new Section(sectionId(key, label.caption()), label, new ArrayList<>()));
                if (!sections.contains(currentSection)) {
                    sections.add(currentSection);
                }
                continue;
            }
            Item item = items.get(variable);
            if (item == null || currentSection == null) {
                continue;
            }
            LinkedHashSet<String> itemRoles = roles.getOrDefault(variable, new LinkedHashSet<>());
            if (initiallyHidden.contains(variable) && itemRoles.isEmpty()) {
                continue;
            }
            String previousSection = itemSections.putIfAbsent(variable, currentSection.id());
            if (previousSection != null && !previousSection.equals(currentSection.id())) {
                return null;
            }
            if (currentSection.items().stream().noneMatch(entry -> entry.variable().equals(variable))) {
                currentSection.items().add(new MenuItem(variable, item, List.copyOf(itemRoles)));
            }
        }
        sections.removeIf(section -> section.items().isEmpty());
        if (sections.isEmpty() || hasDuplicateTargets(sections)) {
            return null;
        }
        return new Match(method, buildReplacement(sections));
    }

    private static boolean isStandardBody(String body) {
        String remaining = body;
        remaining = FAV_TAB.matcher(remaining).replaceAll("");
        remaining = LABEL.matcher(remaining).replaceAll("");
        remaining = ICON.matcher(remaining).replaceAll("");
        remaining = SECTION_TAB.matcher(remaining).replaceAll("");
        remaining = ADD.matcher(remaining).replaceAll("");
        remaining = VISIBILITY.matcher(remaining).replaceAll("");
        remaining = remaining.replaceAll("\\w+\\.setEnabled\\s*\\(\\s*false\\s*\\)\\s*;", "");
        remaining = ROUTE.matcher(remaining).replaceAll("");
        remaining = remaining.replaceAll(
                "getOperaterEJB\\(\\)\\.getRoles\\s*\\(\\s*getOperater\\(\\)\\s*\\)"
                        + "\\.(?:parallelStream|stream)\\(\\)\\.forEach\\s*\\(\\s*\\w+\\s*->\\s*\\{",
                "");
        remaining = remaining.replaceAll("switch\\s*\\(\\s*\\w+\\s*\\)\\s*\\{", "");
        remaining = remaining.replaceAll("case\\s+[^:]+:", "");
        remaining = remaining.replaceAll("default\\s*:", "");
        remaining = remaining.replaceAll("break\\s*;", "");
        remaining = remaining.replaceAll("}\\s*\\);", "");
        remaining = remaining.replace("{", "").replace("}", "");
        return remaining.isBlank();
    }

    private static boolean hasDuplicateTargets(List<Section> sections) {
        Set<String> targets = new HashSet<>();
        return sections.stream().flatMap(section -> section.items().stream())
                .anyMatch(item -> !targets.add(item.item().target()));
    }

    private static String buildReplacement(List<Section> sections) {
        String type = "rs.co.bora5.programs.bab.front.menu.MenuDefinition";
        StringBuilder result = new StringBuilder("""
                @Override
                protected rs.co.bora5.programs.bab.front.menu.MenuDefinition createMenuDefinition() {
                    return rs.co.bora5.programs.bab.front.menu.MenuDefinition.menu(
                """);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Section section = sections.get(sectionIndex);
            result.append("            ").append(type).append(".section(\"")
                    .append(section.id()).append("\", ").append(section.label().caption())
                    .append(", ").append(section.label().icon()).append(", ")
                    .append((sectionIndex + 1) * 10).append(",\n");
            for (int itemIndex = 0; itemIndex < section.items().size(); itemIndex++) {
                MenuItem entry = section.items().get(itemIndex);
                result.append("                    ").append(type).append(".item(")
                        .append(entry.item().caption()).append(", ")
                        .append(entry.item().target()).append(", ")
                        .append((itemIndex + 1) * 10);
                for (String role : entry.roles()) {
                    result.append(", ").append(role);
                }
                result.append(")");
                if (itemIndex + 1 < section.items().size()) {
                    result.append(',');
                }
                result.append('\n');
            }
            result.append("            )");
            if (sectionIndex + 1 < sections.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        return result.append("        );\n    }").toString();
    }

    private static String sectionId(String variable, String captionLiteral) {
        String candidate = variable.startsWith("tab") && variable.length() > 3
                ? Character.toLowerCase(variable.charAt(3)) + variable.substring(4)
                : captionLiteral.substring(1, captionLiteral.length() - 1);
        String normalized = Normalizer.normalize(candidate, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "section" : normalized;
    }

    record Match(PsiMethod method, String replacementMethod) {
    }

    private record Item(String caption, String target) {
    }

    private record Label(String caption, String icon) {
    }

    private record MenuItem(String variable, Item item, List<String> roles) {
    }

    private record Section(String id, Label label, List<MenuItem> items) {
    }
}
