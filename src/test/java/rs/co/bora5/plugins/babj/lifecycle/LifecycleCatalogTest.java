package rs.co.bora5.plugins.babj.lifecycle;

import java.util.Arrays;

import junit.framework.TestCase;

import rs.co.bora5.plugins.babj.lifecycle.LifecycleTemplate.Owner;

public class LifecycleCatalogTest extends TestCase {

    public void testEverySupportedEventHasAConnectedTemplate() {
        for (LifecycleEvent event : LifecycleEvent.values()) {
            LifecycleTemplate template = LifecycleCatalog.template(event);
            assertNotNull(event.name(), template);
            assertFalse(event.name(), template.steps().isEmpty());
            assertFalse(event.name(), template.edges().isEmpty());
            assertTrue(event.name(), template.steps().stream()
                    .anyMatch(step -> step.kind() == LifecycleTemplate.StepKind.START));
            assertTrue(event.name(), template.steps().stream()
                    .anyMatch(step -> step.kind() == LifecycleTemplate.StepKind.END
                            || step.kind() == LifecycleTemplate.StepKind.STOP));

            var ids = template.steps().stream().map(LifecycleTemplate.Step::id).toList();
            assertEquals(event.name(), ids.size(), ids.stream().distinct().count());
            assertTrue(event.name(), template.edges().stream()
                    .allMatch(edge -> ids.contains(edge.from()) && ids.contains(edge.to())));
        }
    }

    public void testValidateKeepsViewAndHomeHooksSeparate() {
        LifecycleTemplate template = LifecycleCatalog.template(LifecycleEvent.VALIDATE);
        var beforeHooks = template.steps().stream()
                .filter(step -> step.method() != null)
                .filter(step -> step.method().name().equals("beforeValidate"))
                .toList();

        assertEquals(2, beforeHooks.size());
        assertEquals(Arrays.asList(Owner.VIEW, Owner.HOME),
                beforeHooks.stream().map(step -> step.method().owner()).toList());
    }

    public void testUnpayoutShowsActualViewAndHomeHookNames() {
        LifecycleTemplate template = LifecycleCatalog.template(LifecycleEvent.UNPAYOUT);
        assertTrue(template.steps().stream().anyMatch(step -> step.method() != null
                && step.method().owner() == Owner.VIEW
                && step.method().name().equals("beforePayout")));
        assertTrue(template.steps().stream().anyMatch(step -> step.method() != null
                && step.method().owner() == Owner.HOME
                && step.method().name().equals("beforeUnpayout")));
    }
}
