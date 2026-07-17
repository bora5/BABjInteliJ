package rs.co.bora5.plugins.babj.action;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import rs.co.bora5.plugins.babj.model.BABjNaming;
import rs.co.bora5.plugins.babj.model.EntityModel;
import rs.co.bora5.plugins.babj.model.GenerationContext;
import rs.co.bora5.plugins.babj.model.OperaterTypeResolver;
import rs.co.bora5.plugins.babj.model.RolesTypeResolver;

/**
 * Configuration dialog for the BABj CRUD generator. Pre-fills every field from the selected entity
 * and the observed package layout, so the common case is just pressing OK.
 */
public class GenerateBABjCrudDialog extends DialogWrapper {

    private final EntityModel model;
    private final Project project;
    private final List<RolesTypeResolver.RolesType> discoveredRolesTypes;

    private final JBTextField basePackageField;
    private final ComboBox<String> kTypeField;
    private final ComboBox<String> rolesTypeField;
    private final DefaultListModel<String> rolesModel = new DefaultListModel<>();
    private final JBList<String> rolesList = new JBList<>(rolesModel);
    private final JBTextField additionalRolesField = new JBTextField();
    private final JBTextField viewNameField;
    private final JBTextField routeField;
    private final JBTextField titleField;

    private final JBCheckBox dtoCheck = new JBCheckBox("DTO (projection)", true);
    private final JBCheckBox homeCheck = new JBCheckBox("Home (EJB service)", true);
    private final JBCheckBox viewCheck = new JBCheckBox("View (grid)", true);
    private final JBCheckBox windowCheck = new JBCheckBox("EditWindow", true);

    public GenerateBABjCrudDialog(@Nullable Project project, EntityModel model) {
        super(project);
        this.model = model;
        this.project = project;
        this.discoveredRolesTypes = RolesTypeResolver.resolveAll(project);

        String entity = model.getSimpleName();
        basePackageField = new JBTextField(BABjNaming.basePackage(model.getPackageName()));
        List<String> operatorTypes = OperaterTypeResolver.resolveAll(project);
        kTypeField = new ComboBox<>(new DefaultComboBoxModel<>(operatorTypes.toArray(String[]::new)));
        kTypeField.setEditable(true);

        String[] rolesTypeNames = discoveredRolesTypes.stream()
                .map(RolesTypeResolver.RolesType::simpleName)
                .toArray(String[]::new);
        rolesTypeField = new ComboBox<>(new DefaultComboBoxModel<>(rolesTypeNames));
        rolesTypeField.setEditable(true);
        rolesTypeField.addActionListener(event -> refreshRoles());
        rolesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        rolesList.setVisibleRowCount(5);
        refreshRoles();
        viewNameField = new JBTextField(entity + "View");
        routeField = new JBTextField(BABjNaming.decapitalize(entity) + "View");
        titleField = new JBTextField(BABjNaming.label(entity));

        setTitle("BABj CRUD generator — " + entity);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Base package:", basePackageField)
                .addLabeledComponent("Operator (K) type:", kTypeField)
                .addLabeledComponent("Roles class:", rolesTypeField)
                .addLabeledComponent("Roles (multiple selection):", new JBScrollPane(rolesList))
                .addLabeledComponent("Additional role constants:", additionalRolesField)
                .addLabeledComponent("View class name:", viewNameField)
                .addLabeledComponent("Route:", routeField)
                .addLabeledComponent("Page title:", titleField)
                .addSeparator()
                .addComponent(dtoCheck)
                .addComponent(homeCheck)
                .addComponent(viewCheck)
                .addComponent(windowCheck)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (basePackageField.getText().isBlank()) {
            return new ValidationInfo("Base package is required.", basePackageField);
        }
        if (comboText(kTypeField).isBlank()) {
            return new ValidationInfo("Operator (K) type is required.", kTypeField);
        }
        if (viewCheck.isSelected() && comboText(rolesTypeField).isBlank()) {
            return new ValidationInfo("A concrete AbstractRoles subclass is required.", rolesTypeField);
        }
        if (viewCheck.isSelected() && selectedRoles().isEmpty()) {
            return new ValidationInfo("Select or enter at least one role for the generated View.", rolesList);
        }
        if (viewCheck.isSelected() && project != null) {
            PsiNameHelper names = PsiNameHelper.getInstance(project);
            if (!names.isIdentifier(comboText(rolesTypeField))) {
                return new ValidationInfo("Roles class must be a valid simple Java class name.", rolesTypeField);
            }
            for (String role : selectedRoles()) {
                if (!names.isIdentifier(role)) {
                    return new ValidationInfo("Role constants must be valid Java identifiers.", additionalRolesField);
                }
            }
        }
        if (viewCheck.isSelected() && viewNameField.getText().isBlank()) {
            return new ValidationInfo("View class name is required.", viewNameField);
        }
        if (!dtoCheck.isSelected() && !homeCheck.isSelected()
                && !viewCheck.isSelected() && !windowCheck.isSelected()) {
            return new ValidationInfo("Select at least one artifact to generate.", dtoCheck);
        }
        return null;
    }

    public GenerationContext buildContext() {
        return new GenerationContext(
                basePackageField.getText().trim(),
                model.getSimpleName(),
                comboText(kTypeField),
                comboText(rolesTypeField),
                selectedRolesTypeFqn(),
                selectedRoles(),
                viewNameField.getText().trim(),
                routeField.getText().trim(),
                titleField.getText().trim(),
                model.getFields(),
                dtoCheck.isSelected(),
                homeCheck.isSelected(),
                viewCheck.isSelected(),
                windowCheck.isSelected());
    }

    private void refreshRoles() {
        Set<String> previouslySelected = new LinkedHashSet<>(rolesList.getSelectedValuesList());
        rolesModel.clear();
        resolvedRolesType().ifPresent(type -> type.roleNames().forEach(rolesModel::addElement));
        for (int i = 0; i < rolesModel.size(); i++) {
            if (previouslySelected.contains(rolesModel.get(i))) {
                rolesList.addSelectionInterval(i, i);
            }
        }
    }

    private java.util.Optional<RolesTypeResolver.RolesType> resolvedRolesType() {
        String selected = comboText(rolesTypeField);
        return discoveredRolesTypes.stream()
                .filter(type -> type.simpleName().equals(selected))
                .findFirst();
    }

    private String selectedRolesTypeFqn() {
        String selected = comboText(rolesTypeField);
        if (selected.isBlank()) {
            return "";
        }
        return resolvedRolesType()
                .map(RolesTypeResolver.RolesType::qualifiedName)
                .orElseGet(() -> basePackageField.getText().trim() + ".utils."
                        + selected);
    }

    private List<String> selectedRoles() {
        Set<String> result = new LinkedHashSet<>(rolesList.getSelectedValuesList());
        for (String role : additionalRolesField.getText().split("[,;\\s]+")) {
            if (!role.isBlank()) {
                result.add(role.trim());
            }
        }
        return List.copyOf(result);
    }

    private static String comboText(ComboBox<String> comboBox) {
        Object item = comboBox.isEditable()
                ? comboBox.getEditor().getItem() : comboBox.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }
}
