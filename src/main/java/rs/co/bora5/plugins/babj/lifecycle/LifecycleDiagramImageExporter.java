package rs.co.bora5.plugins.babj.lifecycle;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;

/** Clipboard and PNG output helpers for a rendered BABj lifecycle diagram. */
final class LifecycleDiagramImageExporter {

    private static final String EXPORT_TITLE = "Export BABj Lifecycle Diagram";
    private static final String EXPORT_DESCRIPTION =
            "Save the current lifecycle diagram as a PNG image.";

    private LifecycleDiagramImageExporter() {
    }

    static void copy(@NotNull BufferedImage image) {
        CopyPasteManager.getInstance().setContents(transferable(image));
    }

    static void writePng(@NotNull BufferedImage image, @NotNull File file) throws IOException {
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG image writer is available.");
        }
    }

    static @NotNull String suggestedFileName(@NotNull LifecycleDiagram diagram) {
        String name = diagram.contextName() + "-" + diagram.event().getDisplayName();
        String safe = name.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return (safe.isBlank() ? "babj-lifecycle" : safe) + ".png";
    }

    static @NotNull Transferable transferable(@NotNull Image image) {
        return new ImageTransferable(image);
    }

    /**
     * Uses the non-deprecated single-extension constructor on 2025.1+, with a reflective fallback
     * for the varargs constructor that is the only option in the minimum supported 2024.3 API.
     */
    static @NotNull FileSaverDescriptor fileSaverDescriptor() {
        try {
            return FileSaverDescriptor.class
                    .getConstructor(String.class, String.class, String.class)
                    .newInstance(EXPORT_TITLE, EXPORT_DESCRIPTION, "png");
        } catch (NoSuchMethodException unavailableIn2024) {
            try {
                return FileSaverDescriptor.class
                        .getConstructor(String.class, String.class, String[].class)
                        .newInstance(EXPORT_TITLE, EXPORT_DESCRIPTION,
                                (Object) new String[]{"png"});
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not create the PNG file chooser.", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the PNG file chooser.", exception);
        }
    }

    private record ImageTransferable(@NotNull Image image) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
