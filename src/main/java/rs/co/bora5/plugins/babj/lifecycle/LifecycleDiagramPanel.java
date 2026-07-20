package rs.co.bora5.plugins.babj.lifecycle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/** Lightweight Swing renderer for lifecycle nodes, decisions, branches, and navigation targets. */
final class LifecycleDiagramPanel extends JComponent {

    private static final int NODE_WIDTH = 250;
    private static final int NODE_HEIGHT = 72;
    private static final int COLUMN_GAP = 310;
    private static final int ROW_GAP = 112;
    private static final int MARGIN = 36;

    private static final Color ACTION = new JBColor(new Color(225, 235, 252),
            new Color(53, 66, 91));
    private static final Color OVERRIDDEN = new JBColor(new Color(216, 238, 255),
            new Color(36, 76, 111));
    private static final Color INHERITED = new JBColor(new Color(235, 235, 235),
            new Color(66, 66, 66));
    private static final Color DECISION = new JBColor(new Color(255, 239, 195),
            new Color(91, 72, 31));
    private static final Color SIDE_EFFECT = new JBColor(new Color(220, 244, 226),
            new Color(41, 83, 52));
    private static final Color STOP = new JBColor(new Color(255, 224, 224),
            new Color(95, 47, 47));
    private static final Color TERMINAL = new JBColor(new Color(226, 221, 252),
            new Color(65, 55, 102));
    private static final Color BORDER = new JBColor(new Color(100, 105, 115),
            new Color(165, 170, 180));

    private final Consumer<LifecycleDiagram.Node> navigationHandler;
    private final Map<String, Rectangle> bounds = new HashMap<>();
    private LifecycleDiagram diagram;

