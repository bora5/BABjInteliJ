package rs.co.bora5.plugins.babj.action;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import rs.co.bora5.plugins.babj.gen.CodeTemplates;
import rs.co.bora5.plugins.babj.model.BABjField;
import rs.co.bora5.plugins.babj.model.BABjNaming;

/** Field selection, ordering, editor choice, and live preview for the CRUD generator. */
final class CrudFieldDesignerPanel extends JPanel {

    private final FieldTableModel tableModel;
    private final JBTable table;
    private final JBTextArea gridPreview = previewArea();
    private final JBTextArea formPreview = previewArea();

    CrudFieldDesignerPanel(List<BABjField> fields) {
        super(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        setPreferredSize(new Dimension(JBUI.scale(760), JBUI.scale(360)));

        tableModel = new FieldTableModel(fields, this::refreshPreview);
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(55));
        table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(BABjField.EditorKind.values())));

        JButton up = new JButton("Move up");
        up.addActionListener(event -> move(-1));
        JButton down = new JButton("Move down");
        down.addActionListener(event -> move(1));
        JButton all = new JButton("Select all");
        all.addActionListener(event -> tableModel.setAll(true));
        JButton none = new JButton("Clear");
        none.addActionListener(event -> tableModel.setAll(false));

        JPanel controls = new JPanel();
        controls.add(up);
        controls.add(down);
        controls.add(all);
        controls.add(none);

        JPanel left = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        left.add(new JLabel("Choose and order entity fields:"), BorderLayout.NORTH);
        left.add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);
        left.add(controls, BorderLayout.SOUTH);

        JPanel previews = new JPanel(new GridLayout(2, 1, 0, JBUI.scale(8)));
        previews.add(previewPanel("Grid projection", gridPreview));
        previews.add(previewPanel("Edit form", formPreview));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, previews);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        refreshPreview();
    }

    List<BABjField> selectedFields() {
        return tableModel.selectedFields();
    }

    private void move(int delta) {
        int row = table.getSelectedRow();
        int target = row + delta;
        if (row < 0 || target < 0 || target >= tableModel.getRowCount()) {
            return;
        }
        tableModel.move(row, target);
        table.setRowSelectionInterval(target, target);
    }

    private void refreshPreview() {
        List<BABjField> fields = selectedFields();
        StringBuilder columns = new StringBuilder("@ColumnNames(\"");
        StringBuilder form = new StringBuilder();
        for (BABjField field : fields) {
            if (columns.length() > 14) {
                columns.append(',');
            }
            String label = BABjNaming.label(field.name());
            if (field.isAssociation()) {
                columns.append('*').append(field.name()).append('.')
                        .append(field.displayProperty()).append('~').append(field.name())
                        .append('~').append(label);
            } else {
                columns.append(field.name()).append('~').append(field.name()).append('~').append(label);
            }
            form.append(field.name()).append("  →  ")
                    .append(CodeTemplates.editorName(field)).append('\n');
        }
        columns.append("\")");
        gridPreview.setText(columns.toString());
        formPreview.setText(form.isEmpty() ? "No form fields selected." : form.toString());
    }

    private static JPanel previewPanel(String title, JBTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(ScrollPaneFactory.createScrollPane(area, true), BorderLayout.CENTER);
        return panel;
    }

    private static JBTextArea previewArea() {
        JBTextArea area = new JBTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(JBUI.Borders.empty(6));
        return area;
    }

    private static final class Row {
        private boolean selected = true;
        private BABjField field;

        private Row(BABjField field) {
            this.field = field;
        }
    }

    private static final class FieldTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Use", "Field", "Kind", "DTO type", "Editor"};
        private final List<Row> rows;
        private final Runnable onChange;

        private FieldTableModel(List<BABjField> fields, Runnable onChange) {
            rows = new ArrayList<>();
            fields.forEach(field -> rows.add(new Row(field)));
            this.onChange = onChange;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class
                    : column == 4 ? BABjField.EditorKind.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || (column == 4 && !rows.get(row).field.isAssociation()
                    && rows.get(row).field.kind() != BABjField.Kind.ENUM);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.selected;
                case 1 -> row.field.name();
                case 2 -> row.field.kind().name();
                case 3 -> row.field.getDtoType();
                case 4 -> row.field.editorKind();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            if (columnIndex == 0) {
                row.selected = Boolean.TRUE.equals(value);
            } else if (columnIndex == 4 && value instanceof BABjField.EditorKind editor) {
                row.field = row.field.withEditor(editor);
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
            onChange.run();
        }

        private List<BABjField> selectedFields() {
            return rows.stream().filter(row -> row.selected).map(row -> row.field).toList();
        }

        private void move(int from, int to) {
            Row row = rows.remove(from);
            rows.add(to, row);
            fireTableDataChanged();
            onChange.run();
        }

        private void setAll(boolean selected) {
            rows.forEach(row -> row.selected = selected);
            fireTableDataChanged();
            onChange.run();
        }
    }
}
