package rs.co.bora5.plugins.babj.lifecycle;

import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

public class LifecycleDiagramImageExporterTest extends TestCase {

    public void testRendersFullDiagramAndWritesPng() throws Exception {
        LifecycleDiagram diagram = diagram();
        LifecycleDiagramPanel panel = new LifecycleDiagramPanel(ignored -> {
        });
        panel.setDiagram(diagram);

        BufferedImage image = panel.renderImage();

        assertNotNull(image);
        assertTrue(image.getWidth() >= 300);
        assertTrue(image.getHeight() >= 250);
        assertEquals(255, image.getRGB(0, 0) >>> 24);

        Path file = Files.createTempFile("babj-lifecycle-", ".png");
        try {
            LifecycleDiagramImageExporter.writePng(image, file.toFile());
            assertTrue(Files.size(file) > 0);
            BufferedImage restored = ImageIO.read(file.toFile());
            assertNotNull(restored);
            assertEquals(image.getWidth(), restored.getWidth());
            assertEquals(image.getHeight(), restored.getHeight());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    public void testCreatesPortableFileNameAndImageTransferable() throws Exception {
        LifecycleDiagram diagram = diagram();
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        var transferable = LifecycleDiagramImageExporter.transferable(image);

        assertEquals("order-view-create-persist.png",
                LifecycleDiagramImageExporter.suggestedFileName(diagram));
        assertNotNull(LifecycleDiagramImageExporter.fileSaverDescriptor());
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertSame(image, transferable.getTransferData(DataFlavor.imageFlavor));
    }

    private static LifecycleDiagram diagram() {
        LifecycleDiagram.Node start = new LifecycleDiagram.Node(
                "start", "Start", "User action", LifecycleTemplate.StepKind.START,
                0, 0, LifecycleDiagram.Implementation.NONE, null);
        LifecycleDiagram.Node action = new LifecycleDiagram.Node(
                "action", "Save", "Persist entity", LifecycleTemplate.StepKind.ACTION,
                1, 0, LifecycleDiagram.Implementation.FRAMEWORK, null);
        LifecycleDiagram.Node end = new LifecycleDiagram.Node(
                "end", "End", "Completed", LifecycleTemplate.StepKind.END,
                2, 0, LifecycleDiagram.Implementation.NONE, null);
        return new LifecycleDiagram(LifecycleEvent.CREATE, "OrderView",
                List.of(start, action, end),
                List.of(new LifecycleTemplate.Edge("start", "action", ""),
                        new LifecycleTemplate.Edge("action", "end", "")));
    }
}