    LifecycleDiagramPanel(Consumer<LifecycleDiagram.Node> navigationHandler) {
        this.navigationHandler = navigationHandler;
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    LifecycleDiagram.Node node = nodeAt(event.getPoint());
                    if (node != null && node.target() != null) {
                        navigationHandler.accept(node);
                    }
                }
            }
        });
    }

    void setDiagram(@Nullable LifecycleDiagram diagram) {
        this.diagram = diagram;
        bounds.clear();
        setPreferredSize(preferredDiagramSize());
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(UIUtil.getPanelBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (diagram == null) {
                drawEmpty(g);
                return;
            }
            calculateBounds();
            drawEdges(g);
            for (LifecycleDiagram.Node node : diagram.nodes()) {
                drawNode(g, node, bounds.get(node.id()));
            }
        } finally {
            g.dispose();
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        LifecycleDiagram.Node node = nodeAt(event.getPoint());
        if (node == null) {
            return null;
        }
        String navigation = node.target() == null ? "" : "<br><i>Double-click to open code.</i>";
        return "<html><b>" + escape(node.label()) + "</b><br>"
                + escape(node.detail()) + navigation + "</html>";
    }

    private void calculateBounds() {
        bounds.clear();
        if (diagram == null || diagram.nodes().isEmpty()) {
            return;
        }
        int minColumn = diagram.nodes().stream().mapToInt(LifecycleDiagram.Node::column)
                .min().orElse(0);
        for (LifecycleDiagram.Node node : diagram.nodes()) {
            int x = MARGIN + (node.column() - minColumn) * COLUMN_GAP;
            int y = MARGIN + node.row() * ROW_GAP;
            bounds.put(node.id(), new Rectangle(x, y, NODE_WIDTH, NODE_HEIGHT));
        }
    }

    private void drawEdges(Graphics2D g) {
        if (diagram == null) {
            return;
        }
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(JBUI.scale(1), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        for (LifecycleTemplate.Edge edge : diagram.edges()) {
            Rectangle from = bounds.get(edge.from());
            Rectangle to = bounds.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            int startX = from.x + from.width / 2;
            int startY = from.y + from.height;
            int endX = to.x + to.width / 2;
            int endY = to.y;
            Path2D path = new Path2D.Float();
            path.moveTo(startX, startY);
            if (startX == endX) {
                path.lineTo(endX, endY);
            } else {
                int middleY = startY + Math.max(JBUI.scale(18), (endY - startY) / 2);
                path.lineTo(startX, middleY);
                path.lineTo(endX, middleY);
                path.lineTo(endX, endY);
            }
            g.draw(path);
            drawArrow(g, endX, endY);
            if (!edge.label().isBlank()) {
                int labelX = startX == endX ? startX + JBUI.scale(7)
                        : (startX + endX) / 2;
                int labelY = startY + Math.max(JBUI.scale(15), (endY - startY) / 2)
                        - JBUI.scale(3);
                g.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 1));
                g.drawString(edge.label(), labelX, labelY);
            }
        }
    }

    private void drawArrow(Graphics2D g, int x, int y) {
        int size = JBUI.scale(6);
        Polygon arrow = new Polygon(
                new int[]{x, x - size, x + size},
                new int[]{y, y - size - 2, y - size - 2}, 3);
        g.fillPolygon(arrow);
    }

    private void drawNode(Graphics2D g, LifecycleDiagram.Node node, Rectangle box) {
        g.setColor(fill(node));
        if (node.kind() == LifecycleTemplate.StepKind.DECISION) {
            Polygon diamond = new Polygon(
                    new int[]{box.x + box.width / 2, box.x + box.width,
                            box.x + box.width / 2, box.x},
                    new int[]{box.y, box.y + box.height / 2,
                            box.y + box.height, box.y + box.height / 2}, 4);
            g.fillPolygon(diamond);
            g.setColor(BORDER);
            g.drawPolygon(diamond);
        } else {
            g.fillRoundRect(box.x, box.y, box.width, box.height,
                    JBUI.scale(14), JBUI.scale(14));
            g.setColor(BORDER);
            g.drawRoundRect(box.x, box.y, box.width, box.height,
                    JBUI.scale(14), JBUI.scale(14));
        }

        g.setColor(UIUtil.getLabelForeground());
        Font base = getFont();
        g.setFont(base.deriveFont(Font.BOLD));
        drawCentered(g, node.label(), box, box.y + JBUI.scale(27), JBUI.scale(20));
        g.setFont(base.deriveFont(Math.max(10f, base.getSize2D() - 1f)));
        drawCentered(g, node.detail(), box, box.y + JBUI.scale(49), JBUI.scale(32));

        if (node.target() != null) {
            int marker = JBUI.scale(6);
            g.setColor(node.implementation() == LifecycleDiagram.Implementation.OVERRIDDEN
                    ? new JBColor(new Color(37, 117, 191), new Color(88, 166, 255)) : BORDER);
            g.fillOval(box.x + box.width - marker - JBUI.scale(8),
                    box.y + JBUI.scale(8), marker, marker);
        }
    }

    private void drawCentered(Graphics2D g, String text, Rectangle box, int baseline,
                              int horizontalPadding) {
        FontMetrics metrics = g.getFontMetrics();
        String fitted = fit(text, metrics, box.width - horizontalPadding * 2);
        int x = box.x + (box.width - metrics.stringWidth(fitted)) / 2;
        g.drawString(fitted, x, baseline);
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private Color fill(LifecycleDiagram.Node node) {
        return switch (node.kind()) {
            case START, END -> TERMINAL;
            case DECISION -> DECISION;
            case SIDE_EFFECT -> SIDE_EFFECT;
            case STOP -> STOP;
            case HOOK -> node.implementation() == LifecycleDiagram.Implementation.OVERRIDDEN
                    ? OVERRIDDEN : INHERITED;
            case ACTION -> ACTION;
        };
    }

    private void drawEmpty(Graphics2D g) {
        String text = "Place the caret in a BABj class and refresh.";
        g.setColor(UIUtil.getLabelDisabledForeground());
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, Math.max(MARGIN, (getWidth() - metrics.stringWidth(text)) / 2),
                Math.max(MARGIN, getHeight() / 2));
    }

    private @Nullable LifecycleDiagram.Node nodeAt(Point point) {
        if (diagram == null) {
            return null;
        }
        return diagram.nodes().stream()
                .filter(node -> {
                    Rectangle rectangle = bounds.get(node.id());
                    return rectangle != null && rectangle.contains(point);
                })
                .findFirst().orElse(null);
    }

    private Dimension preferredDiagramSize() {
        if (diagram == null || diagram.nodes().isEmpty()) {
            return new Dimension(JBUI.scale(600), JBUI.scale(420));
        }
        int minColumn = diagram.nodes().stream().mapToInt(LifecycleDiagram.Node::column)
                .min().orElse(0);
        int maxColumn = diagram.nodes().stream().mapToInt(LifecycleDiagram.Node::column)
                .max().orElse(0);
        int maxRow = diagram.nodes().stream().mapToInt(LifecycleDiagram.Node::row)
                .max().orElse(0);
        return new Dimension(MARGIN * 2 + NODE_WIDTH + (maxColumn - minColumn) * COLUMN_GAP,
                MARGIN * 2 + NODE_HEIGHT + maxRow * ROW_GAP);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
